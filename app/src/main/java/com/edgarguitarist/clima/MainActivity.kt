package com.edgarguitarist.clima

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.edgarguitarist.clima.ui.Ambar
import com.edgarguitarist.clima.ui.Celeste
import com.edgarguitarist.clima.ui.Fondo
import com.edgarguitarist.clima.ui.TarjetaAire
import com.edgarguitarist.clima.ui.TemaClima
import com.edgarguitarist.clima.ui.TextoSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val GYE_LAT = -2.19 // si no hay permiso de ubicación: Guayaquil
private const val GYE_LON = -79.89

internal val Vidrio = Color(0x14FFFFFF) // tarjetas translúcidas sobre el degradado
internal val Borde = Color(0x1AFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app siempre es oscura: íconos claros en las barras aunque el sistema esté en modo claro.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Permiso de notificaciones (Android 13+) y arranque del aviso de lluvia en segundo plano.
        val pedirNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { programarAlertas(this) }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else programarAlertas(this)
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
    var etapa by mutableStateOf("")
    var satelite by mutableStateOf<Observacion?>(null)
    // Lugares guardados: su pronóstico y lo que ve el satélite, por nombre. sel = null es «Aquí».
    var lugares by mutableStateOf<List<Lugar>>(emptyList())
    val datosLugar = mutableStateMapOf<String, Pronostico>()
    val satLugar = mutableStateMapOf<String, Observacion?>()
    val aire = mutableStateMapOf<String, Aire>() // por nombre de lugar; «Aquí» va con la clave AQUI
    var sel by mutableStateOf<String?>(null)
    var marea by mutableStateOf<Marea?>(null) // río Guayas: la misma para Guayaquil y Durán
}

private fun cacheLugares(ctx: Context) = ctx.getSharedPreferences("cache_lugares", Context.MODE_PRIVATE)

/** El último pronóstico bajado: se muestra al instante al abrir, mientras llega el nuevo. */
private fun cache(ctx: Context) = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)

private fun leerCache(ctx: Context, e: Estado) {
    val c = cache(ctx)
    val json = c.getString("json", null) ?: return
    runCatching { parsear(json) }.onSuccess {
        e.datos = it
        e.lugar = c.getString("lugar", "") ?: ""
        e.lat = c.getFloat("lat", 0f).toDouble()
        e.lon = c.getFloat("lon", 0f).toDouble()
    }
    e.lugares = Lugares.leer(ctx)
    val cl = cacheLugares(ctx)
    e.lugares.forEach { l -> cl.getString(l.nombre, null)?.let { j -> runCatching { parsear(j) }.onSuccess { e.datosLugar[l.nombre] = it } } }
}

