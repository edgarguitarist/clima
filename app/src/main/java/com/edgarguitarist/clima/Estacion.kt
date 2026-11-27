package com.edgarguitarist.clima

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

// Los modelos (todos: ECMWF, GFS, ICON, JMA) dan llovizna en Guayaquil mucho más de lo que llueve. La estación
// meteorológica del aeropuerto (METAR, cada hora, gratis vía aviationweather.gov) sí ve si llueve o no: con ella
// se corrige el «ahora» del modelo. El satélite sigue aparte para las tormentas.

data class Estacion(
    val nombre: String, val obs: Long, val distKm: Double, val llueve: Boolean, val tiempo: String, val nubes: String,
    val temp: Double? = null, val rocio: Double? = null,
)

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
    fun num(k: String) = o.optDouble(k).takeUnless { it.isNaN() }
    return Estacion(nombre, o.optLong("obsTime"), o.optDouble("distKm"), llueve, wx, nubes, num("temp"), num("dewp"))
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

fun esLluvia(codigo: Int) = codigo in 51..67 || codigo in 80..82

/** Humedad relativa con la fórmula de Magnus. */
fun humedadRelativa(t: Double, rocio: Double) =
    (100 * exp(17.625 * rocio / (243.04 + rocio)) / exp(17.625 * t / (243.04 + t))).roundToInt().coerceIn(0, 100)

/**
 * Cruza el «ahora» del modelo con lo observado. Devuelve el actual corregido, los cuartos (el de ahora en 0 si se
 * desmintió la lluvia) y el código que daba el modelo si se cambió.
 * - Temperatura y humedad: las medidas por la estación cercana (la sensación se corre lo mismo que la temperatura).
 * - Cielo sobre tu punto (visible del satélite, de día): con pocas nubes no llueve, diga lo que diga el modelo, y el
 *   código pasa a lo que se ve.
 * - Estación: si no ve lluvia, se desmiente la lluvia ligera del modelo (la moderada o fuerte puede ser un chubasco
 *   local que la estación, a unos km, no ve). Si ve lluvia y el modelo no, pasa a lluvia, salvo que sobre ti esté
 *   despejado.
 */
fun corregir(a: Actual, cuartos: List<Cuarto>, e: Estacion?, cobertura: Double? = null): Triple<Actual, List<Cuarto>, Int?> {
    var b = a
    if (e?.temp != null) {
        b = b.copy(temp = e.temp, sensacion = a.sensacion + (e.temp - a.temp))
        if (e.rocio != null) b = b.copy(rocio = e.rocio, humedad = humedadRelativa(e.temp, e.rocio))
    }
    val modeloLlueve = a.lluvia >= 0.05 || esLluvia(a.codigo)
    val despejado = cobertura != null && cobertura < .4
    val seco = when {
        !modeloLlueve -> false
        despejado -> true
        e != null && !e.llueve -> a.lluvia * 4 < 2.5
        else -> false
    }
    val codigo = when {
        seco -> cobertura?.let(::codigoCielo) ?: codigoEstacion(e!!)
        e != null && e.llueve && !modeloLlueve && !despejado -> codigoEstacion(e)
        // Sin lluvia de por medio, el cielo que se ve manda sobre el del modelo (solo códigos de nubes, no niebla).
        !modeloLlueve && cobertura != null && a.codigo in 0..3 -> codigoCielo(cobertura)
        else -> a.codigo
    }
    val cs = if (seco) cuartos.mapIndexed { i, q -> if (i == 0) q.copy(lluvia = 0.0) else q } else cuartos
    return Triple(b.copy(codigo = codigo, lluvia = if (seco) 0.0 else b.lluvia), cs, a.codigo.takeIf { it != codigo })
}

fun textoEstacion(e: Estacion): String {
    val que = if (e.llueve) texto(codigoEstacion(e)).lowercase() else "sin lluvia"
    val t = e.temp?.let { " · %.0f° medidos".format(it) } ?: ""
    return "Aeropuerto de ${e.nombre} (a %.0f km) · $que$t".format(e.distKm)
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
