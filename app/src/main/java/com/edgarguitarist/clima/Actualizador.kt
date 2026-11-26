package com.edgarguitarist.clima

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.edgarguitarist.clima.ui.Ambar
import com.edgarguitarist.clima.ui.Superficie
import com.edgarguitarist.clima.ui.TextoSec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Avisa de las versiones nuevas publicadas en GitHub y baja el APK (el mismo esquema que Lince). */
object Actualizador {
    private const val API = "https://api.github.com/repos/edgarguitarist/clima/releases/latest"
    private const val TAG = "ClimaActualizador"

    data class Nueva(val version: String, val notas: String, val url: String, val tam: Long)

    private fun versionActual(ctx: Context) =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** La versión nueva, o null si no hay o no se pudo consultar. Las builds debug no preguntan. */
    suspend fun buscar(ctx: Context): Nueva? = withContext(Dispatchers.IO) {
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return@withContext null
        runCatching {
            val j = JSONObject(abrir(API).inputStream.bufferedReader().use { it.readText() })
            val version = j.getString("tag_name").removePrefix("v")
            if (comparar(version, versionActual(ctx)) <= 0) {
                File(ctx.cacheDir, "actualizacion").deleteRecursively() // el APK de la actualización ya instalada
                return@runCatching null
            }
            val assets = j.getJSONArray("assets")
            val a = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            Nueva(version, j.optString("body").replace("**", "").replace("`", "").trim(),
                a.getString("browser_download_url"), a.optLong("size"))
        }.onFailure { Log.w(TAG, "No se pudo consultar la versión nueva", it) }.getOrNull()
    }

    /** Android no deja actualizar encima si la firma cambió; el APK sin firma legible se deja al instalador. */
    fun mismaFirma(ctx: Context, f: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        fun firmas(i: PackageInfo?) = i?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val nuevas = firmas(pm.getPackageArchiveInfo(f.path, flags)) ?: return@runCatching true
        val actuales = firmas(pm.getPackageInfo(ctx.packageName, flags)) ?: return@runCatching true
        nuevas == actuales
    }.getOrDefault(true)

    /** "Ahora no": no vuelve a preguntar por esa versión hasta el día siguiente. */
    fun omitir(ctx: Context, n: Nueva) = prefs(ctx).edit()
        .putString("omitida", n.version).putLong("omitida_en", System.currentTimeMillis()).apply()

    fun toca(ctx: Context, n: Nueva): Boolean {
        val p = prefs(ctx)
        return p.getString("omitida", null) != n.version ||
            System.currentTimeMillis() - p.getLong("omitida_en", 0) > 24 * 3600_000L
    }

    suspend fun bajar(ctx: Context, n: Nueva, progreso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "actualizacion")
        val f = File(dir, "Clima-${n.version}.apk")
        if (n.tam > 0 && f.length() == n.tam) return@withContext f // ya bajado: se canceló el instalador
        dir.deleteRecursively()
        dir.mkdirs()
        val c = abrir(n.url)
        val total = c.contentLengthLong.takeIf { it > 0 } ?: n.tam
        c.inputStream.use { i ->
            f.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var leidos = 0L
                while (true) {
                    ensureActive()
                    val r = i.read(buf)
                    if (r < 0) break
                    o.write(buf, 0, r)
                    leidos += r
                    if (total > 0) progreso(leidos.toFloat() / total)
                }
            }
        }
        if (n.tam > 0 && f.length() != n.tam) {
            f.delete()
            error("la descarga quedó incompleta")
        }
        f
    }

    /** Abre el instalador de Android. */
    fun instalar(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 0.1.10 > 0.1.9: compara número a número, no como texto. */
    internal fun comparar(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("actualizador", Context.MODE_PRIVATE)

    private fun abrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Clima") // la API de GitHub rechaza pedidos sin User-Agent
            connectTimeout = 15_000
            readTimeout = 30_000
            if (responseCode != 200) error("GitHub respondió $responseCode")
        }
}

/** Al abrir la app: si hay una versión nueva en GitHub, pregunta si se actualiza y baja el APK. */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    var nueva by remember { mutableStateOf<Actualizador.Nueva?>(null) }
    LaunchedEffect(Unit) { nueva = Actualizador.buscar(ctx)?.takeIf { Actualizador.toca(ctx, it) } }
    val n = nueva ?: return
    val cerrar = { Actualizador.omitir(ctx, n); nueva = null }
    var progreso by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var esperandoPermiso by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val f = esperandoPermiso ?: return@rememberLauncherForActivityResult
        esperandoPermiso = null
        if (ctx.packageManager.canRequestPackageInstalls()) Actualizador.instalar(ctx, f)
        else error = "Sin el permiso «Instalar apps desconocidas» para Clima, Android no deja instalar la actualización."
    }
    val instalar: (File) -> Unit = { f ->
        if (!Actualizador.mismaFirma(ctx, f)) {
            error = "Esta versión está firmada con otra llave que la instalada. Desinstala Clima una vez e instala el APK nuevo."
        } else if (!ctx.packageManager.canRequestPackageInstalls()) {
            esperandoPermiso = f
            permiso.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
        } else Actualizador.instalar(ctx, f)
    }

    AlertDialog(
        onDismissRequest = { if (progreso == null) cerrar() },
        containerColor = Superficie,
        title = { Text("Clima ${n.version} disponible", color = Color.White) },
        text = {
            Column {
                Text(n.notas.ifBlank { "Hay una versión nueva de Clima." }, color = TextoSec, fontSize = 14.sp)
                progreso?.let { p ->
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth(), color = Ambar)
                    Text("Bajando… ${(p * 100).toInt()} %", color = TextoSec, fontSize = 12.sp)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = progreso == null, onClick = {
                scope.launch {
                    progreso = 0f
                    error = null
                    try {
                        instalar(Actualizador.bajar(ctx, n) { progreso = it })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "No se pudo bajar: ${e.message ?: e}"
                    } finally {
                        progreso = null
                    }
                }
            }) {
                val mb = if (n.tam > 0) " (%.1f MB)".format(n.tam / 1048576.0) else ""
                Text(if (progreso == null) "Actualizar$mb" else "Bajando…", color = Ambar)
            }
        },
        dismissButton = {
            if (progreso == null) TextButton(onClick = cerrar) { Text("Ahora no", color = TextoSec) }
        },
    )
}
