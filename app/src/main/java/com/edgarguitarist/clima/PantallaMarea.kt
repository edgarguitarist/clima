package com.edgarguitarist.clima

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgarguitarist.clima.ui.TextoSec
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

private val Agua = Color(0xFF38BDF8)

/** Marea del río Guayas: nivel ahora, próxima pleamar/bajamar, aguaje y la curva de ~1,5 días. */
@Composable
fun TarjetaMarea(m: Marea, horas: List<Hora>) {
    val ahora = ZonedDateTime.now(ZoneId.of("America/Guayaquil"))
    val nivel = nivelEn(m, ahora)
    val sube = nivel != null && (nivelEn(m, ahora.plusMinutes(15)) ?: nivel) > nivel
    val prox = m.extremos.firstOrNull { it.hora.isAfter(ahora) }
    val aviso = avisoMareaLluvia(m, horas)
    val hhmm = { t: ZonedDateTime -> t.toLocalTime().toString().take(5) }

    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Vidrio)
            .border(1.dp, Borde, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌊  Marea del río Guayas", color = TextoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (esAguaje(ahora)) {
                Text(
                    "AGUAJE", color = Color(0xFF0B1220), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFBBF24)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        if (nivel != null) {
            Text("%.2f m · %s".format(nivel, if (sube) "subiendo ↑" else "bajando ↓"), fontSize = 24.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (prox != null) {
            val min = Duration.between(ahora, prox.hora).toMinutes()
            Text(
                "%s a las %s · %.2f m (en %d h %02d min)".format(if (prox.alta) "Pleamar" else "Bajamar", hhmm(prox.hora), prox.altura, min / 60, min % 60),
                color = if (prox.alta) Agua else TextoSec, fontSize = 14.sp,
            )
        }
        if (esAguaje(ahora)) {
            Text("Luna nueva o llena: son las mareas más altas del mes, cuando más se inundan las zonas bajas.",
                color = Color(0xFFFDE68A), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        aviso?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        GraficoMarea(m, ahora)
        Text(m.fuente, color = Color.White.copy(alpha = .35f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** De 6 h atrás a 30 h adelante; la línea punteada es ahora; cada pleamar y bajamar con su hora y altura. */
@Composable
private fun GraficoMarea(m: Marea, ahora: ZonedDateTime) {
    val desde = ahora.minusHours(6)
    val hasta = ahora.plusHours(30)
    val pts = m.puntos.filter { !it.first.isBefore(desde) && !it.first.isAfter(hasta) }
    if (pts.size < 2) return
    val medidor = rememberTextMeasurer()
    val min = pts.minOf { it.second }
    val max = pts.maxOf { it.second }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val arriba = 30.dp.toPx(); val abajo = size.height - 22.dp.toPx()
        val span = Duration.between(desde, hasta).toMinutes().toFloat()
        fun x(t: ZonedDateTime) = Duration.between(desde, t).toMinutes() / span * size.width
        fun y(v: Double) = (abajo - (v - min) / (max - min).coerceAtLeast(.1) * (abajo - arriba)).toFloat()
        val linea = Path().apply { pts.forEachIndexed { i, (t, v) -> if (i == 0) moveTo(x(t), y(v)) else lineTo(x(t), y(v)) } }
        val relleno = Path().apply { addPath(linea); lineTo(x(pts.last().first), size.height); lineTo(x(pts.first().first), size.height); close() }
        drawPath(relleno, Brush.verticalGradient(listOf(Agua.copy(alpha = .45f), Agua.copy(alpha = .03f)), startY = arriba, endY = size.height))
        drawPath(linea, Agua, style = Stroke(2.5.dp.toPx()))
        // medianoche: separador de días
        var dia = desde.toLocalDate().plusDays(1).atStartOfDay(desde.zone)
        while (dia.isBefore(hasta)) {
            drawLine(Color.White.copy(alpha = .12f), Offset(x(dia), 0f), Offset(x(dia), size.height), 1.dp.toPx())
            val t = medidor.measure(dia.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("es")),
                TextStyle(color = TextoSec, fontSize = 10.sp))
            drawText(t, topLeft = Offset(x(dia) + 4.dp.toPx(), size.height - t.size.height))
            dia = dia.plusDays(1)
        }
        // extremos
        m.extremos.filter { it.hora.isAfter(desde) && it.hora.isBefore(hasta) }.forEach { e ->
            val p = Offset(x(e.hora), y(e.altura))
            drawCircle(if (e.alta) Agua else Color.White.copy(alpha = .6f), 3.5.dp.toPx(), p)
            val t = medidor.measure("%s %.1f".format(e.hora.toLocalTime().toString().take(5), e.altura),
                TextStyle(color = if (e.alta) Color.White else TextoSec, fontSize = 10.sp, fontWeight = if (e.alta) FontWeight.SemiBold else FontWeight.Normal))
            val ty = if (e.alta) p.y - t.size.height - 4.dp.toPx() else p.y + 5.dp.toPx()
            drawText(t, topLeft = Offset((p.x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), ty))
        }
        // ahora
        val xa = x(ahora)
        drawLine(Color.White, Offset(xa, arriba - 10.dp.toPx()), Offset(xa, abajo), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        nivelEn(m, ahora)?.let { drawCircle(Color.White, 5.dp.toPx(), Offset(xa, y(it))) }
        val t = medidor.measure("Ahora", TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
        drawText(t, topLeft = Offset((xa - t.size.width / 2).coerceIn(0f, size.width - t.size.width), 0f))
    }
}
