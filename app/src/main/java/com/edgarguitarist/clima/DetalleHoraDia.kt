package com.edgarguitarist.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgarguitarist.clima.ui.Ambar
import com.edgarguitarist.clima.ui.Celeste
import com.edgarguitarist.clima.ui.Superficie
import com.edgarguitarist.clima.ui.TextoSec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Al tocar una hora o un día: todo lo que se sabe de ese momento, sin saturar la pantalla principal.

private val diaLargo = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))

/** Intensidad en palabras para lluvia por hora (mm en esa hora = mm/h). */
fun intensidad(mmH: Double) = when {
    mmH < 0.1 -> "sin lluvia"; mmH < 2.5 -> "ligera"; mmH < 7.6 -> "moderada"; mmH < 50 -> "fuerte"; else -> "torrencial"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaHora(h: Hora, cerrar: () -> Unit) {
    val dia = h.hora.substring(11, 13).toInt() in 6..17
    ModalBottomSheet(onDismissRequest = cerrar, containerColor = Superficie) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Encabezado(
                LocalDate.parse(h.hora.take(10)).format(diaLargo).replaceFirstChar { it.uppercase() } + " · " + h.hora.substring(11, 16),
                h.codigo, dia, h.temp, "Sensación ${h.sensacion.roundToInt()}°",
            )
            Grilla(
                listOf(
                    "💧 Lluvia" to "%.1f mm · %s".format(h.lluvia, intensidad(h.lluvia)),
                    "🎲 Probabilidad" to "${h.prob}%",
                    "🌬️ Viento" to "${h.viento.roundToInt()} km/h del ${rumbo(h.dirViento)}",
                    "💨 Ráfagas" to "${h.rafagas.roundToInt()} km/h",
                    "💦 Humedad" to "${h.humedad}%",
                    "☁️ Nubosidad" to "${h.nubes}%",
                    "☀️ Índice UV" to "%.1f · %s".format(h.uv, nivelUV(h.uv)),
                    "👁️ Visibilidad" to "%.0f km".format(h.visibilidad / 1000),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDia(d: Dia, horas: List<Hora>, cerrar: () -> Unit) {
    val delDia = horas.filter { it.hora.startsWith(d.fecha) }
    ModalBottomSheet(onDismissRequest = cerrar, containerColor = Superficie) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Encabezado(
                LocalDate.parse(d.fecha).format(diaLargo).replaceFirstChar { it.uppercase() },
                d.codigo, true, d.max, "Mínima ${d.min.roundToInt()}° · sensación hasta ${d.sensMax.roundToInt()}°",
            )
            Grilla(
                listOf(
                    "☔ Lluvia total" to "%.1f mm".format(d.lluvia),
                    "🎲 Probabilidad" to "${d.prob}%",
                    "⏱️ Horas con lluvia" to "%.0f h".format(d.horasLluvia),
                    "☀️ UV máximo" to "%.0f · %s".format(d.uvMax, nivelUV(d.uvMax)),
                    "🌬️ Viento máx." to "${d.vientoMax.roundToInt()} km/h del ${rumbo(d.dirViento)}",
                    "💨 Ráfagas máx." to "${d.rafagasMax.roundToInt()} km/h",
                    "🌅 Amanecer" to d.amanecer.substring(11, 16),
                    "🌇 Atardecer" to d.atardecer.substring(11, 16),
                ),
            )
            if (delDia.isNotEmpty()) {
                Text("Hora por hora", color = TextoSec, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    delDia.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(h.hora.substring(11, 16), color = TextoSec, fontSize = 12.sp)
                            IconoTiempo(h.codigo, h.hora.substring(11, 13).toInt() in 6..17, 26)
                            Text("${h.temp.roundToInt()}°", fontSize = 15.sp)
                            Text(if (h.prob > 0) "${h.prob}%" else "", color = Celeste, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(titulo: String, codigo: Int, dia: Boolean, temp: Double, sub: String) {
    Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        IconoTiempo(codigo, dia, 64, animado = true)
        Column(Modifier.padding(start = 12.dp)) {
            Text("${temp.roundToInt()}° · ${texto(codigo)}", fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text(sub, color = Ambar, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Grilla(datos: List<Pair<String, String>>) {
    datos.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { (t, v) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(12.dp)) {
                    Text(t, color = TextoSec, fontSize = 12.sp)
                    Text(v, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/**
 * Punto único para dibujar el ícono del tiempo (Meteocons, MIT). Animado solo donde se luce (el grande de arriba y
 * los encabezados); en las listas, fijo, para no tener decenas de animaciones gastando batería. `tam` en dp.
 */
@Composable
fun IconoTiempo(codigo: Int, esDia: Boolean, tam: Int, animado: Boolean = false) {
    IconoClima(codigo = codigo, esDia = esDia, tam = tam.dp, animado = animado)
}
