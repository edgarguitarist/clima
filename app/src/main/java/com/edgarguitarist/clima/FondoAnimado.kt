package com.edgarguitarist.clima

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.edgarguitarist.clima.ui.Fondo
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Qué hay en el cielo; decisión pura, sin dibujo. */
data class Escena(
    val mmh: Double,          // intensidad de lluvia en mm/h (0 = sin lluvia)
    val gotas: Int,           // gotas a dibujar (0..MAX_GOTAS)
    val nubes: Boolean,
    val noche: Boolean,
    val tormenta: Boolean,
    val niebla: Boolean,
    val despejado: Boolean,   // sin lluvia, nubes ni niebla: sol de día, luna y estrellas de noche
    val inclinacion: Float,   // desplazamiento horizontal por cada unidad de caída (0..0.6)
)

const val MAX_GOTAS = 120

fun escena(a: Actual?): Escena {
    if (a == null) return Escena(0.0, 0, false, false, false, false, false, 0f)
    val c = a.codigo
    val codigoLluvia = c in 51..67 || c in 80..82 || c in 95..99
    val lluvia = codigoLluvia || a.lluvia > 0
    // lluvia en 15 min * 4 = mm/h; si el código dice lluvia pero no hay medida, se asume llovizna
    val mmh = if (lluvia) maxOf(a.lluvia * 4, if (codigoLluvia) 0.5 else 0.0) else 0.0
    val niebla = c == 45 || c == 48
    // la lluvia también va con cielo cubierto
    val nubes = lluvia || c == 2 || c == 3 || niebla || a.nubes > 50
    return Escena(
        mmh = mmh,
        gotas = if (lluvia) (25 + mmh * 12).toInt().coerceIn(25, MAX_GOTAS) else 0,
        nubes = nubes,
        noche = !a.esDia,
        tormenta = c in 95..99,
        niebla = niebla,
        despejado = !lluvia && !nubes,
        inclinacion = (a.viento / 60.0).coerceIn(0.0, 0.6).toFloat(),
    )
}

/** Destello de tormenta (alpha 0..0.12): raro (1 de cada ~3 ciclos de 8 s) y corto (0,3 s). */
fun destello(t: Float): Float {
    val ciclo = (t / 8f).toInt()
    if ((ciclo * 2654435761L ushr 7) % 3L != 0L) return 0f
    val f = t - ciclo * 8f - 3f
    return if (f in 0f..0.3f) 0.12f * (1f - f / 0.3f) else 0f
}

/** Mismos colores que tenía MainActivity: azul de día, índigo de noche, gris con lluvia o nubes. */
private fun degradado(a: Actual?): Pair<Color, Color> = when {
    a == null -> Color(0xFF1E3A8A) to Color(0xFF172554)
    a.codigo >= 51 -> if (a.esDia) Color(0xFF334155) to Color(0xFF1E293B) else Color(0xFF1E293B) to Color(0xFF0F172A)
    !a.esDia -> Color(0xFF1E1B4B) to Color(0xFF0F172A)
    a.codigo >= 3 -> Color(0xFF3B5578) to Color(0xFF1E2F4A)
    else -> Color(0xFF2563EB) to Color(0xFF1E3A8A)
}

private const val ESTRELLAS = 40

// Partículas precalculadas una vez: nada se reserva por cuadro.
private class Particulas {
    private val r = Random(7)
    val gx = FloatArray(MAX_GOTAS) { r.nextFloat() * 1.6f - 0.3f }
    val gy = FloatArray(MAX_GOTAS) { r.nextFloat() }
    val gLargo = FloatArray(MAX_GOTAS) { 0.025f + r.nextFloat() * 0.03f }   // fracción del alto
    val gVel = FloatArray(MAX_GOTAS) { 0.9f + r.nextFloat() * 0.7f }        // alturas por segundo
    val gAlfa = FloatArray(MAX_GOTAS) { 0.10f + r.nextFloat() * 0.12f }
    val ex = FloatArray(ESTRELLAS) { r.nextFloat() }
    val ey = FloatArray(ESTRELLAS) { r.nextFloat() * 0.6f }
    val eFase = FloatArray(ESTRELLAS) { r.nextFloat() * 6.28f }
    val eRad = FloatArray(ESTRELLAS) { 0.8f + r.nextFloat() * 1.4f }
    // 3 capas x 3 manchas de nube
    val nx = FloatArray(9) { r.nextFloat() }
    val ny = FloatArray(9) { r.nextFloat() * 0.5f }
    val nr = FloatArray(9) { 0.18f + r.nextFloat() * 0.14f }                // fracción del ancho
}

