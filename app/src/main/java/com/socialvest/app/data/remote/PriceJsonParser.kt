package com.socialvest.app.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** One price returned by Yahoo Finance's chart endpoint. */
data class YahooQuote(
    val symbol: String,
    val currency: String,
    val amount: BigDecimal
)

/**
 * Parsers for the two public endpoints, isolated from networking so they can be
 * unit-tested with recorded responses.
 *
 * Numeric JSON values are converted with `toString()` and then [BigDecimal], so
 * no financial arithmetic happens in `Double`.
 */
object PriceJsonParser {

    /**
     * `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=idr`
     * returns `{"bitcoin":{"idr":1223760131},"ethereum":{"idr":34739691}}`.
     */
    fun parseCoinGeckoIdr(body: String, ids: Collection<String>): Map<String, BigDecimal> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyMap()
        return ids.mapNotNull { id ->
            val node = root.optJSONObject(id) ?: return@mapNotNull null
            val value = toBigDecimal(node.opt("idr")) ?: return@mapNotNull null
            if (value.signum() <= 0) return@mapNotNull null
            id to value
        }.toMap()
    }

    /**
     * `https://query1.finance.yahoo.com/v8/finance/chart/BBCA.JK?interval=1d&range=1d`
     * exposes the latest price in `chart.result[0].meta.regularMarketPrice`, and
     * the traded currency in `meta.currency`. When the meta price is missing,
     * the last non-null close is used instead.
     */
    fun parseYahooChart(body: String): YahooQuote? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val chart = root.optJSONObject("chart") ?: return null
        val results = chart.optJSONArray("result") ?: return null
        if (results.length() == 0) return null

        val first = results.optJSONObject(0) ?: return null
        val meta = first.optJSONObject("meta") ?: return null
        val currency = meta.optString("currency", "").trim().uppercase()
        if (currency.isEmpty()) return null

        val amount = toBigDecimal(meta.opt("regularMarketPrice"))
            ?: lastClose(first)
            ?: return null
        if (amount.signum() <= 0) return null

        return YahooQuote(
            symbol = meta.optString("symbol", "").trim(),
            currency = currency,
            amount = amount
        )
    }

    private fun lastClose(result: JSONObject): BigDecimal? {
        val quote = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?: return null
        val closes: JSONArray = quote.optJSONArray("close") ?: return null
        for (index in closes.length() - 1 downTo 0) {
            val value = toBigDecimal(closes.opt(index)) ?: continue
            if (value.signum() > 0) return value
        }
        return null
    }

    private fun toBigDecimal(value: Any?): BigDecimal? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> runCatching { BigDecimal(value.toString()) }.getOrNull()
        is String -> runCatching { BigDecimal(value) }.getOrNull()
        else -> null
    }
}
