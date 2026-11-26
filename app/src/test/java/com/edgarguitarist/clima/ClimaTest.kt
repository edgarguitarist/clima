package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimaTest {
    // Respuesta real de Open-Meteo para Guayaquil (25-nov-2026)
    private val json = javaClass.getResource("/guayaquil.json")!!.readText()

    @Test fun parsea() {
        val p = parsear(json)
        assertEquals(8, p.cuartos.size)
        assertEquals(24, p.horas.size)
        assertEquals(10, p.dias.size)
        assertTrue(p.horas.first().hora.take(13) == p.actual.hora.take(13)) // empieza en la hora actual
        assertTrue(p.actual.sensacion != 0.0)
    }

    @Test fun nulosSonCero() {
        val p = parsear(json.replaceFirst(Regex("\"precipitation_probability\":\\[\\d+"), "\"precipitation_probability\":[null"))
        assertEquals(0, p.cuartos[0].prob)
    }

    @Test fun rumbos() {
        assertEquals("N", rumbo(0.0)); assertEquals("N", rumbo(350.0))
        assertEquals("SO", rumbo(225.0)); assertEquals("O", rumbo(-90.0))
    }

    @Test fun versiones() {
        assertTrue(Actualizador.comparar("0.1.10", "0.1.9") > 0)
        assertEquals(0, Actualizador.comparar("0.2", "0.2.0"))
    }

    @Test fun iconos() {
        assertEquals("🌙", icono(0, esDia = false))
        assertEquals("Lluvia", texto(63))
    }
}
