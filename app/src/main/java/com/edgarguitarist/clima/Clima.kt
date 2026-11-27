package com.edgarguitarist.clima

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// Open-Meteo: gratis y sin API key. Para cada punto elige el mejor modelo (ECMWF, GFS, ICON…) y
// actualiza las condiciones actuales cada 15 min.

data class Actual(
    val hora: String, val temp: Double, val sensacion: Double, val humedad: Int, val rocio: Double,
    val lluvia: Double, val codigo: Int, val nubes: Int, val presion: Double, val viento: Double,
    val dirViento: Double, val rafagas: Double, val uv: Double, val visibilidad: Double, val esDia: Boolean,
)
data class Cuarto(val hora: String, val lluvia: Double, val prob: Int)
data class Hora(
    val hora: String, val temp: Double, val sensacion: Double, val prob: Int, val lluvia: Double,
    val codigo: Int, val nubes: Int,
    // Para el detalle al tocar una hora (con valores por defecto: hay quien construye Hora con 7 campos).
    val humedad: Int = 0, val viento: Double = 0.0, val dirViento: Double = 0.0, val rafagas: Double = 0.0,
    val uv: Double = 0.0, val visibilidad: Double = 0.0,
)
data class Dia(
    val fecha: String, val codigo: Int, val max: Double, val min: Double, val sensMax: Double,
    val sensMin: Double, val lluvia: Double, val prob: Int, val amanecer: String, val atardecer: String,
    val uvMax: Double,
    val vientoMax: Double = 0.0, val rafagasMax: Double = 0.0, val dirViento: Double = 0.0, val horasLluvia: Double = 0.0,
)
data class Pronostico(val actual: Actual, val cuartos: List<Cuarto>, val horas: List<Hora>, val dias: List<Dia>)

private const val ACTUAL = "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation," +
    "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility,is_day"
private const val HORARIO = "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,cloud_cover," +
    "relative_humidity_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility"
private const val DIARIO = "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
    "apparent_temperature_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,precipitation_hours"

