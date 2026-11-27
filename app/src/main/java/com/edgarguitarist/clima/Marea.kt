package com.edgarguitarist.clima

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// Marea del río Guayas. Cuando sube, el agua de lluvia no tiene por dónde salir y se inundan zonas bajas de
// Guayaquil y Durán. Fuente: la predicción oficial de INOCAR para la estación Guayaquil-Río (pleamares y
// bajamares de hoy y los dos días siguientes). Si INOCAR no responde, el modelo de Open-Meteo en el estuario.

private val ZONA: ZoneId = ZoneId.of("America/Guayaquil")
private const val GYE_LAT = -2.19
private const val GYE_LON = -79.89

/** Pleamar (alta = true) o bajamar, con su altura en metros sobre el nivel de referencia de INOCAR. */
data class Extremo(val hora: ZonedDateTime, val altura: Double, val alta: Boolean)

/** `puntos`: la curva para graficar; `fuente`: de dónde sale, para decirlo en pantalla. */
data class Marea(val extremos: List<Extremo>, val puntos: List<Pair<ZonedDateTime, Double>>, val fuente: String)

/** La marea de Guayaquil solo sirve cerca del río: a más de ~60 km no se muestra. */
fun mareaAplica(lat: Double, lon: Double) = abs(lat - GYE_LAT) < .55 && abs(lon - GYE_LON) < .55

/** Lee la tabla de INOCAR: «26/11/2026 … 03:01 0.74 B 08:07 4.32 P …». */
fun parsearInocar(html: String): List<Extremo> {
    val texto = html.replace(Regex("<[^>]*>"), " ")
    val out = mutableListOf<Extremo>()
    var dia: LocalDate? = null
    val f = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    for (m in Regex("""(\d{2}/\d{2}/\d{4})|(\d{1,2}:\d{2})\s+(-?\d+(?:\.\d+)?)\s*([PB])\b""").findAll(texto)) {
        if (m.groupValues[1].isNotEmpty()) { dia = LocalDate.parse(m.groupValues[1], f); continue }
        val d = dia ?: continue
        val (h, mi) = m.groupValues[2].split(":").map { it.toInt() }
        out += Extremo(d.atTime(h, mi).atZone(ZONA), m.groupValues[3].toDouble(), m.groupValues[4] == "P")
    }
    return out.sortedBy { it.hora }
}

/**
 * Curva entre extremos con interpolación coseno (como las tablas de marea): entre una bajamar y una pleamar el
 * nivel sigue media onda de coseno. Un punto cada `paso` minutos.
 */
fun curvaMarea(ext: List<Extremo>, paso: Long = 15): List<Pair<ZonedDateTime, Double>> {
    val out = mutableListOf<Pair<ZonedDateTime, Double>>()
    for (k in 0 until ext.size - 1) {
        val a = ext[k]; val b = ext[k + 1]
        val total = Duration.between(a.hora, b.hora).toMinutes().toDouble()
        var t = 0L
        while (t < total) {
            val f = (1 - cos(PI * t / total)) / 2
            out += a.hora.plusMinutes(t) to a.altura + (b.altura - a.altura) * f
            t += paso
        }
    }
    ext.lastOrNull()?.let { out += it.hora to it.altura }
    return out
}

/** Nivel interpolado en un instante, o null si está fuera de la tabla. */
fun nivelEn(m: Marea, t: ZonedDateTime): Double? {
    val i = m.puntos.indexOfFirst { !it.first.isBefore(t) }
    if (i <= 0) return null
    val (t0, v0) = m.puntos[i - 1]; val (t1, v1) = m.puntos[i]
    val f = Duration.between(t0, t).toMinutes().toDouble() / Duration.between(t0, t1).toMinutes().coerceAtLeast(1)
    return v0 + (v1 - v0) * f
}

/**
 * Aguaje: las mareas más altas del mes, que llegan uno a cuatro días después de la luna nueva o llena (la marea
 * reacciona con retraso). Se calcula con la edad de la luna desde una luna nueva conocida (6-ene-2000 18:14 UTC).
 */
fun esAguaje(t: ZonedDateTime): Boolean {
    val dias = Duration.between(ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneId.of("UTC")), t).toMinutes() / 1440.0
    val edad = ((dias % 29.530588853) + 29.530588853) % 29.530588853
    val desdeSicigia = edad % 14.7652944 // días desde la última luna nueva o llena
    return desdeSicigia <= 4.0 || desdeSicigia >= 13.77
}

/** Bloqueante: llamar desde Dispatchers.IO. Null si ninguna fuente respondió. */
fun descargarMarea(): Marea? {
    runCatching {
        val ext = parsearInocar(leer("https://www.inocar.mil.ec/mareas/consultan.php", latin1 = true))
        if (ext.size >= 4) return Marea(ext, curvaMarea(ext), "INOCAR · estación Guayaquil-Río")
    }
    return runCatching {
        // El modelo no llega río adentro: el punto con datos más cercano está en el estuario, ~20 km al sur.
        val j = JSONObject(leer("https://marine-api.open-meteo.com/v1/marine?latitude=-2.375&longitude=-79.875" +
            "&hourly=sea_level_height_msl&forecast_days=3&timezone=America%2FGuayaquil", latin1 = false))
        val h = j.getJSONObject("hourly")
        val ts = h.getJSONArray("time"); val vs = h.getJSONArray("sea_level_height_msl")
        val puntos = (0 until ts.length()).filter { !vs.isNull(it) }
            .map { LocalDateTime.parse(ts.getString(it)).atZone(ZONA) to vs.getDouble(it) }
        val ext = puntos.indices.filter { it in 1 until puntos.size - 1 }.mapNotNull { i ->
            val (a, b, c) = Triple(puntos[i - 1].second, puntos[i].second, puntos[i + 1].second)
            when { b >= a && b > c -> Extremo(puntos[i].first, b, true); b <= a && b < c -> Extremo(puntos[i].first, b, false); else -> null }
        }
        Marea(ext, puntos, "Modelo Open-Meteo en el estuario (alturas sobre el nivel medio, no comparables con INOCAR)")
    }.getOrNull()
}

private fun leer(url: String, latin1: Boolean): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Clima") // INOCAR rechaza pedidos sin navegador
    try {
        if (c.responseCode != 200) error("respondió ${c.responseCode}")
        return c.inputStream.reader(if (latin1) Charsets.ISO_8859_1 else Charsets.UTF_8).use { it.readText() }
    } finally {
        c.disconnect()
    }
}

/** Una línea útil: si una pleamar cae cerca de lluvia prevista, el agua no drena. */
fun avisoMareaLluvia(m: Marea, horas: List<Hora>): String? {
    val ahora = ZonedDateTime.now(ZONA)
    for (p in m.extremos.filter { it.alta && it.hora.isAfter(ahora.minusHours(1)) && it.hora.isBefore(ahora.plusHours(24)) }) {
        val lluvia = horas.filter {
            val t = LocalDateTime.parse(it.hora).atZone(ZONA)
            abs(Duration.between(t, p.hora).toMinutes()) <= 120 && it.lluvia >= 1.0 && it.prob >= 50
        }
        if (lluvia.isNotEmpty()) {
            val mm = lluvia.maxOf { it.lluvia }
            return "⚠️ Pleamar de %.2f m a las %s con lluvia prevista (hasta %.1f mm/h): riesgo de que se represe el agua en zonas bajas"
                .format(p.altura, p.hora.toLocalTime().toString().take(5), mm)
        }
    }
    return null
}
