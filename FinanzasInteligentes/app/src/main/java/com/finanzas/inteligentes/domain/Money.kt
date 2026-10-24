package com.finanzas.inteligentes.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Todas las cantidades se guardan como centavos (Long) en CAD. Nunca Double. */
object Money {
    const val CURRENCY = "CAD"
    const val MAX_CENTS = 100_000_000_00L // 100 millones

    /** Devuelve centavos o null si es inválido. Acepta "12.34" o "12,34". */
    fun parseToCents(input: String, allowZero: Boolean = false): Long? {
        val t = input.trim().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        val bd = try { BigDecimal(t) } catch (e: NumberFormatException) { return null }
        if (bd.scale() > 2) return null
        if (bd.signum() < 0 || (bd.signum() == 0 && !allowZero)) return null
        val cents = bd.movePointRight(2)
        if (cents > BigDecimal(MAX_CENTS)) return null
        return cents.longValueExact()
    }

    fun toPlain(cents: Long): String =
        BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()

    fun format(cents: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CA"))
        nf.currency = Currency.getInstance(CURRENCY)
        return nf.format(BigDecimal(cents).movePointLeft(2))
    }
}
