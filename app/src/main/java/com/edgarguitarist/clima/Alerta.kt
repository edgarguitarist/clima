package com.edgarguitarist.clima

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class Aviso(val titulo: String, val texto: String)

private const val CANAL = "lluvia"
private const val TRABAJO = "alerta_lluvia"
private const val PAUSA_MS = 3 * 60 * 60 * 1000L

/** Decide si hay que avisar: lluvia en la próxima hora o tormenta sobre el lugar. Función pura, sin Android. */
fun debeAvisar(lugar: String, cuartos: List<Cuarto>, topeSatelite: Int?): Aviso? {
    val i = cuartos.take(4).indexOfFirst { it.lluvia >= 0.2 && it.prob >= 50 }
    if (i >= 0) {
        val q = cuartos[i]
        val texto = if (i == 0) "Está lloviendo en $lugar"
        else "Lluvia en $lugar desde las ${q.hora.takeLast(5)} (≈ ${String.format(Locale("es"), "%.1f", q.lluvia * 4)} mm/h)"
        return Aviso("Aviso de lluvia", texto)
    }
    if (topeSatelite != null && topeSatelite <= -52) return Aviso("Aviso de lluvia", "Tormenta sobre $lugar (satélite)")
    return null
}

/** La próxima pleamar dentro de 3 h que coincide (±2 h) con lluvia fuerte prevista (>= 1 mm/h y prob >= 50). Pura. */
fun pleamarEnRiesgo(extremos: List<Extremo>, horas: List<Hora>, ahora: ZonedDateTime): Extremo? {
    val p = extremos.filter { it.alta && !it.hora.isBefore(ahora) }.minByOrNull { it.hora } ?: return null
    if (Duration.between(ahora, p.hora).toMinutes() > 180) return null
    val llueve = horas.any {
        val t = LocalDateTime.parse(it.hora).atZone(ahora.zone)
        abs(Duration.between(t, p.hora).toMinutes()) <= 120 && it.lluvia >= 1.0 && it.prob >= 50
    }
    return if (llueve) p else null
}

fun textoPleamar(p: Extremo, aguaje: Boolean) =
    Aviso("🌊 Marea alta y lluvia", "Pleamar de ${String.format(Locale("es"), "%.2f", p.altura)} m a las ${p.hora.toLocalTime().toString().take(5)} " +
        "con lluvia fuerte prevista: riesgo de inundación en zonas bajas" + if (aguaje) " · aguaje" else "")

fun avisoPleamar(extremos: List<Extremo>, horas: List<Hora>, ahora: ZonedDateTime, aguaje: Boolean): Aviso? =
    pleamarEnRiesgo(extremos, horas, ahora)?.let { textoPleamar(it, aguaje) }

private fun hayPermiso(ctx: Context) =
    Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Cada 15 min mira los lugares con `avisar` y notifica si viene lluvia. */
class AlertaLluvia(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!hayPermiso(ctx)) return Result.success()
        val estado = ctx.getSharedPreferences("alertas", Context.MODE_PRIVATE)
        val lugares = Lugares.leer(ctx).filter { it.avisar }
        // La marea es la misma para todos los lugares: se descarga una sola vez y solo si alguno está cerca del río.
        val marea = if (lugares.any { mareaAplica(it.lat, it.lon) }) runCatching { descargarMarea() }.getOrNull() else null
        for (l in lugares) {
            // Un lugar que falla no impide revisar los demás; se reintenta en la siguiente vuelta.
            runCatching {
                val pron = parsear(descargarPronostico(l.lat, l.lon))
                val cuartos = pron.cuartos
                val aviso = debeAvisar(l.nombre, cuartos, observarSatelite(l.lat, l.lon)?.temp)
                val clave = "ultimo_" + l.nombre
                val ahora = System.currentTimeMillis()
                if (aviso == null) {
                    estado.edit().remove(clave).apply() // dejó de llover: el próximo aviso ya no espera las 3 h
                } else if (ahora - estado.getLong(clave, 0L) >= PAUSA_MS) {
                    notificar(l.nombre, aviso)
                    estado.edit().putLong(clave, ahora).apply()
                }
                if (marea != null && mareaAplica(l.lat, l.lon)) {
                    val t = ZonedDateTime.now(ZoneId.of("America/Guayaquil"))
                    val p = pleamarEnRiesgo(marea.extremos, pron.horas, t)
                    val marca = p?.hora?.toEpochSecond() ?: 0L
                    // Una sola vez por pleamar, aunque haya varios lugares o varias vueltas.
                    if (p != null && estado.getLong("pleamar", 0L) != marca) {
                        notificar("pleamar", textoPleamar(p, esAguaje(t)), 0x7EA)
                        estado.edit().putLong("pleamar", marca).apply()
                    }
                }
            }
        }
        return Result.success()
    }

    private fun notificar(lugar: String, aviso: Aviso, id: Int = lugar.hashCode()) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManager.IMPORTANCE_DEFAULT).setName("Aviso de lluvia").build(),
        )
        val abrir = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_lluvia).setContentTitle(aviso.titulo).setContentText(aviso.texto)
            .setContentIntent(abrir).setAutoCancel(true).build()
        @Suppress("MissingPermission") // ya se comprobó al inicio de doWork
        nm.notify(id, n)
    }
}

/** Activa la vigilancia si hay algún lugar con aviso; si no, la cancela. Idempotente (KEEP). */
fun programarAlertas(ctx: Context) {
    val wm = WorkManager.getInstance(ctx)
    if (Lugares.leer(ctx).none { it.avisar }) {
        wm.cancelUniqueWork(TRABAJO)
        return
    }
    val pedido = PeriodicWorkRequestBuilder<AlertaLluvia>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, pedido)
}
