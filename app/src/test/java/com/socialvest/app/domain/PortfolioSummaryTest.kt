package com.socialvest.app.domain

import com.socialvest.app.data.model.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioSummaryTest {

    /** 2 units bought for Rp2.000, now valued at Rp1.500 per unit. */
    private fun heldAsset() = Asset(
        id = "a",
        quantity = 2_000_000L,
        totalCost = 200_000L,
        currentPrice = 150_000L
    )

    /** Fully sold: keeps its realised P/L but no current value or cost. */
    private fun soldAsset() = Asset(
        id = "b",
        quantity = 0L,
        totalCost = 0L,
        realizedPl = 25_000L,
        currentPrice = 150_000L
    )

    @Test
    fun `no assets means nothing to show`() {
        val summary = PortfolioSummary.of(emptyList())

        assertFalse(summary.hasAssets)
        assertEquals(0L, summary.totalValue)
        assertEquals(0L, summary.totalCost)
        assertEquals(0L, summary.totalUnrealized)
        assertEquals(0L, summary.totalRealized)
        assertNull(summary.returnPercent)
    }

    @Test
    fun `held asset totals match the calculator`() {
        val summary = PortfolioSummary.of(listOf(heldAsset()))

        assertTrue(summary.hasAssets)
        assertEquals(300_000L, summary.totalValue)
        assertEquals(200_000L, summary.totalCost)
        assertEquals(100_000L, summary.totalUnrealized)
        assertEquals(0L, summary.totalRealized)
        assertEquals(BigDecimal("50.0000"), summary.returnPercent)
    }

    @Test
    fun `realised profit is summed but sold assets add no value or cost`() {
        val summary = PortfolioSummary.of(listOf(heldAsset(), soldAsset()))

        assertEquals(300_000L, summary.totalValue)
        assertEquals(200_000L, summary.totalCost)
        assertEquals(100_000L, summary.totalUnrealized)
        assertEquals(25_000L, summary.totalRealized)
        assertEquals(BigDecimal("50.0000"), summary.returnPercent)
    }

    @Test
    fun `fully sold portfolio has no value and no percent`() {
        val summary = PortfolioSummary.of(listOf(soldAsset()))

        assertTrue(summary.hasAssets)
        assertEquals(0L, summary.totalValue)
        assertEquals(0L, summary.totalCost)
        assertEquals(0L, summary.totalUnrealized)
        assertEquals(25_000L, summary.totalRealized)
        assertNull(summary.returnPercent)
    }

    @Test
    fun `unrealised loss is negative`() {
        val losing = Asset(
            id = "c",
            quantity = 1_000_000L,
            totalCost = 100_000L,
            currentPrice = 50_000L
        )

        val summary = PortfolioSummary.of(listOf(losing))

        assertEquals(50_000L, summary.totalValue)
        assertEquals(-50_000L, summary.totalUnrealized)
        assertEquals(BigDecimal("-50.0000"), summary.returnPercent)
    }
}
