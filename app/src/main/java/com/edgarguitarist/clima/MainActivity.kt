package com.edgarguitarist.clima

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.edgarguitarist.clima.ui.Ambar
import com.edgarguitarist.clima.ui.Azul
import com.edgarguitarist.clima.ui.Celeste
import com.edgarguitarist.clima.ui.Fondo
import com.edgarguitarist.clima.ui.Superficie
import com.edgarguitarist.clima.ui.TemaClima
import com.edgarguitarist.clima.ui.TextoSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val GYE_LAT = -2.19 // si no hay permiso de ubicación: Guayaquil
private const val GYE_LON = -79.89

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app siempre es oscura: íconos claros en las barras aunque el sistema esté en modo claro.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { TemaClima { Surface(color = Fondo, contentColor = Color.White) { App() } } }
    }
}

private class Estado {
    var lat by mutableStateOf<Double?>(null)
    var lon by mutableStateOf<Double?>(null)
    var lugar by mutableStateOf("")
    var datos by mutableStateOf<Pronostico?>(null)
    var error by mutableStateOf("")
    var cargando by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val e = remember { Estado() }
    val scope = rememberCoroutineScope()
    var pestana by rememberSaveable { mutableStateOf(0) }
    var permisoResuelto by remember { mutableStateOf(false) }

    suspend fun cargar() {
        if (e.cargando) return
        e.cargando = true
        e.error = ""
        try {
            val conPermiso = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val pos = if (conPermiso) ubicacionActual(ctx) else null
            val lat = pos?.latitude ?: GYE_LAT
            val lon = pos?.longitude ?: GYE_LON
            e.lat = lat
            e.lon = lon
            e.datos = withContext(Dispatchers.IO) { obtenerPronostico(lat, lon) }
            val nombre = nombreLugar(ctx, lat, lon) ?: "Mi ubicación"
            e.lugar = when {
                !conPermiso -> "$nombre (sin permiso de ubicación)"
                pos == null -> "$nombre (sin señal de ubicación)"
                else -> nombre
            }
        } catch (x: Exception) {
            e.error = x.message ?: x.toString()
        } finally {
            e.cargando = false
        }
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permisoResuelto = true
    }
    LaunchedEffect(Unit) {
        pedirPermiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Recarga al volver a la app y cada 10 min mientras está abierta (Open-Meteo cambia cada 15).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(permisoResuelto) {
        if (!permisoResuelto) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                cargar()
                delay(10 * 60_000L)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth().background(Superficie, RoundedCornerShape(12.dp)).padding(4.dp),
        ) {
            listOf("Pronóstico", "Radar y nubes").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f)
                        .background(if (pestana == i) Azul else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { pestana = i }.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
        }
        val lat = e.lat
        val lon = e.lon
        if (pestana == 1 && lat != null && lon != null) {
            Mapa(lat, lon)
        } else {
            PullToRefreshBox(isRefreshing = e.cargando, onRefresh = { scope.launch { cargar() } }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (e.error.isNotEmpty()) {
                        Text("No se pudo cargar: ${e.error}. Desliza hacia abajo para reintentar.", color = Color(0xFFFCA5A5))
                    }
                    val d = e.datos
                    if (d == null) {
                        if (e.error.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        Detalle(d, e.lugar)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    AvisoActualizacion()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Mapa(lat: Double, lon: Double) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(android.graphics.Color.parseColor("#0B1220"))
            // Errores de la página al logcat: adb logcat -s ClimaMapa
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.w("ClimaMapa", "${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
            val html = ctx.assets.open("mapa.html").bufferedReader().use { it.readText() }
                .replace("__LAT__", lat.toString()).replace("__LON__", lon.toString())
            loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        }
    })
}

private fun r(x: Double) = x.roundToInt()
private fun hora(iso: String) = iso.substring(11, 16)
private val formatoDia = DateTimeFormatter.ofPattern("EEE d", Locale("es"))

@Composable
private fun Detalle(p: Pronostico, lugar: String) {
    val a = p.actual
    Text(lugar, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text("Actualizado ${hora(a.hora)} (hora local)", color = TextoSec, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(icono(a.codigo, a.esDia), fontSize = 64.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Text("${r(a.temp)}°", fontSize = 88.sp, fontWeight = FontWeight.ExtraLight, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(texto(a.codigo), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Sensación térmica ${r(a.sensacion)}°", color = Ambar, fontSize = 17.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
    )

    Tarjeta("Próximas 2 horas · cada 15 min") {
        Text(if (p.cuartos.any { it.lluvia > 0 }) "Se espera lluvia" else "Sin lluvia prevista")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            p.cuartos.forEach {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hora(it.hora), color = TextoSec, fontSize = 11.sp)
                    Text("%.1f".format(it.lluvia), fontSize = 15.sp)
                    Text("${it.prob}%", color = Celeste, fontSize = 11.sp)
                }
            }
        }
        Text("mm de lluvia y probabilidad", color = TextoSec, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    val datos = listOf(
        "Humedad" to "${a.humedad}%",
        "Punto de rocío" to "${r(a.rocio)}°",
        "Viento" to "${r(a.viento)} km/h ${rumbo(a.dirViento)}",
        "Ráfagas" to "${r(a.rafagas)} km/h",
        "Nubosidad" to "${a.nubes}%",
        "Lluvia ahora" to "${a.lluvia} mm",
        "Índice UV" to "%.1f · %s".format(a.uv, nivelUV(a.uv)),
        "Presión" to "${r(a.presion)} hPa",
        "Visibilidad" to "%.1f km".format(a.visibilidad / 1000),
        "Sol" to "${hora(p.dias[0].amanecer)} – ${hora(p.dias[0].atardecer)}",
    )
    datos.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { (t, v) ->
                Column(Modifier.weight(1f).background(Superficie, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(t, color = TextoSec, fontSize = 12.sp)
                    Text(v, fontSize = 16.sp)
                }
            }
        }
    }

    Tarjeta("Próximas 24 horas") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            p.horas.forEachIndexed { i, h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i == 0) "Ahora" else hora(h.hora), color = TextoSec, fontSize = 12.sp)
                    Text(icono(h.codigo), fontSize = 22.sp)
                    Text("${r(h.temp)}°", fontSize = 16.sp)
                    Text("ST ${r(h.sensacion)}°", color = Ambar, fontSize = 11.sp)
                    Text("💧${h.prob}%", color = Celeste, fontSize = 11.sp)
                    Text("☁️${h.nubes}%", color = TextoSec, fontSize = 11.sp)
                }
            }
        }
        Text("ST = sensación térmica", color = TextoSec, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }

    Tarjeta("Próximos 10 días") {
        p.dias.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "Hoy" else LocalDate.parse(d.fecha).format(formatoDia), modifier = Modifier.width(64.dp))
                Text(icono(d.codigo), fontSize = 20.sp, modifier = Modifier.width(34.dp))
                Text("💧${d.prob}%", color = Celeste, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                Text("${r(d.min)}° / ${r(d.max)}°", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("ST ${r(d.sensMax)}°", color = Ambar, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
            }
        }
    }
    Text(
        "Datos: Open-Meteo · Radar: RainViewer · Satélite: NASA GIBS",
        color = Color(0xFF475569), fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(Superficie, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(titulo.uppercase(), color = TextoSec, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        contenido()
    }
}
