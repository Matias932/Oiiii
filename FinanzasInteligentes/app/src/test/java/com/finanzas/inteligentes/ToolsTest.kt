package com.finanzas.inteligentes

import com.finanzas.inteligentes.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ToolsTest {
    @Test fun singleDebtPayoff() {
        val p = DebtSim.simulate(listOf(DebtInput("A", 100_000, 1200, 10_000)), 0, Strategy.AVALANCHE)
        assertTrue(p.feasible)
        assertEquals(11, p.months)
        assertTrue(p.totalInterestCents in 5_500..6_500)
    }
    @Test fun minimumBelowInterestIsInfeasible() {
        val p = DebtSim.simulate(listOf(DebtInput("A", 1_000_000, 2400, 10_000)), 0, Strategy.SNOWBALL)
        assertFalse(p.feasible)
    }
    @Test fun avalancheNotWorseHere() {
        val d = listOf(DebtInput("Pequeña", 50_000, 500, 2_500), DebtInput("Grande", 200_000, 2400, 5_000))
        val a = DebtSim.simulate(d, 10_000, Strategy.AVALANCHE)
        val s = DebtSim.simulate(d, 10_000, Strategy.SNOWBALL)
        assertTrue(a.feasible && s.feasible)
        assertTrue(a.totalInterestCents <= s.totalInterestCents)
        assertEquals(listOf("Grande", "Pequeña").toSet(), a.payoffOrder.toSet())
    }
    @Test fun subscriptionCosts() {
        assertEquals(1_000L, SubCost.monthly(12_000, Freq.YEARLY))
        assertEquals(4_333L, SubCost.monthly(1_000, Freq.WEEKLY))
        assertEquals(52_000L, SubCost.annual(1_000, Freq.WEEKLY))
        assertEquals(12_000L, SubCost.annual(1_000, Freq.MONTHLY))
        assertEquals(setOf("netflix"), SubCost.duplicateNames(listOf("Netflix", " netflix", "Spotify")))
    }
    @Test fun quebecTax() {
        val r = QcTax.addTax(10_000)
        assertEquals(500L, r.gstCents); assertEquals(998L, r.qstCents); assertEquals(11_498L, r.totalCents)
        assertEquals(114_975L, QcTax.addTax(100_000).totalCents)
        val back = QcTax.removeTax(11_498)
        assertEquals(10_000L, back.preCents); assertTrue(back.exact)
    }
    @Test fun investmentScenarios() {
        val s = InvestSim.project(100_000, 0, 1, 1200, 0, 0, "x")
        assertEquals(112_683L, s.finalNominalCents)
        assertEquals(100_000L, s.contributedCents)
        val neg = InvestSim.project(100_000, 0, 1, -1000, 0, 0, "n")
        assertTrue(neg.finalNominalCents < 100_000)
        val sc = InvestSim.scenarios(100_000, 5_000, 10, 500, 100, 200)
        assertTrue(sc[0].finalNominalCents < sc[1].finalNominalCents && sc[1].finalNominalCents < sc[2].finalNominalCents)
        assertTrue(sc[1].finalRealCents < sc[1].finalNominalCents)
    }
    @Test fun unitPrices() {
        val a = UnitPrice.normalized(200, BigDecimal("500"), Measure.G)
        assertEquals(0, a.compareTo(BigDecimal("0.4")))
        val b = UnitPrice.normalized(300, BigDecimal("1"), Measure.KG)
        assertEquals(0, b.compareTo(BigDecimal("0.3")))
        assertEquals(0, UnitPrice.normalized(600, BigDecimal("12"), Measure.UNIT).compareTo(BigDecimal("0.5")))
    }
    @Test fun percentParsing() {
        assertEquals(1999, Money.parsePercentToBp("19.99"))
        assertEquals(-250, Money.parsePercentToBp("-2,5", -50, 50))
        assertNull(Money.parsePercentToBp("101"))
        assertNull(Money.parsePercentToBp("1.234"))
    }
}
