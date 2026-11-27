package com.edgarguitarist.clima

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Widget de pantalla de inicio: pinta el último pronóstico en caché y lo refresca en segundo plano. */
class ClimaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        pintar(ctx, manager, ids)
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)
                // Sin ubicación en segundo plano: se usan las coordenadas que guardó la app (o Guayaquil).
                val lat = if (c.contains("lat")) c.getFloat("lat", 0f).toDouble() else -2.19
                val lon = if (c.contains("lon")) c.getFloat("lon", 0f).toDouble() else -79.89
                val json = descargarPronostico(lat, lon)
                parsear(json) // valida antes de pisar la caché
                c.edit().putString("json", json).apply()
                pintar(ctx, manager, ids)
            } catch (_: Exception) {
                // sin red o respuesta rara: se queda lo que ya se ve
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Vuelve a pintar todos los widgets desde la caché, sin red. La app lo llama tras cargar datos. */
fun actualizarWidgets(ctx: Context) {
    val m = AppWidgetManager.getInstance(ctx)
    pintar(ctx, m, m.getAppWidgetIds(ComponentName(ctx, ClimaWidget::class.java)))
}

private fun pintar(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
    val v = RemoteViews(ctx.packageName, R.layout.widget_clima)
    val c = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)
    val p = try { c.getString("json", null)?.let { parsear(it) } } catch (_: Exception) { null }
    if (p != null) {
        val a = p.actual
        val hoy = p.dias.firstOrNull()
        v.setTextViewText(R.id.lugar, c.getString("lugar", null) ?: "Clima")
        v.setTextViewText(R.id.temp, "${a.temp.roundToInt()}°")
        v.setTextViewText(R.id.emoji, icono(a.codigo, a.esDia))
        v.setTextViewText(R.id.condicion, texto(a.codigo))
        v.setTextViewText(R.id.sensacion, "Sensación ${a.sensacion.roundToInt()}°")
        v.setTextViewText(R.id.maxmin, hoy?.let { "↑${it.max.roundToInt()}° ↓${it.min.roundToInt()}°" } ?: "")
        v.setTextViewText(R.id.lluvia, resumenLluvia(p.cuartos))
        v.setTextViewText(R.id.hora, a.hora.takeLast(5))
    } else {
        v.setTextViewText(R.id.lluvia, "Abre Clima para cargar el pronóstico")
    }
    val abrir = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    v.setOnClickPendingIntent(R.id.raiz, abrir)
    manager.updateAppWidget(ids, v)
}
