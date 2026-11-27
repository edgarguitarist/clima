package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
}
