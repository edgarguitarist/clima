package com.edgarguitarist.clima

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Widget 2×2: temperatura y una frase corta que responde "¿llueve pronto?". */
class ClimaWidgetChico : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        pintarChico(ctx, manager, ids)
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refrescarCache(ctx)
                pintarChico(ctx, manager, ids)
            } catch (_: Exception) {
                // sin red o respuesta rara: se queda lo que ya se ve
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Respuesta en pocas palabras sobre la lluvia de las próximas 2 h. Llueve = al menos 0,1 mm. */
fun lluviaCorta(cuartos: List<Cuarto>, ahora: LocalDateTime): String {
    // Minutos hasta cada cuarto, redondeados a 5; un cuarto ya empezado cuenta como 0 (ahora).
    fun min(c: Cuarto): Int {
        val m = Duration.between(ahora, LocalDateTime.parse(c.hora)).toMinutes().toInt()
        return if (m <= 0) 0 else maxOf(5, (m / 5.0).roundToInt() * 5)
    }
    val llueve = { c: Cuarto -> c.lluvia >= 0.1 }
    // Solo cuartos vigentes: el que empezó hace 15 min o más ya terminó.
    val vigentes = cuartos.filter { Duration.between(LocalDateTime.parse(it.hora), ahora).toMinutes() < 15 }
    val primero = vigentes.firstOrNull() ?: return "Sin datos"
    if (llueve(primero) && min(primero) == 0) {
        val fin = vigentes.firstOrNull { !llueve(it) } ?: return "Llueve ahora"
        return "Para en ${min(fin)} min"
    }
    val ini = vigentes.firstOrNull(llueve) ?: return "Sin lluvia 2 h"
    return "Lluvia en ${min(ini)} min"
}

internal fun pintarChico(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
    val v = RemoteViews(ctx.packageName, R.layout.widget_chico)
    val c = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)
    val p = try { c.getString("json", null)?.let { parsear(it) } } catch (_: Exception) { null }
    if (p != null) {
        val a = p.actual
        v.setTextViewText(R.id.lugar, c.getString("lugar", null) ?: "Clima")
        v.setTextViewText(R.id.temp, "${a.temp.roundToInt()}°")
        v.setTextViewText(R.id.emoji, icono(a.codigo, a.esDia))
        v.setTextViewText(R.id.lluvia, lluviaCorta(p.cuartos, LocalDateTime.now()))
    } else {
        v.setTextViewText(R.id.lluvia, "Abre Clima")
    }
    val abrir = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    v.setOnClickPendingIntent(R.id.raiz, abrir)
    manager.updateAppWidget(ids, v)
}
