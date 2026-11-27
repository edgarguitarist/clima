package com.edgarguitarist.clima.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgarguitarist.clima.Aire
import com.edgarguitarist.clima.consejoAire
import com.edgarguitarist.clima.nivelAire

private val Vidrio = Color(0x14FFFFFF)
private val Borde = Color(0x1AFFFFFF)

// Mismos colores que nivelAire, repartidos a lo largo de 0–300.
private val Escala = arrayOf(
    0f to Color(0xFF22C55E), .17f to Color(0xFFFACC15), .33f to Color(0xFFF97316),
    .5f to Color(0xFFEF4444), .67f to Color(0xFFA855F7), 1f to Color(0xFF9F1239),
)

@Composable
fun TarjetaAire(a: Aire) {
    val (nombre, argb) = nivelAire(a.aqi)
    val color = Color(argb)
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Vidrio)
            .border(1.dp, Borde, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Text("🌫️  Calidad del aire", color = TextoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${a.aqi}", fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(nombre, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
        // Barra 0–300 con marcador en el valor actual.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp).height(14.dp)) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(colorStops = Escala)),
            )
            val d = 14.dp
            Box(
                Modifier.offset(x = (maxWidth - d) * (a.aqi.coerceIn(0, 300) / 300f)).size(d).clip(CircleShape)
                    .background(Color.White).border(2.dp, color, CircleShape),
            )
        }
        Text(consejoAire(a.aqi), color = TextoSec, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato("PM2.5", "%.1f".format(a.pm25), "µg/m³")
            Dato("PM10", "%.1f".format(a.pm10), "µg/m³")
            Dato("O₃", "%.0f".format(a.ozono), "µg/m³")
            Dato("NO₂", "%.0f".format(a.no2), "µg/m³")
        }
        val peor = a.aqiHoras.withIndex().maxByOrNull { it.value }
        if (peor != null) {
            Text(
                "Peor en las próximas 24 h: ${peor.value} a las ${a.horas[peor.index].takeLast(5)}",
                color = Color.White.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String, unidad: String) {
    Column {
        Text(nombre, color = TextoSec, fontSize = 11.sp)
        Text(valor, fontSize = 16.sp)
        Text(unidad, color = TextoSec, fontSize = 10.sp)
    }
}
