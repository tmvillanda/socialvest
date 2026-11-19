package com.socialvest.app.data.remote

import com.socialvest.app.domain.market.MarketInstrument
import com.socialvest.app.domain.market.MarketProvider
import com.socialvest.app.domain.market.QuoteFetchPlanner
import com.socialvest.app.domain.price.PriceFailureReason
import com.socialvest.app.domain.price.PriceMath
import kotlinx.coroutines.delay

enum class MarketQuoteSource { YAHOO, COINGECKO }

/** One labelled snapshot: never auto-refreshed, never presented as real-time. */
data class MarketQuote(
    val ticker: String,
    val priceSen: Long,
    val source: MarketQuoteSource,
    val fetchedAt: Long
)

data class MarketQuoteFailure(
    val ticker: String,
    val reason: PriceFailureReason
)

data class MarketQuoteBatch(
    val quotes: Map<String, MarketQuote>,
    val failures: List<MarketQuoteFailure>,
    val fetchedAt: Long
)

/**
 * Fetches one-off quotes for the Search catalogue from the same free sources the
 * portfolio uses: Yahoo Finance for IDX stocks and CoinGecko for crypto.
 *
 * The service never invents a price. A symbol that cannot be resolved is
 * reported as a failure so the UI can show "Harga tidak tersedia" and keep the
 * manual price path available.
 *
 * Stocks are requested in bursts of [QuoteFetchPlanner.STOCK_BATCH_SIZE] with a
 * short pause between bursts, so one press can cover the whole visible list
 * without hammering the free endpoint. Successful quotes are cached for
 * [CACHE_TTL_MS] and reused when the tab is reopened, unless the caller asks
 * for a forced refresh.
 */
