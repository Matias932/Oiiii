package com.finanzas.inteligentes.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// ---------------- Deudas ----------------
enum class Strategy { AVALANCHE, SNOWBALL }
data class DebtInput(val name: String, val balanceCents: Long, val rateBp: Int, val minPaymentCents: Long)
data class DebtPlan(val months: Int, val totalInterestCents: Long, val payoffOrder: List<String>, val feasible: Boolean)

/**
 * Supuestos: tasa anual fija; interés mensual = saldo × tasa / 12 (redondeo a centavo, mitad hacia arriba);
 * el interés se aplica al inicio del mes y los pagos al final; sin nuevos cargos.
 * Presupuesto mensual = suma de pagos mínimos + pago extra; lo que sobra tras pagar mínimos
 * (incluye los mínimos de deudas ya liquidadas) va a la deuda prioritaria.
 */
object DebtSim {
    const val MAX_MONTHS = 1200
    private const val BLOWUP_CENTS = 10_000_000_000_000L

    fun interest(balance: Long, rateBp: Int): Long =
        BigDecimal(balance).multiply(BigDecimal(rateBp))
            .divide(BigDecimal(120_000), 0, RoundingMode.HALF_UP).toLong()

    fun simulate(debts: List<DebtInput>, extraCents: Long, strategy: Strategy): DebtPlan {
        val bal = debts.map { it.balanceCents }.toMutableList()
        val paid = BooleanArray(debts.size) { bal[it] <= 0 }
        val order = mutableListOf<String>()
        val budget = debts.sumOf { it.minPaymentCents } + extraCents
        var months = 0
        var totalInterest = 0L
        while (bal.indices.any { bal[it] > 0 }) {
            months++
            if (months > MAX_MONTHS) return DebtPlan(MAX_MONTHS, totalInterest, order, false)
            for (i in bal.indices) if (bal[i] > 0) {
                val x = interest(bal[i], debts[i].rateBp)
                bal[i] += x; totalInterest += x
            }
            if (bal.sum() > BLOWUP_CENTS) return DebtPlan(months, totalInterest, order, false)
            var left = budget
            for (i in bal.indices) if (bal[i] > 0) {
                val p = minOf(bal[i], debts[i].minPaymentCents)
                bal[i] -= p; left -= p
            }
            while (left > 0) {
                val open = bal.indices.filter { bal[it] > 0 }
                if (open.isEmpty()) break
                val t = if (strategy == Strategy.AVALANCHE) open.maxByOrNull { debts[it].rateBp }!!
                else open.minByOrNull { bal[it] }!!
                val p = minOf(left, bal[t])
                bal[t] -= p; left -= p
            }
            for (i in bal.indices) if (!paid[i] && bal[i] <= 0) { paid[i] = true; order.add(debts[i].name) }
        }
        return DebtPlan(months, totalInterest, order, true)
    }
}

// ---------------- Suscripciones ----------------
enum class Freq(val label: String) { WEEKLY("Semanal"), MONTHLY("Mensual"), YEARLY("Anual") }

object SubCost {
    private fun div(n: Long, d: Long) = BigDecimal(n).divide(BigDecimal(d), 0, RoundingMode.HALF_UP).toLong()
    fun monthly(amountCents: Long, f: Freq): Long = when (f) {
        Freq.WEEKLY -> div(amountCents * 52, 12)
        Freq.MONTHLY -> amountCents
        Freq.YEARLY -> div(amountCents, 12)
    }
    fun annual(amountCents: Long, f: Freq): Long = when (f) {
        Freq.WEEKLY -> amountCents * 52
        Freq.MONTHLY -> amountCents * 12
        Freq.YEARLY -> amountCents
    }
    /** Nombres repetidos (sin distinguir mayúsculas): posibles duplicados a revisar. */
    fun duplicateNames(names: List<String>): Set<String> =
        names.groupBy { it.trim().lowercase() }.filter { it.value.size > 1 }.keys
}

// ---------------- Impuestos Quebec ----------------
data class TaxResult(val preCents: Long, val gstCents: Long, val qstCents: Long, val totalCents: Long, val exact: Boolean)

