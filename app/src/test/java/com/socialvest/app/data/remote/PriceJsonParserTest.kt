package com.socialvest.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PriceJsonParserTest {

    @Test
    fun `coingecko response is read per id`() {
        val body = """
            {
              "bitcoin": {"idr": 1223760131.44},
              "ethereum": {"idr": 34739691}
            }
        """.trimIndent()

        val prices = PriceJsonParser.parseCoinGeckoIdr(body, listOf("bitcoin", "ethereum", "solana"))

        assertEquals(2, prices.size)
        assertEquals(0, BigDecimal("1223760131.44").compareTo(prices.getValue("bitcoin")))
        assertEquals(0, BigDecimal("34739691").compareTo(prices.getValue("ethereum")))
        assertNull(prices["solana"])
    }

    @Test
    fun `coingecko error payload yields no prices`() {
        val body = """{"status":{"error_code":429,"error_message":"rate limited"}}"""
        assertTrue(PriceJsonParser.parseCoinGeckoIdr(body, listOf("bitcoin")).isEmpty())
    }

    @Test
    fun `yahoo chart exposes symbol currency and market price`() {
        val body = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "IDR",
                    "symbol": "BBCA.JK",
                    "regularMarketPrice": 5500.0
                  },
                  "indicators": {"quote": [{"close": [5480.0, 5500.0]}]}
                }],
                "error": null
              }
            }
        """.trimIndent()

        val quote = requireNotNull(PriceJsonParser.parseYahooChart(body))
        assertEquals("BBCA.JK", quote.symbol)
        assertEquals("IDR", quote.currency)
        assertEquals(0, BigDecimal("5500").compareTo(quote.amount))
    }

    @Test
    fun `yahoo falls back to the last non-null close`() {
        val body = """
            {
              "chart": {
                "result": [{
                  "meta": {"currency": "USD", "symbol": "AAPL", "regularMarketPrice": null},
                  "indicators": {"quote": [{"close": [340.1, null, 356.27]}]}
                }]
              }
            }
        """.trimIndent()

        val quote = requireNotNull(PriceJsonParser.parseYahooChart(body))
        assertEquals("USD", quote.currency)
        assertEquals(0, BigDecimal("356.27").compareTo(quote.amount))
    }

    @Test
    fun `yahoo gold quote keeps usd currency`() {
        val body = """
            {
              "chart": {
                "result": [{
                  "meta": {"currency": "USD", "symbol": "GC=F", "regularMarketPrice": 3886.3}
                }]
              }
            }
        """.trimIndent()

        val quote = requireNotNull(PriceJsonParser.parseYahooChart(body))
        assertEquals("GC=F", quote.symbol)
        assertEquals("USD", quote.currency)
        assertEquals(0, BigDecimal("3886.30").compareTo(quote.amount))
    }

    @Test
    fun `yahoo error payload and invalid json are rejected`() {
        assertNull(PriceJsonParser.parseYahooChart("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        assertNull(PriceJsonParser.parseYahooChart("not json"))
    }
}
