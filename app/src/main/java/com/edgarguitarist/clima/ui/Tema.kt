package com.edgarguitarist.clima.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Azul = Color(0xFF2563EB)
val Fondo = Color(0xFF0B1220)
val Superficie = Color(0xFF111A2E)
val TextoSec = Color(0xFF94A3B8)
val Ambar = Color(0xFFFBBF24)
val Celeste = Color(0xFF60A5FA)

@Composable
fun TemaClima(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Azul,
            onPrimary = Color.White,
            background = Fondo,
            surface = Fondo,
            surfaceContainer = Superficie,
            onSurface = Color.White,
            onSurfaceVariant = TextoSec,
        ),
        content = content,
    )
}
