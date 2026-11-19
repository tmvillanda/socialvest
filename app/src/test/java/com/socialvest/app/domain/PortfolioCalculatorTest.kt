package com.socialvest.app.domain

import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * JVM tests for the money math. These exist because a demo that shows
 * `Rp105.000,00000001` or a negative holding is worse than a missing feature.
 *
 * Method names avoid backticks: dots are illegal in JVM method names.
 */
class PortfolioCalculatorTest {

    // ---------- helpers ----------

    private fun rp(value: String): Long = requireNotNull(Money.parseToCents(value)) {
        "Cannot parse rupiah value: $value"
    }

    private fun qty(value: String): Long = requireNotNull(Quantity.parse(value)) {
        "Cannot parse quantity: $value"
    }

    private fun buy(
        id: String = "buy",
        quantity: String,
        price: String,
        fee: String = "0",
        date: Long = 0L
    ) = PortfolioCalculator.Entry(
        id = id,
        type = TransactionType.BUY,
        quantity = qty(quantity),
        pricePerUnit = rp(price),
        fee = rp(fee),
        dateMillis = date
    )

    private fun sell(
        id: String = "sell",
        quantity: String,
        price: String,
        fee: String = "0",
        date: Long = 0L
    ) = PortfolioCalculator.Entry(
        id = id,
        type = TransactionType.SELL,
        quantity = qty(quantity),
        pricePerUnit = rp(price),
        fee = rp(fee),
        dateMillis = date
    )

    private fun holdingOf(vararg entries: PortfolioCalculator.Entry): PortfolioCalculator.Holding {
        val result = PortfolioCalculator.replay(entries.toList())
        assertTrue("Expected success but was $result", result is PortfolioCalculator.ReplayResult.Success)
        return (result as PortfolioCalculator.ReplayResult.Success).holding
    }

    // ---------- 1. single buy ----------

    @Test
    fun buyTenUnitsSetsQuantityCostAndAverage() {
        val holding = holdingOf(buy(quantity = "10", price = "10.000"))

        assertEquals(qty("10"), holding.quantity)
        assertEquals(rp("100.000"), holding.totalCost)
        assertEquals(rp("10.000"), holding.averageBuyPrice)
        assertEquals(0L, holding.realizedPl)
        // Price unchanged -> no unrealised gain.
        assertEquals(0L, PortfolioCalculator.unrealizedPl(holding, rp("10.000")))
    }

    @Test
    fun buyFeeIsIncludedInCostBasis() {
        val holding = holdingOf(buy(quantity = "10", price = "10.000", fee = "1.500"))

        assertEquals(rp("101.500"), holding.totalCost)
        assertEquals(rp("10.150"), holding.averageBuyPrice)
    }

    // ---------- 2. weighted average ----------

    @Test
    fun buyingAtTwoPricesProducesWeightedAverage() {
        val holding = holdingOf(
            buy(id = "b1", quantity = "10", price = "10.000"),
            buy(id = "b2", quantity = "10", price = "12.000")
        )

        assertEquals(qty("20"), holding.quantity)
        assertEquals(rp("220.000"), holding.totalCost)
        assertEquals(rp("11.000"), holding.averageBuyPrice)
    }

    // ---------- 3. partial sell ----------

    @Test
    fun sellingHalfRemovesHalfOfTheCost() {
        val holding = holdingOf(
            buy(id = "b1", quantity = "10", price = "10.000"),
            buy(id = "b2", quantity = "10", price = "12.000"),
            sell(id = "s1", quantity = "5", price = "12.000")
        )

        assertEquals(qty("15"), holding.quantity)
        // 220.000 - 55.000
        assertEquals(rp("165.000"), holding.totalCost)
        // 5 x 12.000 - 55.000
        assertEquals(rp("5.000"), holding.realizedPl)
    }

    @Test
    fun sellingEverythingLeavesZeroCost() {
        val holding = holdingOf(
            buy(id = "b1", quantity = "3", price = "33.333"),
            sell(id = "s1", quantity = "1", price = "35.000"),
            sell(id = "s2", quantity = "2", price = "35.000")
        )

        assertEquals(0L, holding.quantity)
        assertEquals(0L, holding.totalCost)
        assertEquals(0L, holding.averageBuyPrice)
        // Realised = revenue minus the full original cost (3 x 33.333 = 99.999).
        assertEquals(rp("105.000") - rp("99.999"), holding.realizedPl)
    }

    @Test
    fun sellFeeReducesRealisedProfit() {
        val holding = holdingOf(
            buy(id = "b1", quantity = "10", price = "10.000"),
            sell(id = "s1", quantity = "10", price = "12.000", fee = "2.000")
        )

        // 120.000 - 2.000 - 100.000
        assertEquals(rp("18.000"), holding.realizedPl)
    }

    // ---------- 4. oversell ----------

    @Test
    fun sellingMoreThanHeldReturnsNull() {
        val holding = PortfolioCalculator.Holding(quantity = qty("5"), totalCost = rp("50.000"))

        assertNull(PortfolioCalculator.applySell(holding, qty("6"), rp("10.000"), 0L))
        assertNotNull(PortfolioCalculator.applySell(holding, qty("5"), rp("10.000"), 0L))
    }

