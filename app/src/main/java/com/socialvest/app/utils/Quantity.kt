package com.socialvest.app.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Quantities are stored as a Long scaled by 1_000_000 so fractional units
 * (crypto, mutual funds) stay exact. `0,5` units is stored as `500_000`.
 */
object Quantity {

    const val SCALE = 1_000_000L
    private const val SCALE_DIGITS = 6

    fun parse(input: String): Long? {
        val cleaned = input
            .trim()
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "+") return null

        // Accept both `0,5` and `0.5`. A dot with three trailing digits is read
        // as a thousands separator so `1.000` stays one thousand units.
        val normalized = when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
            cleaned.count { it == '.' } == 1 -> {
                val integer = cleaned.substringBefore('.')
                val fraction = cleaned.substringAfter('.')
                val isThousands = fraction.length == 3 && integer.isNotEmpty() && integer != "0"
                if (isThousands) cleaned.replace(".", "") else cleaned
            }
            cleaned.contains('.') -> cleaned.replace(".", "")
            else -> cleaned
        }

        if (!normalized.matches(Regex("^\\d+(\\.\\d+)?$"))) return null

        val decimal = normalized.toBigDecimalOrNull() ?: return null
        if (decimal < BigDecimal.ZERO) return null

        val scaled = decimal.multiply(BigDecimal.valueOf(SCALE))
            .setScale(0, RoundingMode.DOWN)

        if (scaled > BigDecimal.valueOf(Long.MAX_VALUE)) return null
        return scaled.toLong()
    }

    fun format(quantity: Long): String {
        val negative = quantity < 0
        val absolute = if (negative) -quantity else quantity
        val whole = absolute / SCALE
        val fraction = absolute % SCALE
        if (fraction == 0L) {
            return if (negative) "-$whole" else whole.toString()
        }
        val fractionDigits = fraction.toString()
            .padStart(SCALE_DIGITS, '0')
            .trimEnd('0')
        val sign = if (negative) "-" else ""
        return "$sign$whole,$fractionDigits"
    }

    /** `quantity * unitPrice` rounded half-up, returned in sen. */
    fun multiplyToCents(quantity: Long, priceCents: Long): Long {
        val product = BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(priceCents))
        val scale = BigInteger.valueOf(SCALE)
        val half = scale.divide(BigInteger.valueOf(2))
        val rounded = product.add(half).divide(scale)
        return rounded.longValueExact()
    }

    /** True when [part] is greater than [whole] (both scaled the same way). */
    fun exceeds(part: Long, whole: Long): Boolean = part > whole
}
