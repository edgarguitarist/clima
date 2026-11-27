package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertaTest {
    private fun q(h: String, mm: Double, p: Int = 80) = Cuarto("2026-11-27T$h", mm, p)
    private val seco = listOf(q("15:00", 0.0), q("15:15", 0.0), q("15:30", 0.0), q("15:45", 0.0), q("16:00", 2.0))

    @Test fun sinLluviaNiTormenta() = assertNull(debeAvisar("Durán", seco, -40))

    @Test fun lluviaMasAdelante() {
        val c = listOf(q("15:00", 0.0), q("15:15", 0.0), q("15:30", 0.3), q("15:45", 0.0))
        assertEquals("Lluvia en Durán desde las 15:30 (≈ 1,2 mm/h)", debeAvisar("Durán", c, null)?.texto)
    }

    @Test fun yaLloviendo() =
        assertEquals("Está lloviendo en Durán", debeAvisar("Durán", listOf(q("15:00", 0.5)) + seco, null)?.texto)

    @Test fun probBajaOMuyPocoNoCuenta() {
        assertNull(debeAvisar("Durán", listOf(q("15:00", 1.0, 40), q("15:15", 0.1)), null))
    }

    @Test fun tormentaPorSatelite() {
        assertEquals("Tormenta sobre Durán (satélite)", debeAvisar("Durán", seco, -52)?.texto)
        assertNotNull(debeAvisar("Durán", emptyList(), -70))
    }

    private val gye = ZoneId.of("America/Guayaquil")
    private val ahora = ZonedDateTime.of(2026, 11, 27, 19, 0, 0, 0, gye)
    private fun alta(h: Int, m: Int) = Extremo(ZonedDateTime.of(2026, 11, 27, h, m, 0, 0, gye), 4.41, true)
    private fun hr(h: String, mm: Double, p: Int = 80) = Hora("2026-11-27T$h", 25.0, 27.0, p, mm, 61, 90)

    @Test fun pleamarConLluvia() {
        val a = avisoPleamar(listOf(alta(21, 36)), listOf(hr("22:00", 1.5)), ahora, false)
        assertEquals("🌊 Marea alta y lluvia", a?.titulo)
        assertEquals("Pleamar de 4,41 m a las 21:36 con lluvia fuerte prevista: riesgo de inundación en zonas bajas", a?.texto)
        assertEquals(true, avisoPleamar(listOf(alta(21, 36)), listOf(hr("22:00", 1.5)), ahora, true)?.texto?.endsWith(" · aguaje"))
    }

    @Test fun pleamarSinAvisoSiNoCumple() {
        val e = listOf(alta(21, 36))
        assertNull(avisoPleamar(e, listOf(hr("22:00", 0.5)), ahora, false))          // poca lluvia
        assertNull(avisoPleamar(e, listOf(hr("22:00", 2.0, 40)), ahora, false))      // prob baja
        assertNull(avisoPleamar(e, listOf(hr("19:00", 2.0)), ahora, false))          // lluvia a más de 2 h
        assertNull(avisoPleamar(listOf(alta(23, 0)), listOf(hr("23:00", 2.0)), ahora, false)) // pleamar a más de 3 h
        assertNull(avisoPleamar(listOf(alta(18, 0)), listOf(hr("18:00", 2.0)), ahora, false)) // ya pasó
    }
}
