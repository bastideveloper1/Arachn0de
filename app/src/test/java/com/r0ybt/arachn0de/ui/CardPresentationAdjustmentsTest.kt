package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R
import org.junit.Assert.*
import org.junit.Test

class CardPresentationAdjustmentsTest {
    @Test fun enumerationSpacingIsDisplayOnlyAndPreservesLineFormatting() {
        val original="1.Texto\r\n2.texto\n  a.Texto\nb.texto.\n12.Otro\nÁ.Texto\n3. Ya separado\n\t4.\tTabulado"
        val expected="1. Texto\r\n2. texto\n  a. Texto\nb. texto.\n12. Otro\nÁ. Texto\n3. Ya separado\n\t4.\tTabulado"
        assertEquals(expected,cardEnumerationSpacing(original))
        assertEquals(expected,cardEnumerationSpacing(expected))
        assertTrue(original.startsWith("1.Texto\r\n"))
    }
    @Test fun decimalAddressesAndOtherDotsStayUnchanged() {
        val text="1.25\n2.0.1\na.com\nb.org/ruta\nhttps://a.dev/1.Texto\nwww.ejemplo.cl\na.ejemplo.cl\na.usuario@ejemplo.cl\n1.archivo.txt\nTexto 1.Texto\nA.B.C.\n\nSin enumeración"
        assertEquals(text,cardEnumerationSpacing(text))
    }
    @Test fun actionIconsAreDistinctAndSharedAcrossConversionLabels() {
        assertEquals(R.drawable.lucide_repeat,contextActionIconResource("Recurrencia"))
        assertEquals(R.drawable.lucide_repeat,contextActionIconResource("Recurrencias"))
        assertEquals(R.drawable.lucide_list_checks,contextActionIconResource("Cambiar estado"))
        listOf("Convertir en proyecto","Convertir en Borrador").forEach {
            assertEquals(R.drawable.lucide_arrow_left_right,contextActionIconResource(it))
        }
        assertEquals(R.drawable.conversion_convertir_en,contextActionIconResource("Convertir en..."))
        assertEquals(R.drawable.conversion_tarea,contextActionIconResource("Convertir en tarea"))
        assertEquals(R.drawable.conversion_nota,contextActionIconResource("Convertir en nota"))
        assertEquals(R.drawable.conversion_capa,contextActionIconResource("Convertir en capa"))
        assertNull(contextActionIconResource("Responsables"))
        assertEquals(3,setOf(contextActionIconResource("Recurrencia"),contextActionIconResource("Cambiar estado"),contextActionIconResource("Convertir en...")).size)
    }
}
