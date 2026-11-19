package com.socialvest.app.domain.market

/**
 * Decides which symbols a "Perbarui harga" press should request and in what
 * order, independently of Android and networking so it can be unit-tested.
 *
 * Ordering is retry-first: symbols that failed or were never fetched come
 * before symbols that already show a labelled price. A second press therefore
 * only fills the gaps instead of re-hammering symbols that already worked.
 *
 * The whole visible list is covered. [STOCK_BATCH_SIZE] only limits how many
 * stock requests are sent back-to-back, never how many symbols can be priced.
 */
object QuoteFetchPlanner {

    /** Stock requests per burst; keeps the free Yahoo endpoint friendly. */
    const val STOCK_BATCH_SIZE = 8

    data class Plan(
        val targets: List<MarketInstrument>,
        /** Visible symbols that currently show no price at all. */
        val pendingCount: Int,
        /** Visible symbols whose last attempt failed and that still have no price. */
        val failedCount: Int,
        /** Stock symbols among [targets]; crypto is fetched in one batch. */
        val stockTargetCount: Int
    )

    fun plan(
        visible: List<MarketInstrument>,
        quotedTickers: Set<String>,
        failedTickers: Set<String>
    ): Plan {
        val pending = visible.filter { it.ticker !in quotedTickers }
        val retry = pending.filter { it.ticker in failedTickers }
        val untouched = pending.filter { it.ticker !in failedTickers }
        val priced = visible.filter { it.ticker in quotedTickers }
        val targets = (retry + untouched + priced).distinctBy { it.ticker }

        return Plan(
            targets = targets,
            pendingCount = pending.size,
            failedCount = retry.size,
            stockTargetCount = targets.count { it.provider == MarketProvider.YAHOO_STOCK }
        )
    }

    /** Splits stocks into ordered bursts; the flattened result keeps every symbol. */
    fun batches(
        stocks: List<MarketInstrument>,
        batchSize: Int = STOCK_BATCH_SIZE
    ): List<List<MarketInstrument>> =
        if (batchSize <= 0) listOf(stocks) else stocks.chunked(batchSize)
}
