package com.edgarguitarist.clima

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class IconosTest {
    // Los códigos WMO de CODIGOS (Clima.kt, privado), más uno desconocido
    private val codigos = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77,
        80, 81, 82, 85, 86, 95, 96, 99, -1)

    @Test fun cadaCodigoTieneArchivo() {
        for (c in codigos) for (dia in listOf(true, false)) {
            val f = File("src/main/assets", archivoIcono(c, dia))
            assertTrue("falta ${f.path} (código $c, día=$dia)", f.isFile)
        }
    }
}
