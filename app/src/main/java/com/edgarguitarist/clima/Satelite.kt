package com.edgarguitarist.clima

import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

// Observación real sobre tu ubicación: el pronóstico es un modelo y puede no ver una tormenta que ya está
// encima. El infrarrojo de GOES-East (cada 10 min, ~30 min de retraso) dice qué tan fríos están los topes de
// las nubes: bajo -38 °C casi siempre llueve, y cuanto más frío, más fuerte (es lo mismo que pinta el mapa).

data class Observacion(val hora: String, val temp: Int?) // temp del tope más frío cerca; null si no hay nube fría

// Colores de la paleta de GIBS para la banda 13 (r, g, b, °C). Los grises se resuelven aparte.
private val PALETA = intArrayOf(
    127,0,127,-91, 140,13,135,-90, 153,25,142,-89, 165,38,150,-88, 178,51,157,-87, 191,64,165,-86, 204,76,173,-85,
    217,89,180,-84, 229,102,188,-83, 242,114,195,-82, 255,127,203,-81, 26,0,0,-70, 51,0,0,-69, 77,0,0,-68,
    102,0,0,-67, 128,0,0,-66, 153,0,0,-65, 179,0,0,-64, 204,0,0,-63, 230,0,0,-62, 255,0,0,-61, 255,26,0,-60,
    255,51,0,-59, 255,77,0,-58, 255,102,0,-57, 255,128,0,-56, 255,153,0,-55, 255,179,0,-54, 255,204,0,-53,
    255,230,0,-52, 255,255,0,-51, 230,255,0,-50, 204,255,0,-49, 179,255,0,-48, 153,255,0,-47, 128,255,0,-46,
    102,255,0,-45, 77,255,0,-44, 51,255,0,-43, 26,255,0,-42, 0,255,0,-41, 0,234,10,-40, 0,212,19,-39,
    0,191,29,-38, 0,170,38,-37, 0,149,48,-36, 0,128,58,-35, 0,106,67,-34, 0,85,77,-33, 0,64,86,-32,
    0,42,96,-31, 0,21,105,-30, 0,0,115,-30, 0,0,125,-29, 0,13,122,-29, 0,26,129,-28, 0,38,136,-28,
    0,51,143,-27, 0,64,150,-27, 0,76,157,-26, 0,89,164,-26, 0,102,171,-25, 0,115,178,-25, 0,128,185,-24,
    0,140,192,-24, 0,153,199,-23, 0,166,206,-23, 0,178,213,-22, 0,191,220,-22, 0,204,227,-21, 0,217,234,-21,
    0,230,241,-20, 0,242,248,-20, 0,255,255,-19,
)

/** Temperatura de un color de la paleta, o null si se parece más a un gris (suelo o nube tibia). */
private fun tempDeColor(r: Int, g: Int, b: Int): Int? {
    var mejor = Int.MAX_VALUE
    var t: Int? = null
    for (k in PALETA.indices step 4) {
        val d = (PALETA[k] - r) * (PALETA[k] - r) + (PALETA[k + 1] - g) * (PALETA[k + 1] - g) + (PALETA[k + 2] - b) * (PALETA[k + 2] - b)
        if (d < mejor) { mejor = d; t = PALETA[k + 3] }
    }
    val v = (r + g + b) / 3 // el gris más parecido; si está más cerca que cualquier color, es gris
    return if ((v - r) * (v - r) + (v - g) * (v - g) + (v - b) * (v - b) < mejor) null else t
}

/**
 * Tope más frío entre los píxeles ARGB dados (un cuadro de ~17 km alrededor tuyo), o null si no hay nube fría.
 * Los grises son ambiguos: la paleta usa grises para -71 a -78 °C (núcleos de tormenta) y también para suelo
 * tibio. Se toman como núcleo solo si en el cuadro también está el anillo rojo de -66 °C o menos.
 */
fun topeMasFrio(argb: IntArray): Int? {
    var frio: Int? = null
    var hayGris = false
    for (p in argb) {
        if ((p ushr 24) < 10) continue
        val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
        val t = if (maxOf(r, g, b) - minOf(r, g, b) < 20) (if (r > 210) -80 else null) else tempDeColor(r, g, b)
        if (t == null) hayGris = true else if (frio == null || t < frio) frio = t
    }
    if (hayGris && frio != null && frio <= -66) frio = minOf(frio, -75)
    return frio?.takeIf { it <= -38 }
}

fun textoSatelite(t: Int?): String = when {
    t == null -> "Sin nubes de lluvia encima"
    t <= -66 -> "Tormenta fuerte encima"
    t <= -52 -> "Tormenta encima"
    else -> "Nubes de lluvia encima"
}