/** Pronóstico y satélite de todos los lugares guardados, en paralelo. Un lugar que falla se queda con su caché. */
private suspend fun cargarLugares(ctx: Context, e: Estado) = coroutineScope {
    e.lugares.map { l ->
        async(Dispatchers.IO) {
            runCatching { descargarPronostico(l.lat, l.lon) }.onSuccess { j ->
                runCatching { parsear(j) }.onSuccess { e.datosLugar[l.nombre] = it; cacheLugares(ctx).edit().putString(l.nombre, j).apply() }
            }
            e.satLugar[l.nombre] = observarSatelite(l.lat, l.lon)
            runCatching { descargarAire(l.lat, l.lon) }.onSuccess { e.aire[l.nombre] = it }
        }
    }.awaitAll()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val e = remember { Estado().also { leerCache(ctx, it) } }
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
            e.etapa = "Buscando tu ubicación…"
            val pos = if (conPermiso) ubicacionActual(ctx) else null
            val lat = pos?.latitude ?: GYE_LAT
            val lon = pos?.longitude ?: GYE_LON
            e.etapa = "Descargando el pronóstico…"
            scope.launch { cargarLugares(ctx, e) }
            scope.launch { withContext(Dispatchers.IO) { descargarMarea() }?.let { e.marea = it } }
            val json = withContext(Dispatchers.IO) { descargarPronostico(lat, lon) }
            e.datos = parsear(json)
            scope.launch { e.satelite = withContext(Dispatchers.IO) { observarSatelite(lat, lon) } }
            scope.launch { withContext(Dispatchers.IO) { runCatching { descargarAire(lat, lon) } }.onSuccess { e.aire[AQUI] = it } }
            e.lat = lat
            e.lon = lon
            // El nombre llega después: el pronóstico ya se ve mientras el Geocoder responde.
            val nombre = nombreLugar(ctx, lat, lon) ?: "Mi ubicación"
            e.lugar = when {
                !conPermiso -> "$nombre · sin permiso de ubicación"
                pos == null -> "$nombre · sin señal de ubicación"
                else -> nombre
            }
            cache(ctx).edit().putString("json", json).putString("lugar", e.lugar)
                .putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
            actualizarWidgets(ctx)
        } catch (x: Exception) {
            e.error = mensajeError(x)
        } finally {
            e.cargando = false
            e.etapa = ""
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

    val lugarSel = e.lugares.find { it.nombre == e.sel }
    val d = if (lugarSel == null) e.datos else e.datosLugar[lugarSel.nombre]
    var hoja by remember { mutableStateOf(false) }
    val colores = fondo(d?.actual)
    val arriba by animateColorAsState(colores.first, tween(800), label = "fondo")
    val medio by animateColorAsState(colores.second, tween(800), label = "fondo2")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(arriba, medio, Fondo)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Pestanas(pestana) { pestana = it }
            SelectorLugares(e.lugares, lugarSel?.nombre, { e.sel = it }) { hoja = true }
            Spacer(Modifier.height(6.dp))
            if (pestana == 1) {
                val la = lugarSel?.lat ?: e.lat ?: GYE_LAT
                val lo = lugarSel?.lon ?: e.lon ?: GYE_LON
                key(la, lo) { Mapa(la, lo) }
            } else {
                PullToRefreshBox(isRefreshing = e.cargando && d != null, onRefresh = { scope.launch { cargar() } }) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        if (d == null) {
                            Cargando(e.etapa, e.error)
                        } else {
                            if (e.error.isNotEmpty()) Aviso("No se pudo actualizar: ${e.error}. Desliza hacia abajo para reintentar.")
                            val filas = listOf(FilaLugar(AQUI, e.datos, e.satelite)) +
                                e.lugares.map { FilaLugar(it.nombre, e.datosLugar[it.nombre], e.satLugar[it.nombre]) }
                            Detalle(
                                d, lugarSel?.nombre ?: e.lugar, if (lugarSel == null) e.satelite else e.satLugar[lugarSel.nombre],
                                e.aire[lugarSel?.nombre ?: AQUI],
                                e.marea?.takeIf { mareaAplica(lugarSel?.lat ?: e.lat ?: GYE_LAT, lugarSel?.lon ?: e.lon ?: GYE_LON) },
                            ) { TarjetaLugares(filas, lugarSel?.nombre) { e.sel = it } }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    if (hoja) HojaLugares(e.lugares, { nuevos ->
        e.lugares = nuevos
        Lugares.guardar(ctx, nuevos)
        programarAlertas(ctx)
        if (e.sel != null && nuevos.none { it.nombre == e.sel }) e.sel = null
        scope.launch { cargarLugares(ctx, e) }
    }) { hoja = false }
    AvisoActualizacion()
}

/** Colores del degradado de fondo según el cielo: azul de día, índigo de noche, gris con lluvia o nubes. */
private fun fondo(a: Actual?): Pair<Color, Color> = when {
    a == null -> Color(0xFF1E3A8A) to Color(0xFF172554)
    a.codigo >= 51 -> if (a.esDia) Color(0xFF334155) to Color(0xFF1E293B) else Color(0xFF1E293B) to Color(0xFF0F172A)
    !a.esDia -> Color(0xFF1E1B4B) to Color(0xFF0F172A)
    a.codigo >= 3 -> Color(0xFF3B5578) to Color(0xFF1E2F4A)
    else -> Color(0xFF2563EB) to Color(0xFF1E3A8A)
}

@Composable
private fun Pestanas(actual: Int, elegir: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
            .background(Color(0x33000000), RoundedCornerShape(50)).padding(4.dp),
    ) {
        listOf("Pronóstico", "Radar y nubes").forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (actual == i) Color.White else Color.Transparent)
                    .clickable { elegir(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (actual == i) Fondo else Color.White.copy(alpha = .8f),
                )
            }
        }
    }
}