/** Bloqueante: llamar desde Dispatchers.IO. Devuelve el JSON tal cual, para guardarlo en caché. */
fun descargarPronostico(lat: Double, lon: Double): String {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=$ACTUAL" +
        "&minutely_15=precipitation,precipitation_probability&forecast_minutely_15=8" +
        "&hourly=$HORARIO&forecast_hours=48&daily=$DIARIO&forecast_days=10&timezone=auto"
    // Con datos móviles una petición se queda colgada de vez en cuando: se reintenta antes de rendirse.
    var ultimo: Exception? = null
    repeat(3) { intento ->
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 12_000
        try {
            if (c.responseCode != 200) error("Open-Meteo respondió ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ultimo = e
            Thread.sleep(1_000L * (intento + 1))
        } finally {
            c.disconnect()
        }
    }
    throw ultimo!!
}

/** Para mostrar: «timeout» o «Unable to resolve host» no le dicen nada a nadie. */
fun mensajeError(e: Exception): String = when (e) {
    is java.net.UnknownHostException -> "Sin conexión a internet"
    is java.net.SocketTimeoutException -> "El servidor del pronóstico tardó demasiado en responder"
    is java.io.IOException -> "Falló la conexión (${e.message ?: e.javaClass.simpleName})"
    else -> e.message ?: e.toString()
}

/** Resumen de la lluvia de las próximas 2 horas, a partir de los cuartos de hora. */
fun resumenLluvia(cuartos: List<Cuarto>): String {
    val llueve = cuartos.map { it.lluvia >= 0.1 }
    val hhmm = { c: Cuarto -> c.hora.substring(11, 16) }
    return when {
        llueve.isEmpty() || llueve.none { it } ->
            if (cuartos.any { it.prob >= 40 }) "Puede llover en las próximas 2 horas" else "Sin lluvia en las próximas 2 horas"
        llueve.all { it } -> "Lluvia durante las próximas 2 horas"
        llueve.first() -> "Lluvia hasta las ${hhmm(cuartos[llueve.indexOf(false)])}"
        else -> "Lluvia desde las ${hhmm(cuartos[llueve.indexOf(true)])}"
    }
}

// Algunos modelos dejan huecos (null): se leen como 0.
private fun JSONArray.d(i: Int) = optDouble(i).let { if (it.isNaN()) 0.0 else it }
private fun JSONArray.n(i: Int) = d(i).roundToInt()
// Campos agregados después: si una respuesta guardada en caché no los trae, valen 0.
private fun JSONObject.op(k: String, i: Int) = optJSONArray(k)?.d(i) ?: 0.0
private fun JSONObject.d(k: String) = optDouble(k).let { if (it.isNaN()) 0.0 else it }

fun parsear(json: String): Pronostico {
    val j = JSONObject(json)
    val a = j.getJSONObject("current")
    val actual = Actual(
        hora = a.getString("time"), temp = a.d("temperature_2m"), sensacion = a.d("apparent_temperature"),
        humedad = a.d("relative_humidity_2m").roundToInt(), rocio = a.d("dew_point_2m"),
        lluvia = a.d("precipitation"), codigo = a.optInt("weather_code"), nubes = a.d("cloud_cover").roundToInt(),
        presion = a.d("pressure_msl"), viento = a.d("wind_speed_10m"), dirViento = a.d("wind_direction_10m"),
        rafagas = a.d("wind_gusts_10m"), uv = a.d("uv_index"), visibilidad = a.d("visibility"),
        esDia = a.optInt("is_day", 1) == 1,
    )

    val m = j.getJSONObject("minutely_15")
    val mt = m.getJSONArray("time")
    val cuartos = (0 until mt.length()).map {
        Cuarto(mt.getString(it), m.getJSONArray("precipitation").d(it), m.getJSONArray("precipitation_probability").n(it))
    }

    val h = j.getJSONObject("hourly")
    val ht = h.getJSONArray("time")
    val horaActual = actual.hora.take(13) // "2026-11-25T09"
    val horas = (0 until ht.length()).filter { ht.getString(it) >= horaActual }.take(24).map {
        Hora(
            ht.getString(it), h.getJSONArray("temperature_2m").d(it), h.getJSONArray("apparent_temperature").d(it),
            h.getJSONArray("precipitation_probability").n(it), h.getJSONArray("precipitation").d(it),
            h.getJSONArray("weather_code").n(it), h.getJSONArray("cloud_cover").n(it),
            h.op("relative_humidity_2m", it).toInt(), h.op("wind_speed_10m", it), h.op("wind_direction_10m", it),
            h.op("wind_gusts_10m", it), h.op("uv_index", it), h.op("visibility", it),
        )
    }

    val d = j.getJSONObject("daily")
    val dt = d.getJSONArray("time")
    val dias = (0 until dt.length()).map {
        Dia(
            dt.getString(it), d.getJSONArray("weather_code").n(it), d.getJSONArray("temperature_2m_max").d(it),
            d.getJSONArray("temperature_2m_min").d(it), d.getJSONArray("apparent_temperature_max").d(it),
            d.getJSONArray("apparent_temperature_min").d(it), d.getJSONArray("precipitation_sum").d(it),
            d.getJSONArray("precipitation_probability_max").n(it), d.getJSONArray("sunrise").getString(it),
            d.getJSONArray("sunset").getString(it), d.getJSONArray("uv_index_max").d(it),
            d.op("wind_speed_10m_max", it), d.op("wind_gusts_10m_max", it), d.op("wind_direction_10m_dominant", it),
            d.op("precipitation_hours", it),
        )
    }
    return Pronostico(actual, cuartos, horas, dias)
}

// Códigos WMO que usa Open-Meteo
private val CODIGOS = mapOf(
    0 to ("Despejado" to "☀️"), 1 to ("Mayormente despejado" to "🌤️"), 2 to ("Parcialmente nublado" to "⛅"),
    3 to ("Nublado" to "☁️"), 45 to ("Niebla" to "🌫️"), 48 to ("Niebla con escarcha" to "🌫️"),
    51 to ("Llovizna ligera" to "🌦️"), 53 to ("Llovizna" to "🌦️"), 55 to ("Llovizna intensa" to "🌧️"),
    56 to ("Llovizna helada" to "🌧️"), 57 to ("Llovizna helada intensa" to "🌧️"),
    61 to ("Lluvia ligera" to "🌦️"), 63 to ("Lluvia" to "🌧️"), 65 to ("Lluvia fuerte" to "🌧️"),
    66 to ("Lluvia helada" to "🌧️"), 67 to ("Lluvia helada fuerte" to "🌧️"),
    71 to ("Nevada ligera" to "🌨️"), 73 to ("Nevada" to "🌨️"), 75 to ("Nevada fuerte" to "❄️"),
    77 to ("Granizo fino" to "🌨️"), 80 to ("Lluvia ligera a ratos" to "🌦️"), 81 to ("Lluvia a ratos" to "🌧️"),
    82 to ("Aguaceros fuertes" to "⛈️"), 85 to ("Chubascos de nieve" to "🌨️"),
    86 to ("Chubascos de nieve fuertes" to "❄️"), 95 to ("Tormenta eléctrica" to "⛈️"),
    96 to ("Tormenta con granizo" to "⛈️"), 99 to ("Tormenta con granizo fuerte" to "⛈️"),
)

/** Una frase arriba de todo que responde «¿necesito paraguas?», con el satélite si ve una tormenta encima. */
fun titular(cuartos: List<Cuarto>, topeSatelite: Int?): String {
    val lloviendo = cuartos.firstOrNull()?.let { it.lluvia >= 0.1 } == true
    val viene = cuartos.any { it.lluvia >= 0.1 }
    return when {
        !lloviendo && topeSatelite != null && topeSatelite <= -52 -> "⛈️ El satélite ve una tormenta encima: puede llover en cualquier momento"
        lloviendo -> "☔ " + resumenLluvia(cuartos)
        viene -> "☂️ " + resumenLluvia(cuartos) + ": lleva paraguas"
        cuartos.any { it.prob >= 40 } -> "🌂 Puede llover: mejor lleva paraguas"
        else -> "😎 Sin lluvia en las próximas 2 horas"
    }
}

/** Lo que llovió en los últimos 15 min, como intensidad (escala usual: ligera < 2,5 mm/h < moderada < 7,6 < fuerte). */
fun lluviaAhora(mm15: Double): String? {
    if (mm15 < 0.05) return null
    val porHora = mm15 * 4
    val tipo = when { porHora < 2.5 -> "ligera"; porHora < 7.6 -> "moderada"; porHora < 50 -> "fuerte"; else -> "torrencial" }
    return "Lluvia $tipo · %.1f mm/h".format(porHora)
}

fun texto(codigo: Int) = CODIGOS[codigo]?.first ?: "Desconocido"
fun icono(codigo: Int, esDia: Boolean = true): String {
    val i = CODIGOS[codigo]?.second ?: "❔"
    if (esDia) return i
    // De noche, nada de sol: luna si está despejado y la versión sin sol del resto.
    return when (i) { "☀️", "🌤️" -> "🌙"; "⛅" -> "☁️"; "🌦️" -> "🌧️"; else -> i }
}

fun rumbo(grados: Double): String =
    listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[(((grados % 360 + 360) % 360) / 45).roundToInt() % 8]

fun nivelUV(uv: Double) = when {
    uv < 3 -> "Bajo"; uv < 6 -> "Moderado"; uv < 8 -> "Alto"; uv < 11 -> "Muy alto"; else -> "Extremo"
}