/** Bloqueante: llamar desde Dispatchers.IO. Null si no se pudo consultar. */
fun observarSatelite(lat: Double, lon: Double): Observacion? = runCatching {
    val n = 64 // zoom 6: ~2,4 km por píxel, la resolución nativa del infrarrojo
    val xf = (lon + 180) / 360 * n
    val la = lat * PI / 180
    val yf = (1 - ln(tan(la) + 1 / cos(la)) / PI) / 2 * n
    val tx = floor(xf).toInt(); val ty = floor(yf).toInt()
    val c = URL("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/default/" +
        "GoogleMapsCompatible_Level6/6/$ty/$tx.png").openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    try {
        if (c.responseCode != 200) return@runCatching null
        val hora = c.getHeaderField("Layer-Time-Actual") ?: ""
        val bmp = BitmapFactory.decodeStream(c.inputStream) ?: return@runCatching null
        // shortcut: cerca del borde del mosaico el cuadro se recorta; bastaría pedir también el vecino.
        val px = ((xf - tx) * 256).toInt().coerceIn(3, 252)
        val py = ((yf - ty) * 256).toInt().coerceIn(3, 252)
        val argb = IntArray(49)
        bmp.getPixels(argb, 0, 7, px - 3, py - 3, 7, 7)
        Observacion(hora, topeMasFrio(argb))
    } finally {
        c.disconnect()
    }
}.getOrNull()

// Visible de GOES-East (banda 2, ~1 km, cada 10 min): de día dice si sobre tu punto exacto hay nubes o sol, cosa que
// el infrarrojo no distingue en nubes bajas. GIBS la entrega en grises con gamma: suelo y agua quedan bajo ~100,
// cúmulos y estratos sobre ~130 (reflectancia ~0,26).

private const val UMBRAL_NUBE = 130

/** Fracción de píxeles nublados (0 a 1) entre los ARGB grises dados. */
fun coberturaVisible(argb: IntArray): Double = argb.count { ((it shr 16) and 255) >= UMBRAL_NUBE }.toDouble() / argb.size

/** Solo con el sol alto: temprano o al atardecer todo se ve oscuro y parecería despejado. */
fun solAlto(horaUtc: java.time.Instant, lon: Double): Boolean {
    val solar = horaUtc.atZone(java.time.ZoneOffset.UTC).let { it.hour + it.minute / 60.0 } + lon / 15
    return ((solar % 24) + 24) % 24 in 8.0..16.5
}

fun codigoCielo(cobertura: Double): Int = when {
    cobertura < .15 -> 0; cobertura < .4 -> 1; cobertura < .75 -> 2; else -> 3
}

fun textoCielo(cobertura: Double): String = when {
    cobertura < .15 -> "despejado"; cobertura < .4 -> "pocas nubes"; cobertura < .75 -> "nubes dispersas"; else -> "nublado"
}

/** Bloqueante. {"cobertura", "hora"} sobre un cuadro de ~6 km alrededor del punto, o null de noche o si falla. */
fun observarCielo(lat: Double, lon: Double): org.json.JSONObject? = runCatching {
    val n = 128 // zoom 7: ~1,2 km por píxel, el máximo de esta capa en GIBS
    val xf = (lon + 180) / 360 * n
    val la = lat * PI / 180
    val yf = (1 - ln(tan(la) + 1 / cos(la)) / PI) / 2 * n
    val tx = floor(xf).toInt(); val ty = floor(yf).toInt()
    val c = URL("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band2_Red_Visible_1km/default/default/" +
        "GoogleMapsCompatible_Level7/7/$ty/$tx.png").openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 10_000
    try {
        if (c.responseCode != 200) return@runCatching null
        val hora = c.getHeaderField("Layer-Time-Actual") ?: return@runCatching null
        if (!solAlto(java.time.Instant.parse(hora), lon)) return@runCatching null
        val bmp = BitmapFactory.decodeStream(c.inputStream) ?: return@runCatching null
        // shortcut: cerca del borde del mosaico el cuadro se recorta; bastaría pedir también el vecino.
        val px = ((xf - tx) * 256).toInt().coerceIn(2, 253)
        val py = ((yf - ty) * 256).toInt().coerceIn(2, 253)
        val argb = IntArray(25)
        bmp.getPixels(argb, 0, 5, px - 2, py - 2, 5, 5)
        org.json.JSONObject().put("cobertura", coberturaVisible(argb)).put("hora", hora)
    } finally {
        c.disconnect()
    }
}.getOrNull()