class MarketQuoteService(
    private val http: HttpTextClient = HttpTextClient(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val batchPauseMs: Long = STOCK_BATCH_PAUSE_MS
) {

    private val cache = mutableMapOf<String, MarketQuote>()
    private var lastYahooRequestAt = 0L

    /**
     * Every quote already fetched, keyed by ticker. Older entries keep their
     * original timestamp so the UI can label them honestly.
     */
    fun cachedQuotes(): Map<String, MarketQuote> = cache.toMap()

    suspend fun fetch(
        instruments: List<MarketInstrument>,
        forceRefresh: Boolean = false,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): MarketQuoteBatch {
        val targets = instruments.distinctBy { it.ticker }
        val quotes = mutableMapOf<String, MarketQuote>()
        val failures = mutableListOf<MarketQuoteFailure>()
        val total = targets.size
        var completed = 0

        val crypto = targets.filter { it.provider == MarketProvider.COINGECKO }
        val cryptoToFetch = mutableListOf<MarketInstrument>()
        crypto.forEach { target ->
            if (!useCached(target, forceRefresh, quotes)) cryptoToFetch += target
        }
        if (cryptoToFetch.isNotEmpty()) {
            fetchCrypto(cryptoToFetch, quotes, failures)
        }
        crypto.forEach {
            completed += 1
            onProgress(completed, total)
        }

        val stocks = targets.filter { it.provider == MarketProvider.YAHOO_STOCK }
        QuoteFetchPlanner.batches(stocks).forEachIndexed { index, batch ->
            if (index > 0 && batchPauseMs > 0L) delay(batchPauseMs)
            batch.forEach { target ->
                if (!useCached(target, forceRefresh, quotes)) {
                    fetchStock(target, quotes, failures)
                }
                completed += 1
                onProgress(completed, total)
            }
        }

        return MarketQuoteBatch(
            quotes = quotes,
            failures = failures,
            fetchedAt = now()
        )
    }

    /** Returns true when a fresh cached quote was reused instead of calling the source. */
    private fun useCached(
        target: MarketInstrument,
        forceRefresh: Boolean,
        quotes: MutableMap<String, MarketQuote>
    ): Boolean {
        if (forceRefresh) return false
        val cached = cache[target.ticker] ?: return false
        if (now() - cached.fetchedAt > CACHE_TTL_MS) return false
        quotes[target.ticker] = cached
        return true
    }

    private suspend fun fetchCrypto(
        targets: List<MarketInstrument>,
        quotes: MutableMap<String, MarketQuote>,
        failures: MutableList<MarketQuoteFailure>
    ) {
        val ids = targets.map { it.quoteKey }.distinct()
        val url = "$COINGECKO_URL?ids=${ids.joinToString(",")}&vs_currencies=idr"

        when (val result = http.getJson(url)) {
            is HttpResult.Success -> {
                val prices = PriceJsonParser.parseCoinGeckoIdr(result.body, ids)
                val fetchedAt = now()
                targets.forEach { target ->
                    val sen = prices[target.quoteKey]?.let(PriceMath::toSen)
                    if (sen == null) {
                        failures += MarketQuoteFailure(target.ticker, PriceFailureReason.NO_DATA)
                    } else {
                        val quote = MarketQuote(
                            ticker = target.ticker,
                            priceSen = sen,
                            source = MarketQuoteSource.COINGECKO,
                            fetchedAt = fetchedAt
                        )
                        cache[target.ticker] = quote
                        quotes[target.ticker] = quote
                    }
                }
            }

            is HttpResult.Failure -> targets.forEach { target ->
                failures += MarketQuoteFailure(target.ticker, result.reason.toPriceFailure())
            }
        }
    }

    private suspend fun fetchStock(
        target: MarketInstrument,
        quotes: MutableMap<String, MarketQuote>,
        failures: MutableList<MarketQuoteFailure>
    ) {
        throttleYahoo()

        when (val result = http.getJson(chartUrl(target.quoteKey))) {
            is HttpResult.Success -> {
                val quote = PriceJsonParser.parseYahooChart(result.body)
                when {
                    quote == null -> failures += MarketQuoteFailure(
                        target.ticker,
                        PriceFailureReason.INVALID_RESPONSE
                    )

                    quote.currency != "IDR" -> failures += MarketQuoteFailure(
                        target.ticker,
                        PriceFailureReason.UNSUPPORTED_CURRENCY
                    )

                    else -> {
                        val sen = PriceMath.toSen(quote.amount)
                        if (sen == null) {
                            failures += MarketQuoteFailure(
                                target.ticker,
                                PriceFailureReason.NO_DATA
                            )
                        } else {
                            val snapshot = MarketQuote(
                                ticker = target.ticker,
                                priceSen = sen,
                                source = MarketQuoteSource.YAHOO,
                                fetchedAt = now()
                            )
                            cache[target.ticker] = snapshot
                            quotes[target.ticker] = snapshot
                        }
                    }
                }
            }

            is HttpResult.Failure -> failures += MarketQuoteFailure(
                target.ticker,
                result.reason.toPriceFailure()
            )
        }
    }

    /** Keeps consecutive Yahoo requests at least [THROTTLE_MS] apart. */
    private suspend fun throttleYahoo() {
        if (lastYahooRequestAt > 0L) {
            val elapsed = now() - lastYahooRequestAt
            if (elapsed >= 0L && elapsed < THROTTLE_MS) {
                delay(THROTTLE_MS - elapsed)
            }
        }
        lastYahooRequestAt = now()
    }

    private fun chartUrl(symbol: String): String {
        val safe = symbol.trim().replace(" ", "")
        return "$YAHOO_CHART_URL/$safe?interval=1d&range=1d"
    }

    private fun HttpFailure.toPriceFailure(): PriceFailureReason = when (this) {
        HttpFailure.NOT_FOUND -> PriceFailureReason.SYMBOL_NOT_FOUND
        HttpFailure.RATE_LIMITED -> PriceFailureReason.RATE_LIMITED
        HttpFailure.SERVER -> PriceFailureReason.PROVIDER_ERROR
        HttpFailure.NETWORK, HttpFailure.TIMEOUT -> PriceFailureReason.NETWORK
    }

    companion object {
        private const val COINGECKO_URL = "https://api.coingecko.com/api/v3/simple/price"
        private const val YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart"
        private const val THROTTLE_MS = 250L
        private const val STOCK_BATCH_PAUSE_MS = 500L

        /** Quotes younger than this are reused by automatic loads. */
        const val CACHE_TTL_MS = 120_000L
    }
}
