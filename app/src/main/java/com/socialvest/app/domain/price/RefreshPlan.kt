package com.socialvest.app.domain.price

import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.model.AssetCategory

/** Provider that will price one target. */
enum class PriceProviderKind { COINGECKO, YAHOO_STOCK, YAHOO_GOLD }

/** Why an asset is not refreshed automatically. */
enum class ManualReason { UNSUPPORTED_CATEGORY, MISSING_TICKER, LIMIT_REACHED }

/** Why one automatic refresh failed. Rendered as Indonesian copy in the UI. */
enum class PriceFailureReason {
    SYMBOL_NOT_FOUND,
    NO_DATA,
    NETWORK,
    RATE_LIMITED,
    UNSUPPORTED_CURRENCY,
    INVALID_RESPONSE,
    PROVIDER_ERROR
}

data class PriceTarget(
    val assetId: String,
    val assetName: String,
    val ticker: String,
    val kind: PriceProviderKind,
    /** CoinGecko id for crypto targets. */
    val cryptoId: String? = null,
    /** Yahoo symbols to try in order for stock targets. */
    val symbols: List<String> = emptyList()
) {
    val label: String
        get() = if (ticker.isBlank()) assetName else "$assetName ($ticker)"
}

data class ManualAsset(
    val assetId: String,
    val label: String,
    val reason: ManualReason
)

data class RefreshPlan(
    val targets: List<PriceTarget>,
    val manual: List<ManualAsset>
) {
    val targetCount: Int get() = targets.size
    val isEmpty: Boolean get() = targets.isEmpty()
}

/**
 * Decides which assets can be refreshed from a public source and which must
 * stay manual. Kept pure so the policy is unit-testable without a network.
 *
 * - CRYPTO -> CoinGecko (needs a mappable CoinGecko id)
 * - STOCK  -> Yahoo Finance (`TICKER.JK` then `TICKER`)
 * - GOLD   -> world gold reference (`GC=F`) converted to IDR/gram
 * - MUTUAL_FUND / BOND / OTHER -> manual only, because no free, stable, and
 *   terms-compliant source is available.
 */
object RefreshPlanner {

    /** Keeps a single refresh short and polite to the free endpoints. */
    const val MAX_TARGETS = 10

    fun plan(assets: List<Asset>): RefreshPlan {
        val targets = mutableListOf<PriceTarget>()
        val manual = mutableListOf<ManualAsset>()

        assets.sortedBy { it.assetName.lowercase() }.forEach { asset ->
            if (targets.size >= MAX_TARGETS) {
                manual += ManualAsset(asset.id, labelOf(asset), ManualReason.LIMIT_REACHED)
                return@forEach
            }

            when (asset.category) {
                AssetCategory.CRYPTO -> {
                    val id = SymbolResolver.cryptoId(asset.ticker)
                    if (id == null) {
                        manual += ManualAsset(asset.id, labelOf(asset), ManualReason.MISSING_TICKER)
                    } else {
                        targets += PriceTarget(
                            assetId = asset.id,
                            assetName = asset.assetName,
                            ticker = asset.ticker.trim().uppercase(),
                            kind = PriceProviderKind.COINGECKO,
                            cryptoId = id
                        )
                    }
                }

                AssetCategory.STOCK -> {
                    val symbols = SymbolResolver.stockCandidates(asset.ticker, asset.priceSymbol)
                    if (symbols.isEmpty()) {
                        manual += ManualAsset(asset.id, labelOf(asset), ManualReason.MISSING_TICKER)
                    } else {
                        targets += PriceTarget(
                            assetId = asset.id,
                            assetName = asset.assetName,
                            ticker = asset.ticker.trim().uppercase(),
                            kind = PriceProviderKind.YAHOO_STOCK,
                            symbols = symbols
                        )
                    }
                }

                AssetCategory.GOLD -> targets += PriceTarget(
                    assetId = asset.id,
                    assetName = asset.assetName,
                    ticker = asset.ticker.trim().uppercase(),
                    kind = PriceProviderKind.YAHOO_GOLD,
                    symbols = listOf(GOLD_SYMBOL)
                )

                AssetCategory.MUTUAL_FUND,
                AssetCategory.BOND,
                AssetCategory.OTHER ->
                    manual += ManualAsset(
                        asset.id,
                        labelOf(asset),
                        ManualReason.UNSUPPORTED_CATEGORY
                    )
            }
        }

        return RefreshPlan(targets = targets, manual = manual)
    }

    private fun labelOf(asset: Asset): String =
        if (asset.ticker.isBlank()) {
            asset.assetName
        } else {
            "${asset.assetName} (${asset.ticker})"
        }

    /** COMEX gold futures, quoted in USD per troy ounce. */
    const val GOLD_SYMBOL = "GC=F"
}
