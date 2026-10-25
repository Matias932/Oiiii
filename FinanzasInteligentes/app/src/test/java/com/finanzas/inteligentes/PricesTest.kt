package com.finanzas.inteligentes

import com.finanzas.inteligentes.data.TxEntity
import com.finanzas.inteligentes.data.TxType
import com.finanzas.inteligentes.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PricesTest {
    @Test fun receiptTotalIgnoresSubtotalAndTaxes() {
        val t = "SUPER MARCHE\n2026-10-20\nPain 3,50\nSOUS-TOTAL 40,00\nTPS 2,00\nTVQ 3,99\nTOTAL 45,99"
        val d = ReceiptParser.parse(t)
        assertEquals("SUPER MARCHE", d.store)
        assertEquals(LocalDate.of(2026, 10, 20), d.day)
        assertEquals(4_599L, d.totalCents)
    }
    @Test fun receiptWithoutTotal() {
        assertNull(ReceiptParser.parse("Tienda\nsin importes").totalCents)
        assertNull(ReceiptParser.parse("Tienda 2026-13-45").day)
    }
    private fun o(id: Long, cents: Long, qty: String, m: Measure, day: Long) =
        Obs(id, "Leche", "A", cents, BigDecimal(qty), m, day, "manual")

    @Test fun priceHistory() {
        val s = PriceHistory.summary(listOf(
            o(1, 400, "1", Measure.L, 10), o(2, 500, "1", Measure.L, 20), o(3, 300, "1", Measure.L, 5)
        ))!!
        assertEquals(2L, s.latest.id)
        assertEquals(1L, s.previous!!.id)
        assertEquals(3L, s.minObs.id)
        assertEquals(0, s.changePct!!.compareTo(BigDecimal("25.0")))
        assertEquals(3, s.comparable)
    }
    @Test fun incompatibleDimensionsAreExcluded() {
        val s = PriceHistory.summary(listOf(o(1, 400, "1", Measure.L, 10), o(2, 100, "1", Measure.KG, 5)))!!
        assertEquals(1, s.comparable)
        assertNull(s.previous)
        assertNull(s.changePct)
        assertNull(PriceHistory.summary(emptyList()))
    }
    @Test fun weeklySpent() {
        val monday = LocalDate.of(2026, 10, 19)
        fun tx(d: LocalDate, c: Long, cat: String = "Alimentación") =
            TxEntity(0, TxType.EXPENSE.name, c, cat, d.toEpochDay(), "", "", 1, null)
        val txs = listOf(tx(monday, 1000), tx(monday.plusDays(6), 500), tx(monday.minusDays(1), 9999), tx(monday.plusDays(7), 7777), tx(monday, 300, "Ropa"))
        assertEquals(1500L, Finance.weeklySpent(txs, "Alimentación", monday.plusDays(3)))
    }
}
