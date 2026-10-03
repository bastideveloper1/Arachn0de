package com.r0ybt.arachn0de.domain.model

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Optional financial capability of an ACTION leaf, stored in its existing row. */
data class Obligation(val amountMinor: Long, val currencyCode: String) {
    init {
        require(amountMinor > 0) { "El monto debe ser mayor que 0." }
        Money.currency(currencyCode)
    }
}

/** Exact money boundary. No floating point, exchange rates or cross-currency arithmetic. */
object Money {
    fun currency(code: String): Currency {
        require(code.matches(Regex("[A-Z]{3}"))) { "La moneda debe ser un código ISO de tres letras." }
        val currency = try { Currency.getInstance(code) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Moneda no válida.")
        }
        require(currency.defaultFractionDigits >= 0) { "La moneda no tiene unidades menores definidas." }
        return currency
    }
    fun fractionDigits(code: String): Int = currency(code).defaultFractionDigits

    /** Accept locale decimal separator and correctly grouped digits, without symbols/exponents. */
    fun parse(input: String, code: String, locale: Locale): Long {
        val digits = fractionDigits(code)
        val text = input.trim()
        require(text.isNotEmpty() && text.length <= 128) { "Introduce un monto válido." }
        val symbols = DecimalFormatSymbols(locale)
        val parts = text.split(symbols.decimalSeparator)
        require(parts.size <= 2) { "Separador decimal no válido." }
        val groups = parts[0].split(symbols.groupingSeparator)
        require(groups.all { it.isNotEmpty() && it.all { c -> c in '0'..'9' } } &&
            (groups.size == 1 || (groups[0].length in 1..3 && groups.drop(1).all { it.length == 3 }))) { "Introduce un monto numérico con separadores de tu región." }
        if (parts.size == 2) require(parts[1].isNotEmpty() && parts[1].length <= digits && parts[1].all { it in '0'..'9' }) {
            "La moneda $code admite $digits decimales."
        }
        val decimal = groups.joinToString("") + if (parts.size == 2) ".${parts[1]}" else ""
        val value = try { BigDecimal(decimal).movePointRight(digits).longValueExact() } catch (_: ArithmeticException) {
            throw IllegalArgumentException("El monto supera el máximo permitido.")
        }
        require(value > 0) { "El monto debe ser mayor que 0." }
        return value
    }
    fun input(amountMinor: Long, code: String, locale: Locale): String =
        BigDecimal.valueOf(amountMinor, fractionDigits(code)).toPlainString()
            .replace('.', DecimalFormatSymbols(locale).decimalSeparator)

    fun format(obligation: Obligation, locale: Locale): String {
        val currency = currency(obligation.currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = currency.defaultFractionDigits
            maximumFractionDigits = currency.defaultFractionDigits
        }
        return "${format.format(BigDecimal.valueOf(obligation.amountMinor, currency.defaultFractionDigits))} ${currency.currencyCode}"
    }
}
