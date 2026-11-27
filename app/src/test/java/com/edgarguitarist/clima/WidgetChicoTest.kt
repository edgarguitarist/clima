package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WidgetChicoTest {
    private val ahora = LocalDateTime.of(2026, 11, 27, 15, 0)
    private fun q(h: String, mm: Double) = Cuarto("2026-11-27T$h", mm, 50)

    @Test fun llueveAhoraSinFin() =
        assertEquals("Llueve ahora", lluviaCorta(listOf(q("15:00", 1.0), q("15:15", 0.5)), ahora))

    @Test fun paraEn() =
        assertEquals("Para en 30 min", lluviaCorta(listOf(q("15:00", 1.0), q("15:15", 0.2), q("15:30", 0.0)), ahora))

    @Test fun lluviaEnSiguienteCuarto() =
        assertEquals("Lluvia en 15 min", lluviaCorta(listOf(q("15:00", 0.0), q("15:15", 0.3)), ahora))

    @Test fun minutosRedondeadosA5() =
        // 15:30 visto desde 15:13 son 17 min -> 15
        assertEquals("Lluvia en 15 min", lluviaCorta(listOf(q("15:00", 0.0), q("15:30", 0.3)), ahora.plusMinutes(13)))

    @Test fun cuartoPasadoSeIgnora() =
        assertEquals("Sin lluvia 2 h", lluviaCorta(listOf(q("14:30", 2.0), q("15:00", 0.0)), ahora))

    @Test fun bajoUmbralNoEsLluvia() =
        assertEquals("Sin lluvia 2 h", lluviaCorta(listOf(q("15:00", 0.09), q("15:15", 0.0)), ahora))

    @Test fun sinDatos() = assertEquals("Sin datos", lluviaCorta(emptyList(), ahora))
}
