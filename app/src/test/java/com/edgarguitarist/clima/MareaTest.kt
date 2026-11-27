package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MareaTest {
    // Página real de INOCAR (Guayaquil-Río, 26 a 28-nov-2026), en latin1 como la sirve el sitio.
    private val html = javaClass.getResourceAsStream("/inocar_guayaquil.html")!!.reader(Charsets.ISO_8859_1).readText()
    private val gye = ZoneId.of("America/Guayaquil")

    @Test fun parseaInocar() {
        val e = parsearInocar(html)
        assertEquals(12, e.size)
        assertEquals(ZonedDateTime.of(2026, 11, 26, 3, 1, 0, 0, gye), e[0].hora)
        assertEquals(0.74, e[0].altura, 1e-9)
        assertFalse(e[0].alta)
        assertTrue(e[1].alta)
        assertEquals(4.32, e[1].altura, 1e-9)
        assertTrue(e.zipWithNext().all { (a, b) -> a.alta != b.alta }) // alternan
    }

    @Test fun curvaPasaPorLosExtremosYEsSuave() {
        val e = parsearInocar(html)
        val m = Marea(e, curvaMarea(e), "")
        assertEquals(0.74, nivelEn(m, e[0].hora.plusSeconds(1)) ?: 0.0, .01)
        val medio = e[0].hora.plusSeconds(java.time.Duration.between(e[0].hora, e[1].hora).seconds / 2)
        assertEquals((0.74 + 4.32) / 2, nivelEn(m, medio)!!, .05)
        assertEquals(null, nivelEn(m, e[0].hora.minusHours(1)))
    }

    @Test fun aguaje() {
        assertTrue(esAguaje(ZonedDateTime.of(2026, 11, 27, 12, 0, 0, 0, gye)))  // ~3 días después de luna llena
        assertFalse(esAguaje(ZonedDateTime.of(2026, 12, 2, 12, 0, 0, 0, gye)))  // cuarto menguante: mareas bajas
    }

    @Test fun avisoConLluvia() {
        val e = parsearInocar(html)
        val m = Marea(e, curvaMarea(e), "")
        // aviso usa la hora actual: armo horas de lluvia fuerte alrededor de la próxima pleamar real del fixture si cae en 24 h
        val p = e.firstOrNull { it.alta && it.hora.isAfter(ZonedDateTime.now(gye)) && it.hora.isBefore(ZonedDateTime.now(gye).plusHours(24)) }
            ?: return // el fixture ya pasó: no hay pleamar futura con qué probar
        val h = Hora(p.hora.toLocalDateTime().withMinute(0).toString(), 26.0, 30.0, 80, 3.0, 63, 100)
        assertNotNull(avisoMareaLluvia(m, listOf(h)))
        assertEquals(null, avisoMareaLluvia(m, listOf(h.copy(lluvia = 0.0))))
    }

    @Test fun aplicaSoloCerca() {
        assertTrue(mareaAplica(-2.17, -79.83))  // Durán
        assertFalse(mareaAplica(-0.18, -78.47)) // Quito
    }
}
