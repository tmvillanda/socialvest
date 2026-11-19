package com.socialvest.app.domain

import com.socialvest.app.data.model.Asset
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Portfolio totals for the Home dashboard. Uses exactly the same math as the
 * Invest tab (market value - remaining cost), so both screens always agree.
 *
 * Only assets that still have units contribute a market value; a fully sold
 * asset keeps its realised P/L but adds nothing to the current value.
 */
data class PortfolioSummary(
    val hasAssets: Boolean = false,
    val totalValue: Long = 0L,
    val totalCost: Long = 0L,
    val totalUnrealized: Long = 0L,
    val totalRealized: Long = 0L,
    val returnPercent: BigDecimal? = null
) {
    companion object {
        fun of(assets: List<Asset>): PortfolioSummary {
            if (assets.isEmpty()) return PortfolioSummary()

            var value = 0L
            var cost = 0L
            var realized = 0L

            for (asset in assets) {
                realized += asset.realizedPl
                if (asset.quantity <= 0L) continue

                val holding = PortfolioCalculator.Holding(
                    quantity = asset.quantity,
                    totalCost = asset.totalCost,
                    realizedPl = asset.realizedPl
                )
                value += PortfolioCalculator.marketValue(holding, asset.currentPrice)
                cost += asset.totalCost
            }

            val unrealized = value - cost
            val percent = if (cost > 0L) {
                BigDecimal.valueOf(unrealized)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(cost), 4, RoundingMode.HALF_UP)
            } else {
                null
            }

            return PortfolioSummary(
                hasAssets = true,
                totalValue = value,
                totalCost = cost,
                totalUnrealized = unrealized,
                totalRealized = realized,
                returnPercent = percent
            )
        }
    }
}
