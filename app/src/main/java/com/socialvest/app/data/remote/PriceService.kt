package com.socialvest.app.data.remote

import com.socialvest.app.data.model.PriceSource
import com.socialvest.app.domain.price.FetchedPrice
import com.socialvest.app.domain.price.PriceFailure
import com.socialvest.app.domain.price.PriceFailureReason
import com.socialvest.app.domain.price.PriceMath
import com.socialvest.app.domain.price.PriceProviderKind
import com.socialvest.app.domain.price.PriceRefreshOutcome
import com.socialvest.app.domain.price.PriceTarget
import com.socialvest.app.domain.price.RefreshPlan
import com.socialvest.app.domain.price.RefreshPlanner
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * Fetches prices for a [RefreshPlan] from two free public endpoints:
 *
 * - CoinGecko for crypto (one batched request for all crypto assets);
 * - Yahoo Finance for stocks and the world gold reference.
 *
 * The service is deliberately conservative: a provider failure never blocks
 * the other assets, and an asset whose symbol cannot be resolved stays
 * untouched instead of receiving a guessed price. Successful quotes are cached
 * for [CACHE_TTL_MS] so pressing "Refresh" twice in a row does not hammer the
 * free endpoints.
 */
class PriceService(
    private val http: HttpTextClient = HttpTextClient(),
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private data class CachedQuote(val quote: YahooQuote, val at: Long)

    private sealed interface YahooFetch {
        data class Success(val quote: YahooQuote) : YahooFetch
        data object NotFound : YahooFetch
        data class Failed(val reason: PriceFailureReason) : YahooFetch
    }

    private val yahooCache = mutableMapOf<String, CachedQuote>()
    private var usdIdrCache: CachedQuote? = null
    private var lastYahooRequestAt = 0L

    suspend fun refresh(
        plan: RefreshPlan,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): PriceRefreshOutcome {
        val updates = mutableListOf<FetchedPrice>()
        val failures = mutableListOf<PriceFailure>()
        val total = plan.targets.size
        var completed = 0

        val crypto = plan.targets.filter { it.kind == PriceProviderKind.COINGECKO }
        if (crypto.isNotEmpty()) {
            fetchCrypto(crypto, updates, failures)
            completed += crypto.size
            onProgress(completed, total)
        }

        plan.targets
            .filter { it.kind != PriceProviderKind.COINGECKO }
            .forEach { target ->
                when (target.kind) {
                    PriceProviderKind.YAHOO_STOCK -> fetchStock(target, updates, failures)
                    PriceProviderKind.YAHOO_GOLD -> fetchGold(target, updates, failures)
                    PriceProviderKind.COINGECKO -> Unit
                }
                completed += 1
                onProgress(completed, total)
            }

        return PriceRefreshOutcome(updates = updates, failures = failures)
    }

    // ---------------- crypto ----------------

    private suspend fun fetchCrypto(
        targets: List<PriceTarget>,
        updates: MutableList<FetchedPrice>,
        failures: MutableList<PriceFailure>
    ) {
        val ids = targets.mapNotNull { it.cryptoId }.distinct()
        if (ids.isEmpty()) {
            targets.forEach { failures += PriceFailure(it.label, PriceFailureReason.NO_DATA) }
            return
        }

        val url = "$COINGECKO_URL?ids=${ids.joinToString(",")}&vs_currencies=idr"
        when (val result = http.getJson(url)) {
            is HttpResult.Success -> {
                val prices = PriceJsonParser.parseCoinGeckoIdr(result.body, ids)
                targets.forEach { target ->
                    val id = target.cryptoId
                    val sen = id?.let(prices::get)?.let(PriceMath::toSen)
                    if (id == null || sen == null) {
                        failures += PriceFailure(target.label, PriceFailureReason.NO_DATA)
                    } else {
                        updates += FetchedPrice(
                            assetId = target.assetId,
                            price = sen,
                            source = PriceSource.COINGECKO,
                            symbol = id
                        )
                    }
                }
            }

            is HttpResult.Failure ->
                targets.forEach {
                    failures += PriceFailure(it.label, result.reason.toPriceFailure())
                }
        }
    }

    // ---------------- stocks ----------------

    private suspend fun fetchStock(
        target: PriceTarget,
        updates: MutableList<FetchedPrice>,
        failures: MutableList<PriceFailure>
    ) {
        var lastReason = PriceFailureReason.SYMBOL_NOT_FOUND

        for (symbol in target.symbols) {
            when (val fetch = fetchYahooQuote(symbol)) {
                is YahooFetch.Success -> {
                    val quote = fetch.quote
                    val idr = when (quote.currency) {
                        "IDR" -> quote.amount
                        "USD" -> {
                            val rate = fetchUsdIdr()
                            if (rate == null) {
                                failures += PriceFailure(
                                    target.label,
                                    PriceFailureReason.INVALID_RESPONSE
                                )
                                return
                            }
                            PriceMath.usdToIdr(quote.amount, rate)
                        }
                        else -> {
                            failures += PriceFailure(
                                target.label,
                                PriceFailureReason.UNSUPPORTED_CURRENCY
                            )
                            return
                        }
                    }

                    val sen = PriceMath.toSen(idr)
                    if (sen == null) {
                        failures += PriceFailure(target.label, PriceFailureReason.NO_DATA)
                    } else {
                        updates += FetchedPrice(
                            assetId = target.assetId,
                            price = sen,
                            source = PriceSource.YAHOO,
                            symbol = quote.symbol.ifBlank { symbol }
                        )
                    }
                    return
                }

                YahooFetch.NotFound -> lastReason = PriceFailureReason.SYMBOL_NOT_FOUND

                is YahooFetch.Failed -> {
                    failures += PriceFailure(target.label, fetch.reason)
                    return
                }
            }
        }

        failures += PriceFailure(target.label, lastReason)
    }

    // ---------------- gold ----------------

    private suspend fun fetchGold(
        target: PriceTarget,
        updates: MutableList<FetchedPrice>,
        failures: MutableList<PriceFailure>
    ) {
        when (val fetch = fetchYahooQuote(RefreshPlanner.GOLD_SYMBOL)) {
            is YahooFetch.Success -> {
                val quote = fetch.quote
                if (quote.currency != "USD") {
                    failures += PriceFailure(
                        target.label,
                        PriceFailureReason.UNSUPPORTED_CURRENCY
                    )
                    return
                }

                val rate = fetchUsdIdr()
                if (rate == null) {
                    failures += PriceFailure(target.label, PriceFailureReason.INVALID_RESPONSE)
                    return
                }

                val perGram = PriceMath.goldOunceToGramIdr(quote.amount, rate)
                val sen = PriceMath.toSen(perGram)
                if (sen == null) {
                    failures += PriceFailure(target.label, PriceFailureReason.NO_DATA)
                } else {
                    updates += FetchedPrice(
                        assetId = target.assetId,
                        price = sen,
                        source = PriceSource.YAHOO,
                        symbol = RefreshPlanner.GOLD_SYMBOL
                    )
                }
            }

            YahooFetch.NotFound -> failures += PriceFailure(
                target.label,
                PriceFailureReason.SYMBOL_NOT_FOUND
            )

            is YahooFetch.Failed -> failures += PriceFailure(target.label, fetch.reason)
        }
    }

    // ---------------- Yahoo helper ----------------

    private suspend fun fetchUsdIdr(): BigDecimal? {
        usdIdrCache?.let { cached ->
            if (now() - cached.at <= CACHE_TTL_MS) return cached.quote.amount
        }

        return when (val fetch = fetchYahooQuote(USD_IDR_SYMBOL)) {
            is YahooFetch.Success -> {
                val rate = fetch.quote.amount
                // Sanity check: a USD/IDR rate outside this range is not a real
                // quote, so it is safer to fail than to convert with it.
                if (rate < BigDecimal("1000") || rate > BigDecimal("100000")) {
                    null
                } else {
                    usdIdrCache = CachedQuote(fetch.quote, now())
                    rate
                }
            }
            else -> null
        }
    }

    private suspend fun fetchYahooQuote(symbol: String): YahooFetch {
        yahooCache[symbol]?.let { cached ->
            if (now() - cached.at <= CACHE_TTL_MS) return YahooFetch.Success(cached.quote)
        }

        throttleYahoo()

        return when (val result = http.getJson(chartUrl(symbol))) {
            is HttpResult.Success -> {
                val quote = PriceJsonParser.parseYahooChart(result.body)
                if (quote == null) {
                    YahooFetch.Failed(PriceFailureReason.INVALID_RESPONSE)
                } else {
                    val resolved = if (quote.symbol.isBlank()) quote.copy(symbol = symbol) else quote
                    yahooCache[symbol] = CachedQuote(resolved, now())
                    YahooFetch.Success(resolved)
                }
            }

            is HttpResult.Failure -> when (result.reason) {
                HttpFailure.NOT_FOUND -> YahooFetch.NotFound
                else -> YahooFetch.Failed(result.reason.toPriceFailure())
            }
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
        private const val COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price"
        private const val YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart"
        private const val USD_IDR_SYMBOL = "IDR=X"
        private const val THROTTLE_MS = 250L
        private const val CACHE_TTL_MS = 60_000L
    }
}
