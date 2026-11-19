package com.socialvest.app.domain.price

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Precision-safe conversion from a provider quote to the app's money unit
 * (IDR sen). All arithmetic uses [BigDecimal]; no price ever passes through a
 * `Double`.
 */
object PriceMath {

    /** Troy ounce, the unit used by the world gold reference (`GC=F`). */
    val OUNCES_PER_GRAM: BigDecimal = BigDecimal("31.1034768")

    /**
     * Converts a currency amount (already in IDR) to sen, rounding half-up.
     * Returns `null` for zero, negative, or oversized values so callers never
     * store a meaningless price.
     */
    fun toSen(idr: BigDecimal): Long? {
        if (idr.signum() <= 0) return null
        return runCatching {
            idr.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()?.takeIf { it > 0L }
    }

    /** Converts a USD amount to IDR using the given USD/IDR rate. */
    fun usdToIdr(usd: BigDecimal, idrPerUsd: BigDecimal): BigDecimal {
        require(idrPerUsd.signum() > 0) { "Kurs USD/IDR tidak valid." }
        return usd.multiply(idrPerUsd)
    }

    /**
     * `GC=F` is quoted in USD per troy ounce. The app stores gold in IDR per
     * gram, so the quote is converted with the current USD/IDR rate.
     */
    fun goldOunceToGramIdr(usdPerOunce: BigDecimal, idrPerUsd: BigDecimal): BigDecimal =
        usdToIdr(usdPerOunce, idrPerUsd).divide(OUNCES_PER_GRAM, 8, RoundingMode.HALF_UP)
}