@Composable
fun FondoAnimado(a: Actual?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val animar = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val c = degradado(a)
    val arriba by animateColorAsState(c.first, tween(800), label = "fondoA")
    val medio by animateColorAsState(c.second, tween(800), label = "fondoB")
    val e = escena(a)
    val p = remember { Particulas() }
    var ns by remember { mutableLongStateOf(0L) }
    if (animar) LaunchedEffect(Unit) {
        val ini = androidx.compose.runtime.withFrameNanos { it }
        var ult = 0L
        while (true) androidx.compose.runtime.withFrameNanos { now ->
            if (now - ult >= 33_000_000L) { ult = now; ns = now - ini } // ~30 fps
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(arriba, medio, Fondo)))
        val t = ns / 1e9f // se lee aquí: solo redibuja, no recompone
        if (e.nubes) nubes(p, t, e.niebla)
        if (e.niebla) niebla(t)
        if (e.despejado && !e.noche) sol(t)
        if (e.despejado && e.noche) cielo(p, t)
        if (e.gotas > 0) lluvia(p, t, e)
        if (e.tormenta) destello(t).let { if (it > 0f) drawRect(Color.White.copy(alpha = it)) }
    }
}

private fun DrawScope.lluvia(p: Particulas, t: Float, e: Escena) {
    val w = size.width; val h = size.height
    val ancho = (1f + minOf(e.mmh.toFloat(), 8f) * 0.15f) * density
    for (i in 0 until e.gotas) {
        val prog = (p.gy[i] + t * p.gVel[i]) % 1f
        val x = (p.gx[i] + prog * e.inclinacion) * w
        val y = (prog * 1.2f - 0.1f) * h
        val l = p.gLargo[i] * h
        drawLine(
            Color(0xFFBFDBFE).copy(alpha = p.gAlfa[i]), Offset(x, y),
            Offset(x + l * e.inclinacion, y + l), ancho, StrokeCap.Round,
        )
    }
}

private fun DrawScope.nubes(p: Particulas, t: Float, denso: Boolean) {
    val w = size.width; val h = size.height
    val base = if (denso) 0.07f else 0.05f
    for (capa in 0 until 3) {
        val vel = 0.006f + capa * 0.004f // la capa cercana va más rápido: paralaje
        for (k in 0 until 3) {
            val i = capa * 3 + k
            val x = ((p.nx[i] + t * vel) % 1.4f - 0.2f) * w
            val y = (p.ny[i] * 0.8f + capa * 0.08f) * h
            val r = p.nr[i] * w * (1f + capa * 0.25f)
            // círculos apilados dan el borde suave sin blur ni shaders
            for (j in 0 until 3) drawCircle(Color.White.copy(alpha = base / 3f), r * (1f - j * 0.25f), Offset(x, y))
        }
    }
}

private fun DrawScope.niebla(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 3) {
        val y = h * (0.55f + i * 0.14f)
        val x = w * (0.5f + 0.15f * sin(t * 0.05f + i * 2f))
        drawRect(Color(0xFFE2E8F0).copy(alpha = 0.05f), Offset(x - w, y), Size(w * 2f, h * 0.10f))
    }
}

private fun DrawScope.sol(t: Float) {
    val pulso = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 12f) // ciclo de 12 s
    val c = Offset(size.width * 0.85f, size.height * 0.08f)
    for (j in 0 until 4) {
        drawCircle(Color(0xFFFDE68A).copy(alpha = 0.04f + 0.015f * pulso), size.width * (0.10f + j * 0.07f) * (1f + 0.05f * pulso), c)
    }
}

private fun DrawScope.cielo(p: Particulas, t: Float) {
    for (i in 0 until ESTRELLAS) {
        val br = 0.5f + 0.5f * sin(t * 1.2f + p.eFase[i])
        drawCircle(Color.White.copy(alpha = 0.15f + 0.35f * br), p.eRad[i] * density, Offset(p.ex[i] * size.width, p.ey[i] * size.height))
    }
    val c = Offset(size.width * 0.82f, size.height * 0.09f)
    for (j in 0 until 3) drawCircle(Color(0xFFE0E7FF).copy(alpha = 0.04f), size.width * (0.05f + j * 0.05f), c)
    drawCircle(Color(0xFFE0E7FF).copy(alpha = 0.18f), size.width * 0.025f, c)
}
