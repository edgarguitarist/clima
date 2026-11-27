package com.edgarguitarist.clima

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// Calidad del aire: Open-Meteo Air Quality (gratis, sin API key). El polen no se pide: fuera de Europa viene null.

data class Aire(
    val hora: String, val aqi: Int, val pm25: Double, val pm10: Double, val ozono: Double,
    val no2: Double, val co: Double, val polvo: Double, val uv: Double,
    val horas: List<String>, val aqiHoras: List<Int>, // próximas 24 h
)

private const val AIRE_ACTUAL = "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,carbon_monoxide,dust,uv_index"

/** Bloqueante: llamar desde Dispatchers.IO. Descarga y parsea. */
fun descargarAire(lat: Double, lon: Double): Aire {
    val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
        "&current=$AIRE_ACTUAL&hourly=us_aqi,pm2_5&forecast_hours=24&timezone=auto"
    var ultimo: Exception? = null
    repeat(3) { intento -> // 1 intento + 2 reintentos
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 12_000
        try {
            if (c.responseCode != 200) error("Open-Meteo (aire) respondió ${c.responseCode}")
            return parsearAire(c.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            ultimo = e
            Thread.sleep(1_000L * (intento + 1))
        } finally {
            c.disconnect()
        }
    }
    throw ultimo!!
}

private fun JSONObject.d(k: String) = optDouble(k).let { if (it.isNaN()) 0.0 else it }

/** Los huecos (null) se leen como 0; en la serie horaria se descartan. */
fun parsearAire(json: String): Aire {
    val j = JSONObject(json)
    val a = j.getJSONObject("current")
    val h = j.optJSONObject("hourly")
    val t = h?.optJSONArray("time")
    val q = h?.optJSONArray("us_aqi")
    val pares = if (t == null || q == null) emptyList() else
        (0 until minOf(t.length(), q.length())).filter { !q.isNull(it) }.map { t.getString(it) to q.getDouble(it).roundToInt() }
    return Aire(
        hora = a.optString("time"), aqi = a.d("us_aqi").roundToInt(), pm25 = a.d("pm2_5"), pm10 = a.d("pm10"),
        ozono = a.d("ozone"), no2 = a.d("nitrogen_dioxide"), co = a.d("carbon_monoxide"), polvo = a.d("dust"),
        uv = a.d("uv_index"), horas = pares.map { it.first }, aqiHoras = pares.map { it.second },
    )
}

/** Categoría US-AQI y su color ARGB. */
fun nivelAire(aqi: Int): Pair<String, Long> = when {
    aqi <= 50 -> "Buena" to 0xFF22C55E
    aqi <= 100 -> "Moderada" to 0xFFFACC15
    aqi <= 150 -> "Dañina para sensibles" to 0xFFF97316
    aqi <= 200 -> "Dañina" to 0xFFEF4444
    aqi <= 300 -> "Muy dañina" to 0xFFA855F7
    else -> "Peligrosa" to 0xFF9F1239
}

fun consejoAire(aqi: Int): String = when {
    aqi <= 50 -> "Aire limpio: ideal para actividades al aire libre"
    aqi <= 100 -> "Bien para salir; personas muy sensibles, eviten esfuerzo largo al aire libre"
    aqi <= 150 -> "Niños, mayores y personas con asma o problemas respiratorios: reduzcan el esfuerzo al aire libre"
    aqi <= 200 -> "Evita el ejercicio intenso en la calle; los grupos sensibles, mejor en interiores"
    aqi <= 300 -> "Quédate en interiores y evita el ejercicio al aire libre; usa mascarilla si sales"
    else -> "Emergencia: permanece en interiores con las ventanas cerradas"
}
