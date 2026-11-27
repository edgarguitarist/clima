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

    @Test fun lluvia() {
        fun c(vararg mm: Double) = mm.mapIndexed { i, v -> Cuarto("2026-11-25T20:%02d".format(i * 15 % 60), v, 50) }
        assertEquals("Puede llover en las próximas 2 horas", resumenLluvia(c(0.0, 0.0)))
        assertEquals("Lluvia hasta las 20:30", resumenLluvia(c(0.5, 0.2, 0.0, 0.0)))
        assertEquals("Lluvia desde las 20:15", resumenLluvia(c(0.0, 0.3, 0.4)))
        assertEquals("Lluvia durante las próximas 2 horas", resumenLluvia(c(1.0, 1.0)))
    }

    @Test fun satelite() {
        fun px(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
        assertEquals(null, topeMasFrio(IntArray(49) { px(122, 122, 122) }))           // suelo tibio
        assertEquals(-61, topeMasFrio(IntArray(49) { px(255, 0, 0) }))                // tormenta
        assertEquals(-75, topeMasFrio(IntArray(49) { if (it < 10) px(77, 0, 0) else px(120, 120, 120) })) // núcleo gris con anillo rojo
        assertEquals(null, topeMasFrio(IntArray(49) { px(0, 128, 185) }))             // nube media, -24 °C
        assertEquals("Tormenta fuerte encima", textoSatelite(-75))
        assertEquals("Lluvia ligera · 2,0 mm/h", lluviaAhora(0.5)?.replace('.', ','))
        assertEquals(null, lluviaAhora(0.0))
    }

    @Test fun iconos() {
        assertEquals("🌙", icono(0, esDia = false))
        assertEquals("🌧️", icono(80, esDia = false))
        assertEquals("Lluvia", texto(63))
    }
}
