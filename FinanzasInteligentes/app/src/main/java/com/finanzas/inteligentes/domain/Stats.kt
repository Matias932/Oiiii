package com.finanzas.inteligentes.domain

import com.finanzas.inteligentes.data.TxEntity
import com.finanzas.inteligentes.data.TxType
import java.time.YearMonth

data class MonthStat(val month: YearMonth, val incomeCents: Long, val expenseCents: Long) {
    val netCents: Long get() = incomeCents - expenseCents
    val hasData: Boolean get() = incomeCents > 0 || expenseCents > 0
}

object Stats {
    /** Últimos n meses terminando en `end` (incluido), en orden cronológico. Transferencias excluidas. */
    fun series(txs: List<TxEntity>, end: YearMonth, n: Int): List<MonthStat> =
        (n - 1 downTo 0).map {
            val m = end.minusMonths(it.toLong())
            MonthStat(m, Finance.sumByType(txs, TxType.INCOME, m), Finance.sumByType(txs, TxType.EXPENSE, m))
        }

    /** Promedio simple de los meses con datos; null si no hay ninguno. */
    fun averageNet(stats: List<MonthStat>): Long? =
        stats.filter { it.hasData }.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.netCents } / l.size }

    fun averageExpense(stats: List<MonthStat>): Long? =
        stats.filter { it.hasData }.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.expenseCents } / l.size }
}

data class DecisionResult(val availableAfterCents: Long, val monthlyNetAfterCents: Long?, val totalCostCents: Long)

object Decision {
    fun analyze(availableCents: Long, avgNetCents: Long?, purchaseCents: Long, monthlyCostCents: Long, months: Int) =
        DecisionResult(
            availableAfterCents = availableCents - purchaseCents,
            monthlyNetAfterCents = avgNetCents?.minus(monthlyCostCents),
            totalCostCents = purchaseCents + monthlyCostCents * months
        )
}
