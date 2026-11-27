package com.edgarguitarist.clima

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un lugar guardado. `avisar`: el aviso de lluvia en segundo plano vigila este lugar. */
data class Lugar(val nombre: String, val lat: Double, val lon: Double, val avisar: Boolean = false, val detalle: String = "")

// Los de Edgar de entrada: pasa entre Guayaquil y Durán.
private val INICIALES = listOf(
    Lugar("Guayaquil", -2.1894, -79.8891, avisar = true, detalle = "Guayas, Ecuador"),
    Lugar("Durán", -2.1716, -79.8296, avisar = true, detalle = "Guayas, Ecuador"),
)

object Lugares {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lugares", Context.MODE_PRIVATE)

    fun leer(ctx: Context): List<Lugar> {
        val json = prefs(ctx).getString("lista", null) ?: return INICIALES
        return runCatching { desdeJson(json) }.getOrDefault(INICIALES)
    }

    fun guardar(ctx: Context, lista: List<Lugar>) = prefs(ctx).edit().putString("lista", aJson(lista)).apply()

    fun aJson(lista: List<Lugar>): String = JSONArray(lista.map {
        JSONObject().put("nombre", it.nombre).put("lat", it.lat).put("lon", it.lon).put("avisar", it.avisar).put("detalle", it.detalle)
    }).toString()

    fun desdeJson(json: String): List<Lugar> {
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Lugar(o.getString("nombre"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("avisar"), o.optString("detalle"))
        }
    }

    /**
     * Buscador de Open-Meteo (gratis, sin key). Bloqueante: llamar desde Dispatchers.IO.
     * Ordena mal por relevancia («Durán» daba Oklahoma y Francia antes que Ecuador), así que primero se busca
     * en el país del celular y después en el resto del mundo.
     */
    fun buscar(texto: String, pais: String = java.util.Locale.getDefault().country): List<Lugar> {
        val q = URLEncoder.encode(texto.trim(), "UTF-8")
        val base = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=es&format=json"
        val local = if (pais.length == 2) runCatching { pedir("$base&countryCode=$pais") }.getOrDefault(emptyList()) else emptyList()
        return (local + pedir(base)).distinctBy { "%.2f,%.2f".format(it.lat, it.lon) }.take(10)
    }

    private fun pedir(url: String): List<Lugar> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) error("El buscador respondió ${c.responseCode}")
            return parsearBusqueda(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    fun parsearBusqueda(json: String): List<Lugar> {
        val r = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map {
            val o = r.getJSONObject(it)
            Lugar(
                o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"),
                detalle = listOf(o.optString("admin1"), o.optString("country")).filter { s -> s.isNotBlank() }.joinToString(", "),
            )
        }
    }
}
