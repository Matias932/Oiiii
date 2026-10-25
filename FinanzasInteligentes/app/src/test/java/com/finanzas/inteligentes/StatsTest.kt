package com.finanzas.inteligentes

import com.finanzas.inteligentes.data.TxEntity
import com.finanzas.inteligentes.data.TxType
import com.finanzas.inteligentes.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {
    private fun tx(type: TxType, c: Long, y: Int, m: Int) =
        TxEntity(0, type.name, c, "x", LocalDate.of(y, m, 5).toEpochDay(), "", "", 1, if (type == TxType.TRANSFER) 2 else null)

    @Test fun seriesAndAverages() {
        val txs = listOf(
            tx(TxType.INCOME, 100_000, 2026, 9), tx(TxType.EXPENSE, 40_000, 2026, 9),
            tx(TxType.INCOME, 100_000, 2026, 10), tx(TxType.EXPENSE, 80_000, 2026, 10),
            tx(TxType.TRANSFER, 50_000, 2026, 10)
        )
        val s = Stats.series(txs, YearMonth.of(2026, 10), 3)
        assertEquals(3, s.size)
        assertEquals(YearMonth.of(2026, 8), s[0].month)
        assertFalse(s[0].hasData)
        assertEquals(60_000L, s[1].netCents)
        assertEquals(20_000L, s[2].netCents) // la transferencia no cuenta
        assertEquals(40_000L, Stats.averageNet(s))
        assertEquals(60_000L, Stats.averageExpense(s))
        assertNull(Stats.averageNet(listOf(s[0])))
    }

    @Test fun decision() {
        val r = Decision.analyze(100_000, 30_000, 120_000, 10_000, 12)
        assertEquals(-20_000L, r.availableAfterCents)
        assertEquals(20_000L, r.monthlyNetAfterCents)
        assertEquals(240_000L, r.totalCostCents)
        assertNull(Decision.analyze(0, null, 0, 0, 1).monthlyNetAfterCents)
    }
}
