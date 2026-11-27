package com.edgarguitarist.clima

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/** Ruta del Lottie (Meteocons, estilo fill) en assets para un código WMO; de noche cambia el sol por luna. */
fun archivoIcono(codigo: Int, esDia: Boolean): String {
    val dn = if (esDia) "day" else "night"
    val n = when (codigo) {
        0 -> "clear-$dn"
        1, 2 -> "partly-cloudy-$dn"
        3 -> "overcast"
        45, 48 -> "fog-$dn"
        51, 53 -> "partly-cloudy-$dn-drizzle"
        55 -> "drizzle"
        56, 57, 66, 67 -> "sleet"
        61, 80 -> "partly-cloudy-$dn-rain"
        63, 65, 81 -> "rain"
        71, 85 -> "partly-cloudy-$dn-snow"
        73, 75, 86 -> "snow"
        77 -> "hail"
        82 -> "thunderstorms-rain"
        95 -> "thunderstorms-$dn-rain"
        96, 99 -> "thunderstorms-$dn-hail"
        else -> "overcast" // código desconocido: nube neutra
    }
    return "iconos/$n.json"
}

/** Icono del tiempo animado (Lottie). Si el archivo no carga, cae al emoji de [icono]. */
@Composable
fun IconoClima(codigo: Int, esDia: Boolean = true, tam: Dp, animado: Boolean = true) {
    val carga = rememberLottieComposition(LottieCompositionSpec.Asset(archivoIcono(codigo, esDia)))
    Box(Modifier.size(tam), contentAlignment = Alignment.Center) {
        if (carga.isFailure) {
            val sp = with(LocalDensity.current) { (tam * 0.8f).toSp() }
            Text(icono(codigo, esDia), fontSize = sp)
        } else if (animado) {
            val progreso = animateLottieCompositionAsState(carga.value, iterations = LottieConstants.IterateForever)
            LottieAnimation(carga.value, { progreso.value }, Modifier.size(tam))
        } else {
            LottieAnimation(carga.value, { 0.5f }, Modifier.size(tam)) // cuadro fijo a mitad del ciclo
        }
    }
}
