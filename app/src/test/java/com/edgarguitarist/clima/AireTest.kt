package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AireTest {
    private val json = javaClass.getResource("/aire_guayaquil.json")!!.readText()

    @Test fun parseaLaRespuestaReal() {
        val a = parsearAire(json)
        assertTrue(a.aqi in 0..500)
        assertTrue(a.pm25 >= 0)
        assertEquals(24, a.horas.size)
        assertEquals(a.horas.size, a.aqiHoras.size)
    }

    @Test fun losNullsSeLeenComoCeroOSeSaltan() {
        val a = parsearAire(
            """{"current":{"time":"2026-01-01T00:00","us_aqi":null,"pm2_5":null},
               "hourly":{"time":["2026-01-01T00:00","2026-01-01T01:00"],"us_aqi":[null,40]}}""",
        )
        assertEquals(0, a.aqi)
        assertEquals(0.0, a.pm25, 0.0)
        assertEquals(listOf("2026-01-01T01:00"), a.horas)
        assertEquals(listOf(40), a.aqiHoras)
    }

    @Test fun limitesDeLosNiveles() {
        val esperado = listOf(
            0 to "Buena", 50 to "Buena", 51 to "Moderada", 100 to "Moderada", 101 to "Dañina para sensibles",
            150 to "Dañina para sensibles", 151 to "Dañina", 200 to "Dañina", 201 to "Muy dañina",
            300 to "Muy dañina", 301 to "Peligrosa",
        )
        esperado.forEach { (aqi, nombre) -> assertEquals("aqi $aqi", nombre, nivelAire(aqi).first) }
        assertTrue(consejoAire(75).startsWith("Bien para salir"))
    }
}
