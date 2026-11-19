package com.socialvest.app.domain.market

import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.domain.price.SymbolResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketCatalogTest {

    @Test
    fun `blank query returns the whole catalogue`() {
        assertEquals(MarketCatalog.instruments.size, MarketCatalog.search("").size)
    }

    @Test
    fun `ticker search is case insensitive and ranks the exact ticker first`() {
        val results = MarketCatalog.search("bbca")

        assertEquals("BBCA", results.first().ticker)
        assertTrue(results.none { it.ticker == "BBRI" })
    }

    @Test
    fun `name search finds instruments by a word in the name`() {
        val results = MarketCatalog.search("telkom")

        assertEquals(listOf("TLKM"), results.map { it.ticker })
    }

    @Test
    fun `category filter keeps only the requested category`() {
        val stocks = MarketCatalog.search("", AssetCategory.STOCK)
        val crypto = MarketCatalog.search("", AssetCategory.CRYPTO)

        assertTrue(stocks.isNotEmpty())
        assertTrue(crypto.isNotEmpty())
        assertTrue(stocks.all { it.category == AssetCategory.STOCK })
        assertTrue(crypto.all { it.category == AssetCategory.CRYPTO })
        assertEquals(MarketCatalog.instruments.size, stocks.size + crypto.size)
    }

    @Test
    fun `unknown query returns an empty list`() {
        assertTrue(MarketCatalog.search("tidakadasimbolini").isEmpty())
    }

    @Test
    fun `crypto quote keys stay compatible with the refresh resolver`() {
        MarketCatalog.instruments
            .filter { it.category == AssetCategory.CRYPTO }
            .forEach { instrument ->
                assertEquals(
                    "CoinGecko id mismatch for ${instrument.ticker}",
                    instrument.quoteKey,
                    SymbolResolver.cryptoId(instrument.ticker)
                )
            }
    }

    @Test
    fun `stock quote keys use the IDX suffix`() {
        MarketCatalog.instruments
            .filter { it.category == AssetCategory.STOCK }
            .forEach { instrument ->
                assertTrue(instrument.quoteKey.endsWith(".JK"))
            }
    }

    @Test
    fun `tickers are unique so quote labels never collide`() {
        val tickers = MarketCatalog.instruments.map { it.ticker }
        assertEquals(tickers.size, tickers.distinct().size)
    }
}