/** TPS 5 % y TVQ 9,975 %, ambas sobre el precio sin impuestos, cada una redondeada al centavo. */
object QcTax {
    const val GST_PCT = "5"
    const val QST_PCT = "9,975"
    const val VERIFIED = "2026-10-25"

    private fun roundDiv(n: Long, d: Long) = (n * 2 + d) / (2 * d)
    fun gst(pre: Long) = roundDiv(pre * 5, 100)
    fun qst(pre: Long) = roundDiv(pre * 9975, 100_000)

    fun addTax(pre: Long) = TaxResult(pre, gst(pre), qst(pre), pre + gst(pre) + qst(pre), true)

    fun removeTax(total: Long): TaxResult {
        val est = roundDiv(total * 100_000, 114_975)
        val hit = (est - 2..est + 2).firstOrNull { it >= 0 && it + gst(it) + qst(it) == total }
        if (hit != null) return TaxResult(hit, gst(hit), qst(hit), total, true)
        return TaxResult(est, gst(est), qst(est), total, false)
    }
}

// ---------------- Simulador de inversión ----------------
data class SimResult(val label: String, val returnBp: Int, val contributedCents: Long, val finalNominalCents: Long, val finalRealCents: Long)

/**
 * Proyección hipotética (no una predicción): capitalización mensual, aportación al final de cada mes,
 * tasa neta = rendimiento − comisión anual. Valor real = nominal ÷ (1 + inflación)^años.
 */
object InvestSim {
    private val MC = MathContext(34)

    fun project(initial: Long, monthly: Long, years: Int, returnBp: Int, feeBp: Int, inflationBp: Int, label: String): SimResult {
        val r = BigDecimal(returnBp - feeBp).divide(BigDecimal(120_000), 18, RoundingMode.HALF_UP)
        val growth = BigDecimal.ONE.add(r)
        var bal = BigDecimal(initial)
        repeat(years * 12) { bal = bal.multiply(growth, MC).add(BigDecimal(monthly)) }
        if (bal.signum() < 0) bal = BigDecimal.ZERO
        val nominal = bal.setScale(0, RoundingMode.HALF_UP).toLong()
        val infl = BigDecimal.ONE.add(BigDecimal(inflationBp).movePointLeft(4)).pow(years, MC)
        val real = bal.divide(infl, 0, RoundingMode.HALF_UP).toLong()
        return SimResult(label, returnBp, initial + monthly * years * 12L, nominal, real)
    }

    /** Desfavorable = base − 3 pp, intermedio = base, favorable = base + 3 pp. */
    fun scenarios(initial: Long, monthly: Long, years: Int, baseBp: Int, feeBp: Int, inflationBp: Int) = listOf(
        project(initial, monthly, years, baseBp - 300, feeBp, inflationBp, "Desfavorable"),
        project(initial, monthly, years, baseBp, feeBp, inflationBp, "Intermedio"),
        project(initial, monthly, years, baseBp + 300, feeBp, inflationBp, "Favorable")
    )
}

// ---------------- Precio por unidad ----------------
enum class Dim { MASS, VOLUME, COUNT }
enum class Measure(val label: String, val dim: Dim, val factor: Long, val perLabel: String) {
    G("g", Dim.MASS, 1, "por 100 g"),
    KG("kg", Dim.MASS, 1000, "por 100 g"),
    ML("ml", Dim.VOLUME, 1, "por 100 ml"),
    L("l", Dim.VOLUME, 1000, "por 100 ml"),
    UNIT("unidad", Dim.COUNT, 1, "por unidad")
}

object UnitPrice {
    /** Dólares por 100 g / 100 ml / unidad, con 6 decimales. */
    fun normalized(priceCents: Long, qty: BigDecimal, m: Measure): BigDecimal {
        val q = qty.multiply(BigDecimal(m.factor))
        val div = if (m.dim == Dim.COUNT) q.multiply(BigDecimal(100)) else q
        return BigDecimal(priceCents).divide(div, 6, RoundingMode.HALF_UP)
    }
}