@Composable
private fun Cargando(etapa: String, error: String) {
    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (error.isEmpty()) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Text(etapa.ifEmpty { "Cargando…" }, color = TextoSec, modifier = Modifier.padding(top = 16.dp))
        } else {
            Text("☁️", fontSize = 56.sp)
            Text("No se pudo cargar el pronóstico", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("$error.\nDesliza hacia abajo para reintentar.", color = TextoSec, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto, color = Color(0xFFFECACA), fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            .background(Color(0x33EF4444), RoundedCornerShape(12.dp)).padding(10.dp),
    )
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
private fun Detalle(p: Pronostico, lugar: String, sat: Observacion?, aire: Aire?, marea: Marea?, debajo: @Composable () -> Unit = {}) {
    val a = p.actual
    val hoy = p.dias[0]
    // Encabezado
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📍 ${lugar.ifEmpty { "Mi ubicación" }}", fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(icono(a.codigo, a.esDia), fontSize = 76.sp, modifier = Modifier.padding(top = 12.dp))
        Text("${r(a.temp)}°", fontSize = 104.sp, fontWeight = FontWeight.Thin, lineHeight = 104.sp)
        Text(texto(a.codigo), fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Text(
            titular(p.cuartos, sat?.temp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Sensación ${r(a.sensacion)}°", Ambar)
            Chip("↑ ${r(hoy.max)}°  ↓ ${r(hoy.min)}°", Color.White)
        }
        Text(
            "Actualizado a las ${hora(a.hora)}", color = Color.White.copy(alpha = .55f), fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        // La intensidad de los últimos 15 min (modelo) y lo que de verdad ve el satélite encima tuyo.
        val medido = listOfNotNull(
            lluviaAhora(a.lluvia)?.let { "💧 $it" to Celeste },
            sat?.let {
                val h = runCatching { java.time.Instant.parse(it.hora).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5) }.getOrDefault("")
                "🛰️ $h · ${textoSatelite(it.temp)}" to if ((it.temp ?: 0) <= -52) Color(0xFFFCA5A5) else Color.White.copy(alpha = .8f)
            },
        )
        medido.forEach { (t, c) -> Text(t, color = c, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }

    debajo()

    Tarjeta("🌧️  Lluvia · próximas 2 horas") {
        Text(resumenLluvia(p.cuartos), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        BarrasLluvia(p.cuartos)
    }

    marea?.let { TarjetaMarea(it, p.horas) }

    Tarjeta("🕒  Próximas 24 horas") { Horas(p.horas) }

    Tarjeta("📅  Próximos 10 días") { Dias(p.dias) }

    aire?.let { TarjetaAire(it) }

    val celdas = listOf(
        Triple("💧 Humedad", "${a.humedad}%", "Punto de rocío ${r(a.rocio)}°"),
        Triple("🌬️ Viento", "${r(a.viento)} km/h", "Ráfagas ${r(a.rafagas)} km/h"),
        Triple("☀️ Índice UV", "%.1f".format(a.uv), "${nivelUV(a.uv)} · máx. hoy %.0f".format(hoy.uvMax)),
        Triple("☁️ Nubosidad", "${a.nubes}%", if (a.lluvia > 0) "Lloviendo ${a.lluvia} mm" else "Sin lluvia ahora"),
        Triple("🌡️ Presión", "${r(a.presion)} hPa", "Al nivel del mar"),
        Triple("👁️ Visibilidad", "%.0f km".format(a.visibilidad / 1000), if (a.visibilidad < 5000) "Reducida" else "Buena"),
        Triple("🌅 Amanecer", hora(hoy.amanecer), "Atardecer ${hora(hoy.atardecer)}"),
        Triple("☔ Lluvia hoy", "%.1f mm".format(hoy.lluvia), "Probabilidad ${hoy.prob}%"),
    )
    celdas.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { (t, v, sub) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Vidrio)
                        .border(1.dp, Borde, RoundedCornerShape(20.dp)).padding(14.dp),
                ) {
                    Text(t, color = TextoSec, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 4.dp))
                        if (t.endsWith("Viento")) {
                            // La flecha apunta hacia donde va el viento (la dirección dice de dónde viene).
                            Text("↑", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Celeste, modifier = Modifier.padding(start = 8.dp).rotate((a.dirViento + 180).toFloat()))
                            Text(rumbo(a.dirViento), fontSize = 13.sp, color = TextoSec, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                    Text(sub, color = TextoSec, fontSize = 12.sp)
                }
            }
        }
    }
    Text(
        "Pronóstico: Open-Meteo (ECMWF, GFS, ICON…) · Radar: RainViewer · Satélite: NASA GIBS",
        color = Color.White.copy(alpha = .35f), fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    )
}

@Composable
private fun Chip(texto: String, color: Color) {
    Text(
        texto, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Vidrio)
            .border(1.dp, Borde, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Text(titulo, color = TextoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
        contenido()
    }
}

/** Barras de lluvia cada 15 min: el alto son los mm, la intensidad del color la probabilidad. */
@Composable
private fun BarrasLluvia(cuartos: List<Cuarto>) {
    val maximo = maxOf(1.0, cuartos.maxOfOrNull { it.lluvia } ?: 0.0)
    Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        cuartos.forEach { c ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                val alto = (c.lluvia / maximo).toFloat().coerceIn(0.06f, 1f)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(alto).clip(RoundedCornerShape(6.dp))
                        .background(Celeste.copy(alpha = if (c.lluvia >= 0.1) .35f + .65f * c.prob / 100f else .15f)),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        cuartos.forEachIndexed { i, c ->
            Text(
                if (i % 2 == 0) (if (i == 0) "Ahora" else hora(c.hora)) else "", color = TextoSec, fontSize = 11.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Start,
            )
        }
    }
    Text(
        "Máx. %.1f mm en 15 min · prob. hasta %d%%".format(cuartos.maxOfOrNull { it.lluvia } ?: 0.0, cuartos.maxOfOrNull { it.prob } ?: 0),
        color = TextoSec, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

/** Las 24 horas: curva de temperatura y, debajo, la sensación térmica de cada hora. */
@Composable
private fun Horas(horas: List<Hora>) {
    val ancho = 58.dp
    val medidor = rememberTextMeasurer()
    // La curva usa su propio rango (mínimo 4°) para que se note la forma del día aunque varíe poco.
    val min = horas.minOf { it.temp }
    val max = maxOf(horas.maxOf { it.temp }, min + 4)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            horas.forEachIndexed { i, h ->
                Column(Modifier.width(ancho), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i == 0) "Ahora" else hora(h.hora), color = TextoSec, fontSize = 12.sp)
                    Text(icono(h.codigo, esDiaAprox(h.hora)), fontSize = 24.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        Canvas(Modifier.width(ancho * horas.size).height(96.dp)) {
            val paso = size.width / horas.size
            val arriba = 26.dp.toPx()
            val abajo = size.height - 18.dp.toPx()
            fun y(v: Double) = (abajo - (v - min) / (max - min) * (abajo - arriba)).toFloat()
            fun curva(valor: (Hora) -> Double): Path = Path().apply {
                horas.forEachIndexed { i, h ->
                    val x = paso * i + paso / 2
                    if (i == 0) moveTo(x, y(valor(h))) else {
                        val xa = paso * (i - 1) + paso / 2
                        val ya = y(valor(horas[i - 1]))
                        cubicTo((xa + x) / 2, ya, (xa + x) / 2, y(valor(h)), x, y(valor(h)))
                    }
                }
            }
            val temp = curva { it.temp }
            val relleno = Path().apply {
                addPath(temp)
                lineTo(size.width - paso / 2, size.height); lineTo(paso / 2, size.height); close()
            }
            drawPath(relleno, Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.Transparent)))
            drawPath(temp, Color.White, style = Stroke(2.5.dp.toPx()))
            horas.forEachIndexed { i, h ->
                val x = paso * i + paso / 2
                drawCircle(Color.White, 3.dp.toPx(), Offset(x, y(h.temp)))
                val t = medidor.measure("${r(h.temp)}°", TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                drawText(t, topLeft = Offset(x - t.size.width / 2, y(h.temp) - t.size.height - 6.dp.toPx()))
                val s = medidor.measure("ST ${r(h.sensacion)}°", TextStyle(color = Ambar, fontSize = 11.sp))
                drawText(s, topLeft = Offset(x - s.size.width / 2, size.height - s.size.height))
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            horas.forEach { h ->
                Text(
                    if (h.prob > 0) "💧${h.prob}%" else "", color = Celeste, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.width(ancho),
                )
            }
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 2.dp).background(Color.White))
        Text(" Temperatura", color = TextoSec, fontSize = 11.sp)
        Text("   ST", color = Ambar, fontSize = 11.sp)
        Text(" sensación térmica", color = TextoSec, fontSize = 11.sp)
    }
}

// Sin el amanecer de cada hora: de 6 a 18 es de día, suficiente cerca del ecuador para elegir sol o luna.
private fun esDiaAprox(iso: String) = iso.substring(11, 13).toInt() in 6..17

@Composable
private fun Dias(dias: List<Dia>) {
    val min = dias.minOf { it.min }
    val max = dias.maxOf { it.max }
    dias.forEachIndexed { i, d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(if (i == 0) "Hoy" else LocalDate.parse(d.fecha).format(formatoDia).replaceFirstChar { it.uppercase() }, fontSize = 15.sp)
                Text("ST ${r(d.sensMax)}°", color = Ambar, fontSize = 11.sp)
            }
            Text(icono(d.codigo), fontSize = 22.sp, modifier = Modifier.width(34.dp))
            Text(if (d.prob >= 10) "${d.prob}%" else "", color = Celeste, fontSize = 12.sp, modifier = Modifier.width(38.dp))
            Text("${r(d.min)}°", color = TextoSec, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
            Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 10.dp)) {
                val rango = (max - min).coerceAtLeast(1.0)
                val x0 = ((d.min - min) / rango * size.width).toFloat()
                val x1 = ((d.max - min) / rango * size.width).toFloat()
                val radio = CornerRadius(size.height / 2)
                drawRoundRect(Color(0x26FFFFFF), cornerRadius = radio)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Celeste, Ambar), startX = 0f, endX = size.width),
                    topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(size.height), size.height), cornerRadius = radio,
                )
            }
            Text("${r(d.max)}°", fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
        }
    }
}
