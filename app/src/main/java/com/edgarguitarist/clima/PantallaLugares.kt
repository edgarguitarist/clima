package com.edgarguitarist.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgarguitarist.clima.ui.Azul
import com.edgarguitarist.clima.ui.Celeste
import com.edgarguitarist.clima.ui.Fondo
import com.edgarguitarist.clima.ui.Superficie
import com.edgarguitarist.clima.ui.TextoSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Chips arriba: «Aquí» (GPS) y los lugares guardados; el último abre la hoja para gestionarlos. */
@Composable
fun SelectorLugares(lugares: List<Lugar>, sel: String?, elegir: (String?) -> Unit, gestionar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("📍 Aquí", sel == null) { elegir(null) }
        lugares.forEach { l -> Chip(l.nombre, sel == l.nombre) { elegir(l.nombre) } }
        Chip("＋ Lugares", false, gestionar)
    }
}

@Composable
private fun Chip(texto: String, activo: Boolean, alTocar: () -> Unit) {
    Text(
        texto, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = if (activo) Fondo else Color.White.copy(alpha = .85f),
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (activo) Color.White else Color(0x26FFFFFF))
            .clickable(onClick = alTocar).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Una fila por lugar: cómo está ahora y qué viene en la próxima hora. Responde «¿cómo está Guayaquil antes de salir?». */
data class FilaLugar(val nombre: String, val datos: Pronostico?, val sat: Observacion?)

@Composable
fun TarjetaLugares(filas: List<FilaLugar>, sel: String?, elegir: (String?) -> Unit) {
    if (filas.size < 2) return
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Vidrio)
            .border(1.dp, Borde, RoundedCornerShape(22.dp)).padding(vertical = 8.dp),
    ) {
        Text("🗺️  Tus lugares ahora", color = TextoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        filas.forEach { f ->
            val clave = if (f.nombre == AQUI) null else f.nombre
            Row(
                Modifier.fillMaxWidth().clickable { elegir(clave) }
                    .background(if (clave == sel) Color(0x14FFFFFF) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val a = f.datos?.actual
                Text(if (a != null) icono(a.codigo, a.esDia) else "…", fontSize = 26.sp, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.nombre, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    val estado = when {
                        f.datos == null -> "Cargando…"
                        (f.sat?.temp ?: 0) <= -52 -> "🛰️ Tormenta encima"
                        else -> lluviaAhora(a!!.lluvia) ?: resumenLluvia(f.datos.cuartos.take(4)).replace("2 horas", "hora")
                    }
                    Text(estado, color = if ((f.sat?.temp ?: 0) <= -52) Color(0xFFFCA5A5) else Celeste, fontSize = 12.sp)
                }
                if (a != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${a.temp.roundToInt()}°", fontSize = 22.sp, fontWeight = FontWeight.Light)
                        Text("ST ${a.sensacion.roundToInt()}°", color = Color(0xFFFBBF24), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

const val AQUI = "Aquí"

/** Gestionar lugares: buscar y agregar, activar el aviso de lluvia, quitar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaLugares(lugares: List<Lugar>, cambiar: (List<Lugar>) -> Unit, cerrar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Lugar>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(texto) {
        if (texto.trim().length < 3) { resultados = emptyList(); return@LaunchedEffect }
        delay(400) // espera a que termine de escribir
        buscando = true
        error = ""
        try {
            resultados = withContext(Dispatchers.IO) { Lugares.buscar(texto) }
        } catch (e: Exception) {
            error = mensajeError(e)
        } finally {
            buscando = false
        }
    }
    ModalBottomSheet(onDismissRequest = cerrar, containerColor = Superficie) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Tus lugares", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Activa el aviso para recibir una notificación cuando vaya a llover ahí.", color = TextoSec, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            lugares.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.nombre, fontSize = 16.sp)
                        Text(if (l.avisar) "Aviso de lluvia activado" else l.detalle, color = TextoSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = l.avisar, onCheckedChange = { v -> cambiar(lugares.map { if (it == l) it.copy(avisar = v) else it }) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Azul),
                    )
                    TextButton(onClick = { cambiar(lugares - l) }) { Text("Quitar", color = Color(0xFFFCA5A5)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                texto, { texto = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Agregar: busca una ciudad…", color = TextoSec) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Celeste, unfocusedBorderColor = Borde),
            )
            if (buscando) CircularProgressIndicator(Modifier.padding(12.dp).align(Alignment.CenterHorizontally), color = Color.White)
            if (error.isNotEmpty()) Text(error, color = Color(0xFFFCA5A5), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            if (!buscando && texto.trim().length >= 3 && resultados.isEmpty() && error.isEmpty()) {
                Text("Sin resultados", color = TextoSec, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
            resultados.forEach { r ->
                val ya = lugares.any { it.nombre == r.nombre && Math.abs(it.lat - r.lat) < .05 }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !ya) {
                        cambiar(lugares + r.copy(avisar = false)); texto = ""
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.nombre, fontSize = 16.sp)
                        Text(r.detalle, color = TextoSec, fontSize = 12.sp)
                    }
                    Text(if (ya) "Guardado" else "＋ Agregar", color = if (ya) TextoSec else Celeste, fontSize = 14.sp)
                }
            }
        }
    }
}
