package com.edgarguitarist.clima

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

// Solo con el LocationManager del sistema: sin Google Play Services, así anda en cualquier Android.

/** Ubicación actual, o null si no hay permiso o no llega en 15 s. */
@SuppressLint("MissingPermission") // el permiso lo pide MainActivity antes de llamar
suspend fun ubicacionActual(ctx: Context): Location? {
    val lm = ctx.getSystemService(LocationManager::class.java)
    val proveedores = lm.getProviders(true)
    // Una posición de hace menos de 10 min sirve: el pronóstico tiene resolución de kilómetros.
    proveedores.mapNotNull { lm.getLastKnownLocation(it) }
        .filter { System.currentTimeMillis() - it.time < 10 * 60_000 }
        .minByOrNull { it.accuracy }?.let { return it }
    val proveedor = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER)
        .firstOrNull { it in proveedores } ?: return proveedores.firstNotNullOfOrNull { lm.getLastKnownLocation(it) }
    return withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { cont ->
            val cancelar = CancellationSignal()
            cont.invokeOnCancellation { cancelar.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, proveedor, cancelar, ContextCompat.getMainExecutor(ctx)) {
                cont.resume(it)
            }
        }
    } ?: proveedores.firstNotNullOfOrNull { lm.getLastKnownLocation(it) }
}

suspend fun nombreLugar(ctx: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION") // la versión asíncrona es de API 33; esta corre ya en IO
        Geocoder(ctx, Locale("es")).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}
