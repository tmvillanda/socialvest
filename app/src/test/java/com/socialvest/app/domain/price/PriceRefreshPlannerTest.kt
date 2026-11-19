package com.socialvest.app.domain.price

import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.model.AssetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceRefreshPlannerTest {

    private fun asset(
        name: String,
        ticker: String,
        category: AssetCategory,
        priceSymbol: String = ""
    ) = Asset(
        assetName = name,
        ticker = ticker,
        category = category,
        priceSymbol = priceSymbol
    )

    @Test
    fun `crypto ticker is resolved to a CoinGecko id`() {
        val plan = RefreshPlanner.plan(
            listOf(asset("Bitcoin", "BTC", AssetCategory.CRYPTO))
        )

        assertEquals(1, plan.targetCount)
        val target = plan.targets.single()
        assertEquals(PriceProviderKind.COINGECKO, target.kind)
        assertEquals("bitcoin", target.cryptoId)
        assertTrue(plan.manual.isEmpty())
    }

    @Test
    fun `unknown crypto slug is still tried as a CoinGecko id`() {
        val plan = RefreshPlanner.plan(
            listOf(asset("Custom Coin", "some-coin", AssetCategory.CRYPTO))
        )

        assertEquals("some-coin", plan.targets.single().cryptoId)
    }

    @Test
    fun `stock tries the Indonesian listing before the bare ticker`() {
        val plan = RefreshPlanner.plan(
            listOf(asset("Bank Central Asia", "BBCA", AssetCategory.STOCK))
        )

        assertEquals(
            listOf("BBCA.JK", "BBCA"),
            plan.targets.single().symbols
        )
    }

    @Test
    fun `stored symbol is reused so a refresh does not guess again`() {
        val plan = RefreshPlanner.plan(
            listOf(
                asset(
                    "Apple",
                    "AAPL",
                    AssetCategory.STOCK,
                    priceSymbol = "AAPL"
                )
            )
        )

        assertEquals(listOf("AAPL"), plan.targets.single().symbols)
    }

    @Test
    fun `gold uses the world gold reference and ignores the ticker`() {
        val plan = RefreshPlanner.plan(
            listOf(asset("Emas Antam", "ANTAM", AssetCategory.GOLD))
        )

        val target = plan.targets.single()
        assertEquals(PriceProviderKind.YAHOO_GOLD, target.kind)
        assertEquals(listOf(RefreshPlanner.GOLD_SYMBOL), target.symbols)
    }

    @Test
    fun `mutual funds and bonds stay manual`() {
        val plan = RefreshPlanner.plan(
            listOf(
                asset("Reksa Dana Pasar Uang", "RDPU", AssetCategory.MUTUAL_FUND),
                asset("SBN RI", "SBN", AssetCategory.BOND),
                asset("Lainnya", "", AssetCategory.OTHER)
            )
        )

        assertEquals(0, plan.targetCount)
        assertEquals(3, plan.manual.size)
        assertTrue(plan.manual.all { it.reason == ManualReason.UNSUPPORTED_CATEGORY })
    }

    @Test
    fun `stock without ticker cannot be refreshed`() {
        val plan = RefreshPlanner.plan(
            listOf(asset("Tanpa Kode", "  ", AssetCategory.STOCK))
        )

        assertEquals(0, plan.targetCount)
        assertEquals(ManualReason.MISSING_TICKER, plan.manual.single().reason)
    }

    @Test
    fun `at most ten assets are refreshed in one run`() {
        val assets = (1..12).map { index ->
            asset("Saham $index", "TICK$index", AssetCategory.STOCK)
        }

        val plan = RefreshPlanner.plan(assets)

        assertEquals(RefreshPlanner.MAX_TARGETS, plan.targetCount)
        assertEquals(2, plan.manual.size)
        assertTrue(plan.manual.all { it.reason == ManualReason.LIMIT_REACHED })
    }
}
