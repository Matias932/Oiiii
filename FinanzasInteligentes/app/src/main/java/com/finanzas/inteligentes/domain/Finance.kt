package com.finanzas.inteligentes.domain

import com.finanzas.inteligentes.data.AccountEntity
import com.finanzas.inteligentes.data.GoalEntity
import com.finanzas.inteligentes.data.TxEntity
import com.finanzas.inteligentes.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Lógica financiera pura y determinista (sin Android). */
object Finance {
    private fun month(t: TxEntity) = YearMonth.from(LocalDate.ofEpochDay(t.epochDay))

    fun accountBalance(acc: AccountEntity, txs: List<TxEntity>): Long {
        var b = acc.openingCents
        for (t in txs) when (TxType.valueOf(t.type)) {
            TxType.INCOME -> if (t.accountId == acc.id) b += t.amountCents
            TxType.EXPENSE -> if (t.accountId == acc.id) b -= t.amountCents
            TxType.TRANSFER -> {
                if (t.accountId == acc.id) b -= t.amountCents
                if (t.toAccountId == acc.id) b += t.amountCents
            }
        }
        return b
    }

    fun totalBalance(accs: List<AccountEntity>, txs: List<TxEntity>): Long =
        accs.sumOf { accountBalance(it, txs) }

    /** Las transferencias nunca cuentan como ingreso ni gasto. */
    fun sumByType(txs: List<TxEntity>, type: TxType, m: YearMonth): Long =
        txs.filter { it.type == type.name && month(it) == m }.sumOf { it.amountCents }

    fun expensesByCategory(txs: List<TxEntity>, m: YearMonth): Map<String, Long> =
        txs.filter { it.type == TxType.EXPENSE.name && month(it) == m }
            .groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amountCents } }

    fun budgetSpent(txs: List<TxEntity>, category: String, m: YearMonth): Long =
        expensesByCategory(txs, m)[category] ?: 0L

    /** Gasto de la categoría en la semana (lunes a domingo) que contiene `today`. */
    fun weeklySpent(txs: List<TxEntity>, category: String, today: LocalDate): Long {
        val start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
        return txs.filter { it.type == TxType.EXPENSE.name && it.category == category && it.epochDay in start..(start + 6) }
            .sumOf { it.amountCents }
    }

    fun percentUsed(spent: Long, limit: Long): Long = if (limit <= 0) 0 else spent * 100 / limit

    /** Aportación mensual necesaria; null si no hay fecha objetivo. Redondea hacia arriba. */
    fun monthlyContributionNeeded(goal: GoalEntity, today: LocalDate): Long? {
        val remaining = goal.targetCents - goal.savedCents
        if (remaining <= 0) return 0L
        val d = goal.targetEpochDay ?: return null
        val months = maxOf(
            1L,
            ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(LocalDate.ofEpochDay(d)))
        )
        return (remaining + months - 1) / months
    }
}