    @Test
    fun replayReportsTheOversellingTransaction() {
        val result = PortfolioCalculator.replay(
            listOf(
                buy(id = "b1", quantity = "5", price = "10.000", date = 1_000),
                sell(id = "s1", quantity = "8", price = "11.000", date = 2_000)
            )
        )

        assertTrue(result is PortfolioCalculator.ReplayResult.Oversell)
        val oversell = result as PortfolioCalculator.ReplayResult.Oversell
        assertEquals("s1", oversell.entryId)
        assertEquals(qty("5"), oversell.available)
        assertEquals(qty("8"), oversell.requested)
    }

    @Test
    fun replayFollowsDatesNotListOrder() {
        // A SELL dated before the BUY must be rejected even if it comes later in the list.
        val result = PortfolioCalculator.replay(
            listOf(
                sell(id = "s1", quantity = "1", price = "11.000", date = 1_000),
                buy(id = "b1", quantity = "1", price = "10.000", date = 2_000)
            )
        )

        assertTrue(result is PortfolioCalculator.ReplayResult.Oversell)
    }

    // ---------- 5. zero values ----------

    @Test
    fun zeroPriceAndQuantityDoNotCrash() {
        val holding = holdingOf(buy(quantity = "0", price = "0"))

        assertEquals(0L, holding.quantity)
        assertEquals(0L, holding.totalCost)
        assertEquals(0L, holding.averageBuyPrice)
        assertEquals(0L, PortfolioCalculator.unrealizedPl(holding, 0L))
        assertNull(PortfolioCalculator.returnPercent(holding, 0L))
    }

    @Test
    fun returnPercentUsesTheCostBasis() {
        val holding = PortfolioCalculator.Holding(quantity = qty("10"), totalCost = rp("100.000"))

        assertEquals(0, BigDecimal("20").compareTo(PortfolioCalculator.returnPercent(holding, rp("12.000"))))
    }

    @Test
    fun lossIsReportedAsNegativePercent() {
        val holding = PortfolioCalculator.Holding(quantity = qty("10"), totalCost = rp("100.000"))

        assertEquals(0, BigDecimal("-10").compareTo(PortfolioCalculator.returnPercent(holding, rp("9.000"))))
    }

    // ---------- 6. fractional quantity ----------

    @Test
    fun fractionalQuantityKeepsExactValues() {
        val holding = holdingOf(buy(quantity = "0,5", price = "1.000.000"))

        assertEquals(qty("0,5"), holding.quantity)
        assertEquals(rp("500.000"), holding.totalCost)
        assertEquals(rp("1.000.000"), holding.averageBuyPrice)
        assertEquals("0,5", Quantity.format(holding.quantity))
    }

    // ---------- deletion / recompute ----------

    @Test
    fun removingASellRestoresThePreviousHolding() {
        val all = listOf(
            buy(id = "b1", quantity = "10", price = "10.000", date = 1_000),
            sell(id = "s1", quantity = "4", price = "12.000", date = 2_000)
        )
        val afterDelete = PortfolioCalculator.replay(all.filterNot { it.id == "s1" })

        val holding = (afterDelete as PortfolioCalculator.ReplayResult.Success).holding
        assertEquals(qty("10"), holding.quantity)
        assertEquals(rp("100.000"), holding.totalCost)
        assertEquals(0L, holding.realizedPl)
    }

    // ---------- formatting ----------

    @Test
    fun rupiahParsingAcceptsIndonesianSeparators() {
        assertEquals(1_000_000L, rp("10.000"))
        assertEquals(1_000_050L, rp("10.000,50"))
        assertEquals(1_000_000L, rp("Rp10.000"))
        assertEquals(1_234_567L, rp("Rp 12.345,67"))
        // A dot with one or two decimals is a decimal separator, not thousands.
        assertEquals(1_050L, rp("10,5"))
        assertEquals(1_050L, rp("10.5"))
        assertEquals(123_456_700L, rp("1.234.567"))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("10.000,555"))
    }

    @Test
    fun quantityParsingKeepsFractionsAndThousandsApart() {
        assertEquals(500_000L, Quantity.parse("0,5"))
        assertEquals(500_000L, Quantity.parse("0.5"))
        assertEquals(10_000_000L, Quantity.parse("10"))
        assertEquals(1_000_000_000L, Quantity.parse("1.000"))
        assertNull(Quantity.parse("abc"))
        assertNull(Quantity.parse("-1"))
    }

    @Test
    fun rupiahFormattingHasNoFloatArtefacts() {
        assertEquals("Rp10.000", Money.format(1_000_000L))
        assertEquals("Rp12.345,67", Money.format(1_234_567L))
        assertEquals("+Rp5.000", Money.formatSigned(500_000L))
        assertEquals("-Rp5.000", Money.formatSigned(-500_000L))
        assertEquals("12,50%", Money.formatPercent(BigDecimal("12.5")))
    }

    /**
     * The price dialog layout already renders an "Rp" prefix, so the field text
     * must not repeat it and must still parse back to the same amount.
     */
    @Test
    fun priceFieldTextDropsThePrefixAndRoundTrips() {
        assertEquals("25.000", Money.formatForInput(2_500_000L))
        assertEquals(2_500_000L, Money.parseToCents(Money.formatForInput(2_500_000L)))
        assertEquals("12.345,67", Money.formatForInput(1_234_567L))
        assertEquals(1_234_567L, Money.parseToCents(Money.formatForInput(1_234_567L)))
    }
}
