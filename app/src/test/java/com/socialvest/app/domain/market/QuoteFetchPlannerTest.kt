package com.socialvest.app.domain.market

import com.socialvest.app.data.model.AssetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteFetchPlannerTest {

    private val stocks = MarketCatalog.search("", AssetCategory.STOCK)
    private val crypto = MarketCatalog.search("", AssetCategory.CRYPTO)

    @Test
    fun `blank cache plans every visible symbol`() {
        val plan = QuoteFetchPlanner.plan(
            visible = MarketCatalog.instruments,
            quotedTickers = emptySet(),
            failedTickers = emptySet()
        )

        assertEquals(MarketCatalog.instruments.size, plan.targets.size)
        assertEquals(MarketCatalog.instruments.size, plan.pendingCount)
        assertEquals(0, plan.failedCount)
        assertEquals(stocks.size, plan.stockTargetCount)
    }

    @Test
    fun `stocks are split into bursts of eight without dropping any`() {
        val batches = QuoteFetchPlanner.batches(stocks)

        assertEquals(listOf(8, 8, 8, 2), batches.map { it.size })
        assertEquals(stocks, batches.flatten())
    }

    @Test
    fun `custom burst size still keeps every symbol and its order`() {
        val subset = stocks.take(5)
        val batches = QuoteFetchPlanner.batches(subset, batchSize = 2)

        assertEquals(listOf(2, 2, 1), batches.map { it.size })
        assertEquals(subset, batches.flatten())
    }

    @Test
    fun `symbols without a quote come before priced ones`() {
        val visible = stocks.take(3)
        val plan = QuoteFetchPlanner.plan(
            visible = visible,
            quotedTickers = setOf(visible[0].ticker),
            failedTickers = emptySet()
        )

        assertEquals(visible[0].ticker, plan.targets.last().ticker)
        assertEquals(2, plan.pendingCount)
    }

    @Test
    fun `previously failed symbols are retried before untouched ones`() {
        val visible = stocks.take(4)
        val failedTicker = visible[2].ticker

        val plan = QuoteFetchPlanner.plan(
            visible = visible,
            quotedTickers = emptySet(),
            failedTickers = setOf(failedTicker)
        )

        assertEquals(failedTicker, plan.targets.first().ticker)
        assertEquals(1, plan.failedCount)
    }

    @Test
    fun `priced symbols are still requested so a force refresh can renew them`() {
        val visible = stocks.take(3)
        val quoted = visible.map { it.ticker }.toSet()

        val plan = QuoteFetchPlanner.plan(visible, quoted, emptySet())

        assertEquals(visible.map { it.ticker }, plan.targets.map { it.ticker })
        assertEquals(0, plan.pendingCount)
    }

    @Test
    fun `counts ignore failures that already have a price`() {
        val visible = stocks.take(4)
        val priced = visible[0].ticker
        val failedOnly = visible[1].ticker

        val plan = QuoteFetchPlanner.plan(
            visible = visible,
            quotedTickers = setOf(priced),
            failedTickers = setOf(priced, failedOnly)
        )

        assertEquals(3, plan.pendingCount)
        assertEquals(1, plan.failedCount)
    }

    @Test
    fun `no duplicate targets when groups overlap`() {
        val plan = QuoteFetchPlanner.plan(
            visible = stocks,
            quotedTickers = stocks.dropLast(2).map { it.ticker }.toSet(),
            failedTickers = setOf(stocks.last().ticker)
        )

        val tickers = plan.targets.map { it.ticker }
        assertEquals(tickers.size, tickers.distinct().size)
        assertEquals(stocks.size, plan.targets.size)
        assertEquals(stocks.last().ticker, plan.targets.first().ticker)
    }

    @Test
    fun `empty visible list plans nothing`() {
        val plan = QuoteFetchPlanner.plan(emptyList(), emptySet(), emptySet())

        assertTrue(plan.targets.isEmpty())
        assertEquals(0, plan.pendingCount)
        assertEquals(0, plan.failedCount)
        assertEquals(0, plan.stockTargetCount)
    }

    @Test
    fun `crypto-only view has no stock targets`() {
        val plan = QuoteFetchPlanner.plan(crypto, emptySet(), emptySet())

        assertEquals(crypto.size, plan.pendingCount)
        assertEquals(0, plan.stockTargetCount)
    }
}
