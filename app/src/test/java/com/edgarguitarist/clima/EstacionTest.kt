package com.edgarguitarist.clima

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstacionTest {
    // Respuesta real de aviationweather.gov del 27-nov-2026 a las 14:00: el modelo daba llovizna y no llovía.
    private val segu = """[{"icaoId":"SEGU","name":"Guayaquil/Olmedo Intl, G, EC","lat":-2.158,"lon":-79.884,"obsTime":1795806000,
        "wxString":null,"temp":31,"dewp":26,"clouds":[{"cover":"SCT","base":2300},{"cover":"BKN","base":10000}]}]"""
    private val actual = Actual("2026-11-27T14:00", 31.0, 36.0, 70, 24.0, 0.2, 53, 98, 1008.0, 6.0, 20.0, 15.0, 6.0, 20000.0, true)
    private val cuartos = listOf(Cuarto("2026-11-27T14:00", 0.2, 90), Cuarto("2026-11-27T14:15", 0.3, 90))

    @Test fun eligeLaEstacionCercanaYVigente() {
        val o = elegirMetar(JSONArray(segu), -2.1894, -79.8891, 1795806000L + 600)!!
        val e = leerEstacion(o)
        assertEquals("Guayaquil", e.nombre)
        assertTrue(e.distKm in 3.0..4.5)
        assertEquals(false, e.llueve)
        assertEquals("BKN", e.nubes)
    }

    @Test fun descartaViejaOLejana() {
        assertNull(elegirMetar(JSONArray(segu), -2.1894, -79.8891, 1795806000L + 3 * 3600))
        assertNull(elegirMetar(JSONArray(segu), -2.9, -79.0, 1795806000L)) // Cuenca
    }

    @Test fun laEstacionSinLluviaCorrigeLaLloviznaDelModelo() {
        val e = leerEstacion(elegirMetar(JSONArray(segu), -2.1894, -79.8891, 1795806000L)!!)
        val (a, c, modelo) = corregir(actual, cuartos, e)
        assertEquals(2, a.codigo)
        assertEquals(0.0, a.lluvia, 0.0)
        assertEquals(0.0, c[0].lluvia, 0.0)
        assertEquals(0.3, c[1].lluvia, 0.0) // el pronóstico de más adelante no se toca
        assertEquals(53, modelo)
        assertEquals(31.0, a.temp, 0.0)
        assertEquals(36.0 + 0.0, a.sensacion, 0.0)
        assertEquals(75, a.humedad) // 31° con rocío de 26°
        assertTrue(textoEstacion(e).contains("31° medidos"))
    }

    @Test fun lluviaFuerteDelModeloNoSeToca() {
        val e = Estacion("Guayaquil", 0, 4.0, false, "", "BKN")
        assertNull(corregir(actual.copy(lluvia = 2.0, codigo = 63), cuartos, e).third)
    }

    @Test fun lluviaEnLaEstacion() {
        val e = Estacion("Guayaquil", 0, 4.0, true, "-DZ", "OVC")
        val (a, _, modelo) = corregir(actual.copy(lluvia = 0.0, codigo = 3), cuartos, e)
        assertEquals(51, a.codigo)
        assertEquals(3, modelo)
        assertEquals(false, leerEstacion(org.json.JSONObject("""{"name":"X","wxString":"VCSH"}""")).llueve)
        assertEquals(true, leerEstacion(org.json.JSONObject("""{"name":"X","wxString":"-SHRA BR"}""")).llueve)
    }
}

class TitularDesmentidoTest {
    @Test fun suavizaCuandoLaEstacionDesmiente() {
        val c = listOf(Cuarto("2026-11-27T14:00", 0.0, 90), Cuarto("2026-11-27T14:15", 0.3, 90))
        assertTrue(titular(c, null, desmentido = true).startsWith("🌂 Ahora no llueve; el modelo da lluvia desde las 14:15"))
        assertTrue(titular(c, null).startsWith("☂️"))
    }
}

class CieloTest {
    private val actual = Actual("2026-11-27T14:00", 31.0, 36.0, 70, 24.0, 0.3, 61, 98, 1008.0, 6.0, 20.0, 15.0, 6.0, 20000.0, true)
    private val cuartos = listOf(Cuarto("2026-11-27T14:00", 0.3, 90))

    @Test fun cobertura() {
        val gris = { v: Int -> (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertEquals(0.4, coberturaVisible(IntArray(10) { gris(if (it < 4) 160 else 80) }), 1e-9)
    }

    @Test fun despejadoSobreTiDesmienteHastaLluviaModerada() {
        val (a, c, modelo) = corregir(actual.copy(lluvia = 1.0, codigo = 63), cuartos, null, .1)
        assertEquals(0, a.codigo); assertEquals(0.0, a.lluvia, 0.0); assertEquals(0.0, c[0].lluvia, 0.0); assertEquals(63, modelo)
    }

    @Test fun nubladoSinEstacionNoSeToca() {
        assertNull(corregir(actual, cuartos, null, .9).third)
    }

    @Test fun sinLluviaElCieloVistoMandaSobreElDelModelo() {
        val (a, _, modelo) = corregir(actual.copy(lluvia = 0.0, codigo = 3), cuartos, null, .2)
        assertEquals(1, a.codigo); assertEquals(3, modelo)
    }

    @Test fun lluviaEnElAeropuertoPeroDespejadoSobreTi() {
        val e = Estacion("Guayaquil", 0, 4.0, true, "-RA", "BKN")
        assertEquals(0, corregir(actual.copy(lluvia = 0.0, codigo = 0), cuartos, e, .05).first.codigo)
    }

    @Test fun soloConSolAlto() {
        assertTrue(solAlto(java.time.Instant.parse("2026-11-27T18:50:00Z"), -79.9)) // 13:50 en Guayaquil
        assertEquals(false, solAlto(java.time.Instant.parse("2026-11-27T12:00:00Z"), -79.9)) // 07:00
        assertEquals(false, solAlto(java.time.Instant.parse("2026-11-28T02:00:00Z"), -79.9)) // 21:00
    }
}
