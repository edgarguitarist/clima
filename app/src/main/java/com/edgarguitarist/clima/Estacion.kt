package com.edgarguitarist.clima

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

// Los modelos (todos: ECMWF, GFS, ICON, JMA) dan llovizna en Guayaquil mucho más de lo que llueve. La estación
// meteorológica del aeropuerto (METAR, cada hora, gratis vía aviationweather.gov) sí ve si llueve o no: con ella
// se corrige el «ahora» del modelo. El satélite sigue aparte para las tormentas.

data class Estacion(val nombre: String, val obs: Long, val distKm: Double, val llueve: Boolean, val tiempo: String, val nubes: String)

private val CERCA_KM = 20.0
private val VIGENCIA_SEG = 80 * 60

fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
    hypot((lat2 - lat1) * 111.2, (lon2 - lon1) * 111.2 * cos(lat1 * PI / 180))

/** El METAR más cercano a menos de 20 km y de hace menos de 80 min, con la distancia agregada. Pura. */
fun elegirMetar(arr: JSONArray, lat: Double, lon: Double, ahoraSeg: Long): JSONObject? =
    (0 until arr.length()).map { arr.getJSONObject(it) }
        .filter { ahoraSeg - it.optLong("obsTime") <= VIGENCIA_SEG }
        .map { it to distanciaKm(lat, lon, it.optDouble("lat"), it.optDouble("lon")) }
        .filter { it.second <= CERCA_KM }
        .minByOrNull { it.second }
        ?.let { (o, d) -> o.put("distKm", d) }

/** Precipitación en la estación misma; «VC» (en las cercanías) y «RE» (reciente) no cuentan. */
private val PRECIP = Regex("^[-+]?(TS|SH|FZ)?(DZ|RA|GR|GS|UP|TS)")

fun leerEstacion(o: JSONObject): Estacion {
    val wx = o.optString("wxString").takeUnless { o.isNull("wxString") } ?: ""
    val llueve = wx.split(' ').any { PRECIP.containsMatchIn(it) }
    val capas = o.optJSONArray("clouds")
    val nubes = (0 until (capas?.length() ?: 0)).map { capas!!.getJSONObject(it).optString("cover") }
        .maxByOrNull { listOf("FEW", "SCT", "BKN", "OVC").indexOf(it) } ?: "CLR"
    val nombre = o.optString("name").substringBefore('/').substringBefore(',').ifEmpty { o.optString("icaoId") }
    return Estacion(nombre, o.optLong("obsTime"), o.optDouble("distKm"), llueve, wx, nubes)
}

/** Código WMO equivalente a lo que reporta la estación. */
fun codigoEstacion(e: Estacion): Int = when {
    "TS" in e.tiempo -> 95
    "+RA" in e.tiempo || "+SHRA" in e.tiempo -> 65
    "SH" in e.tiempo -> 80
    "RA" in e.tiempo -> if (e.tiempo.contains("-RA")) 61 else 63
    "DZ" in e.tiempo -> if (e.tiempo.contains("-DZ")) 51 else 53
    e.nubes == "OVC" -> 3
    e.nubes == "BKN" || e.nubes == "SCT" -> 2
    e.nubes == "FEW" -> 1
    else -> 0
}

private fun esLluvia(codigo: Int) = codigo in 51..67 || codigo in 80..82

/**
 * Cruza el «ahora» del modelo con la estación. Si el modelo da lluvia ligera y la estación no ve lluvia, manda la
 * estación (código de sus nubes y 0 mm). Si llueve en la estación y el modelo no lo ve, el código pasa a lluvia.
 * Lluvia moderada o fuerte del modelo no se toca: puede ser un chubasco local que la estación, a unos km, no ve.
 */
fun corregir(a: Actual, cuartos: List<Cuarto>, e: Estacion?): Triple<Actual, List<Cuarto>, Int?> {
    if (e == null) return Triple(a, cuartos, null)
    val modeloLlueve = a.lluvia >= 0.05 || esLluvia(a.codigo)
    return when {
        !e.llueve && modeloLlueve && a.lluvia * 4 < 2.5 ->
            Triple(a.copy(codigo = codigoEstacion(e), lluvia = 0.0), cuartos.mapIndexed { i, q -> if (i == 0) q.copy(lluvia = 0.0) else q }, a.codigo)
        e.llueve && !modeloLlueve -> Triple(a.copy(codigo = codigoEstacion(e)), cuartos, a.codigo)
        else -> Triple(a, cuartos, null)
    }
}

fun textoEstacion(e: Estacion, modelo: Int?): String {
    val que = if (e.llueve) texto(codigoEstacion(e)).lowercase() else "sin lluvia"
    val nota = when {
        modelo != null && !e.llueve -> " (el modelo daba ${texto(modelo).lowercase()})"
        modelo != null -> " (el modelo no la veía)"
        else -> ""
    }
    return "Aeropuerto de ${e.nombre} (a %.0f km) · $que$nota".format(e.distKm)
}

/** Bloqueante. Null si no hay estación cerca, el reporte es viejo o no responde: entonces queda solo el modelo. */
fun observarEstacion(lat: Double, lon: Double): JSONObject? = runCatching {
    val c = URL("https://aviationweather.gov/api/data/metar?format=json&bbox=%.3f,%.3f,%.3f,%.3f"
        .format(java.util.Locale.US, lat - .25, lon - .25, lat + .25, lon + .25)).openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 10_000
    try {
        if (c.responseCode != 200) return@runCatching null
        elegirMetar(JSONArray(c.inputStream.bufferedReader().use { it.readText() }), lat, lon, System.currentTimeMillis() / 1000)
    } finally {
        c.disconnect()
    }
}.getOrNull()
