package com.edgarguitarist.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FondoTest {
    private fun a(codigo: Int = 0, lluvia: Double = 0.0, nubes: Int = 0, viento: Double = 0.0, dia: Boolean = true) =
        Actual("", 25.0, 25.0, 50, 15.0, lluvia, codigo, nubes, 1010.0, viento, 0.0, 0.0, 0.0, 10000.0, dia)

    @Test fun nuloNoDibujaNada() {
        val e = escena(null)
        assertEquals(0, e.gotas); assertFalse(e.nubes); assertFalse(e.despejado)
    }

    @Test fun despejadoDiaYNoche() {
        assertTrue(escena(a()).despejado)
        val n = escena(a(dia = false))
        assertTrue(n.despejado && n.noche)
    }

    @Test fun lluviaEscalaConLaIntensidad() {
        val ll = escena(a(codigo = 61, lluvia = 0.1))
        val fu = escena(a(codigo = 65, lluvia = 3.0))
        assertEquals(0.5, ll.mmh, 1e-9) // 0,1*4 = 0,4 queda en el mínimo de llovizna
        assertEquals(12.0, fu.mmh, 1e-9)
        assertTrue(fu.gotas > ll.gotas && fu.gotas <= MAX_GOTAS && ll.nubes && !ll.despejado)
    }

    @Test fun codigoDeLluviaSinMedidaEsLlovizna() {
        val e = escena(a(codigo = 51))
        assertEquals(0.5, e.mmh, 1e-9); assertTrue(e.gotas > 0)
    }

    @Test fun lluviaMedidaSinCodigo() = assertTrue(escena(a(lluvia = 0.2)).gotas > 0)

    @Test fun tormentaYNiebla() {
        assertTrue(escena(a(codigo = 95, lluvia = 1.0)).tormenta)
        val n = escena(a(codigo = 45))
        assertTrue(n.niebla && n.nubes && n.gotas == 0 && !n.tormenta)
    }

    @Test fun nubesPorCodigoOPorcentaje() {
        assertTrue(escena(a(codigo = 3)).nubes)
        assertTrue(escena(a(nubes = 60)).nubes)
        assertFalse(escena(a(nubes = 50)).nubes)
    }

    @Test fun vientoInclinaConTope() {
        assertEquals(0f, escena(a(codigo = 61, viento = 0.0)).inclinacion, 0f)
        assertEquals(0.6f, escena(a(codigo = 61, viento = 200.0)).inclinacion, 0f)
    }

    @Test fun destelloEsRaroYAcotado() {
        var activos = 0
        for (i in 0 until 6000) { val d = destello(i / 10f); assertTrue(d in 0f..0.12f); if (d > 0f) activos++ }
        assertTrue(activos in 1..300) // de 600 s, menos del 5 % del tiempo
    }
}
