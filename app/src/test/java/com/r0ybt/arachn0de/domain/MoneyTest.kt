package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class MoneyTest {
    private val chile = Locale.forLanguageTag("es-CL")
    private fun invalid(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun minorUnitsAndLocaleFormattingAreExactAcrossCurrencies() {
        assertEquals(50000L, Money.parse("50.000", "CLP", chile))
        assertEquals(1050L, Money.parse("10.50", "USD", Locale.US))
        assertEquals(1050L, Money.parse("10,50", "EUR", chile))
        assertEquals(1234L, Money.parse("1.234", "KWD", Locale.US))
        assertEquals(0, Money.fractionDigits("CLP")); assertEquals(3, Money.fractionDigits("KWD"))
        assertEquals("10.50", Money.input(1050, "USD", Locale.US))
        assertEquals("10,50", Money.input(1050, "EUR", chile))
        assertEquals("$10.50 USD", Money.format(Obligation(1050,"USD"), Locale.US))
        assertTrue(Money.format(Obligation(50000,"CLP"), chile).contains("50.000"))
        assertEquals("$92,233,720,368,547,758.07 USD", Money.format(Obligation(Long.MAX_VALUE,"USD"), Locale.US))
        assertEquals(Long.MAX_VALUE, Money.parse("9223372036854775807", "CLP", Locale.US))
    }
    @Test fun invalidAmountsPrecisionCurrencyAndOverflowAreRejected() {
        for (text in listOf("", "0", "-1", "NaN", "Infinity", "1e3", "1.001", "1,00", "9223372036854775808")) invalid { Money.parse(text,"USD",Locale.US) }
        invalid { Money.parse("1,0","CLP",chile) }
        invalid { Money.parse("92233720368547758.08","USD",Locale.US) }
        for (code in listOf("usd","ABC","XXX","US","")) invalid { Obligation(1,code) }
        invalid { Obligation(0,"CLP") }; invalid { Obligation(-1,"CLP") }
    }
}
