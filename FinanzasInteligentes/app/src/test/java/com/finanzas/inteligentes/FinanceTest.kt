package com.finanzas.inteligentes

import com.finanzas.inteligentes.data.*
import com.finanzas.inteligentes.domain.Finance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceTest {
    private val day = LocalDate.of(2026, 10, 10).toEpochDay()
    private val a = AccountEntity(1, "Corriente", 10_000)
    private val b = AccountEntity(2, "Ahorro", 0)
    private fun tx(type: TxType, c: Long, acc: Long, to: Long? = null, cat: String = "Alimentación", d: Long = day) =
        TxEntity(0, type.name, c, cat, d, "", "", acc, to)

    private val txs = listOf(
        tx(TxType.INCOME, 5_000, 1, cat = "Sueldo"),
        tx(TxType.EXPENSE, 2_000, 1),
        tx(TxType.TRANSFER, 3_000, 1, 2, cat = "Transferencia")
    )

    @Test fun balances() {
        assertEquals(10_000L, Finance.accountBalance(a, txs))
        assertEquals(3_000L, Finance.accountBalance(b, txs))
    }
    @Test fun transferDoesNotChangeTotal() {
        assertEquals(13_000L, Finance.totalBalance(listOf(a, b), txs))
    }
    @Test fun transferIsNotIncomeOrExpense() {
        val m = YearMonth.of(2026, 10)
        assertEquals(5_000L, Finance.sumByType(txs, TxType.INCOME, m))
        assertEquals(2_000L, Finance.sumByType(txs, TxType.EXPENSE, m))
    }
    @Test fun otherMonthExcluded() {
        assertEquals(0L, Finance.sumByType(txs, TxType.EXPENSE, YearMonth.of(2026, 9)))
    }
    @Test fun budgetSpentAndPercent() {
        val spent = Finance.budgetSpent(txs, "Alimentación", YearMonth.of(2026, 10))
        assertEquals(2_000L, spent)
        assertEquals(40L, Finance.percentUsed(spent, 5_000))
        assertEquals(0L, Finance.percentUsed(100, 0))
    }
    @Test fun goalContributionRoundsUp() {
        val today = LocalDate.of(2026, 1, 15)
        val g = GoalEntity(1, "Meta", 100_000, 0, LocalDate.of(2026, 4, 20).toEpochDay())
        assertEquals(33_334L, Finance.monthlyContributionNeeded(g, today))
    }
    @Test fun goalWithoutDateOrAlreadyReached() {
        val today = LocalDate.of(2026, 1, 15)
        assertNull(Finance.monthlyContributionNeeded(GoalEntity(1, "x", 1000, 0, null), today))
        assertEquals(0L, Finance.monthlyContributionNeeded(GoalEntity(1, "x", 1000, 1000, null), today))
    }
}
