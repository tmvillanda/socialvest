package com.socialvest.app.domain

import com.socialvest.app.data.model.Transaction
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.utils.Quantity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Average-cost portfolio math, kept free of Android/Firebase types so it can be
 * unit tested on the JVM.
 *
 * Units: monetary values are `Long` sen (1/100 IDR), quantities are `Long`
 * scaled by 1_000_000. Every multiplication goes through [BigInteger] to avoid
 * overflow and floating point drift.
 *
 * Transactions are the source of truth; an `assets` document is only a cached
 * aggregate of the result of [replay].
 */
object PortfolioCalculator {

    /** A transaction reduced to the fields the math needs. */
    data class Entry(
        val id: String,
        val type: TransactionType,
        val quantity: Long,
        val pricePerUnit: Long,
        val fee: Long,
        val dateMillis: Long,
        val createdAtMillis: Long = 0L
    )

    /** Aggregate of one asset: current units, remaining cost and realised profit. */
    data class Holding(
        val quantity: Long = 0L,
        val totalCost: Long = 0L,
        val realizedPl: Long = 0L
    ) {
        val hasHoldings: Boolean get() = quantity > 0L

        /**
         * Cost per unit in sen, e.g. `1_100_000` for Rp11.000.
         * Returns 0 when there is nothing left to average.
         */
        val averageBuyPrice: Long
            get() = if (quantity <= 0L) {
                0L
            } else {
                BigInteger.valueOf(totalCost)
                    .multiply(BigInteger.valueOf(Quantity.SCALE))
                    .add(BigInteger.valueOf(quantity / 2L))
                    .divide(BigInteger.valueOf(quantity))
                    .longValueExact()
            }
    }

    sealed interface ReplayResult {
        data class Success(val holding: Holding) : ReplayResult

        /** Reached when the history sells more units than it owns at that moment. */
        data class Oversell(
            val entryId: String,
            val available: Long,
            val requested: Long
        ) : ReplayResult
    }

    /**
     * Applies a BUY. `fee` is added to the cost basis, so the average buy price
     * reflects what the user actually paid.
     */
    fun applyBuy(holding: Holding, quantity: Long, pricePerUnit: Long, fee: Long): Holding {
        val safeQuantity = quantity.coerceAtLeast(0L)
        val safeFee = fee.coerceAtLeast(0L)
        val spent = Quantity.multiplyToCents(safeQuantity, pricePerUnit) + safeFee
        return holding.copy(
            quantity = holding.quantity + safeQuantity,
            totalCost = holding.totalCost + spent
        )
    }

    /**
     * Applies a SELL using proportional cost removal.
     *
     * @return the new holding, or `null` when [quantity] exceeds what is held.
     */
    fun applySell(holding: Holding, quantity: Long, pricePerUnit: Long, fee: Long): Holding? {
        if (quantity <= 0L) return holding
        if (quantity > holding.quantity) return null

        val safeFee = fee.coerceAtLeast(0L)
        val costRemoved = costRemoved(holding.totalCost, quantity, holding.quantity)
        val proceeds = Quantity.multiplyToCents(quantity, pricePerUnit)
        val realized = proceeds - safeFee - costRemoved

        return Holding(
            quantity = holding.quantity - quantity,
            totalCost = holding.totalCost - costRemoved,
            realizedPl = holding.realizedPl + realized
        )
    }

    /**
     * Replays a full transaction history in chronological order (date, then
     * creation order, then id) and returns the resulting aggregate.
     */
    fun replay(entries: List<Entry>): ReplayResult {
        var holding = Holding()
        val ordered = entries.sortedWith(compareBy({ it.dateMillis }, { it.createdAtMillis }, { it.id }))

        for (entry in ordered) {
            holding = when (entry.type) {
                TransactionType.BUY -> applyBuy(
                    holding,
                    entry.quantity,
                    entry.pricePerUnit,
                    entry.fee
                )

                TransactionType.SELL -> applySell(
                    holding,
                    entry.quantity,
                    entry.pricePerUnit,
                    entry.fee
                ) ?: return ReplayResult.Oversell(
                    entryId = entry.id,
                    available = holding.quantity,
                    requested = entry.quantity
                )
            }
        }

        return ReplayResult.Success(holding)
    }

    /** Current market value of the holding using the manual price. */
    fun marketValue(holding: Holding, currentPrice: Long): Long =
        Quantity.multiplyToCents(holding.quantity, currentPrice.coerceAtLeast(0L))

    /** `market value - remaining cost`; negative means an unrealised loss. */
    fun unrealizedPl(holding: Holding, currentPrice: Long): Long =
        marketValue(holding, currentPrice) - holding.totalCost

    /** Unrealised return in percent, or `null` when there is no cost basis. */
    fun returnPercent(holding: Holding, currentPrice: Long): BigDecimal? {
        if (holding.totalCost <= 0L) return null
        return BigDecimal.valueOf(unrealizedPl(holding, currentPrice))
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(holding.totalCost), 4, RoundingMode.HALF_UP)
    }

    /**
     * Cost belonging to [sellQuantity] out of [heldQuantity], rounded half-up.
     * Selling everything always removes the entire cost so no residue is left.
     */
    fun costRemoved(totalCost: Long, sellQuantity: Long, heldQuantity: Long): Long {
        if (heldQuantity <= 0L || sellQuantity <= 0L || totalCost <= 0L) return 0L
        if (sellQuantity >= heldQuantity) return totalCost

        val numerator = BigInteger.valueOf(totalCost).multiply(BigInteger.valueOf(sellQuantity))
        val denominator = BigInteger.valueOf(heldQuantity)
        val half = denominator.divide(BigInteger.valueOf(2))
        return numerator.add(half).divide(denominator).longValueExact()
    }
}

/** Maps a Firestore transaction to the calculator input. */
fun Transaction.toEntry(): PortfolioCalculator.Entry = PortfolioCalculator.Entry(
    id = id,
    type = type,
    quantity = quantity,
    pricePerUnit = pricePerUnit,
    fee = fee,
    dateMillis = transactionDate?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L
)
