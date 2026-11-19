package com.socialvest.app.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Money is stored as a Long in **sen** (1/100 IDR) so that currency math never
 * touches floating point.
 *
 * Parsing accepts Indonesian and keyboard-decimal input:
 * - `10.000` -> Rp10.000 (groups of three digits are thousands)
 * - `10.000,50` -> Rp10.000,50
 * - `10.5` -> Rp10,50 (a dot with one or two trailing digits is a decimal)
 * - `Rp 12.345,67` -> Rp12.345,67
 */
object Money {

    const val SCALE = 100L

    fun parseToCents(input: String): Long? {
        val cleaned = input
            .trim()
            .replace("Rp", "", ignoreCase = true)
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "+") return null

        val negative = cleaned.startsWith("-")
        val body = cleaned.removePrefix("-").removePrefix("+")
        if (body.isEmpty()) return null

        val normalized = normalizeSeparators(body) ?: return null
        val parts = normalized.split('.')
        if (parts.size > 2) return null

        val integerPart = parts[0]
        if (integerPart.isNotEmpty() && integerPart.any { !it.isDigit() }) return null

        val decimalPart = parts.getOrNull(1).orEmpty()
        if (decimalPart.any { !it.isDigit() }) return null
        if (decimalPart.length > 2) return null

        val cents = runCatching {
            val whole = if (integerPart.isEmpty()) BigInteger.ZERO else BigInteger(integerPart)
            val fraction = if (decimalPart.isEmpty()) 0L else decimalPart.padEnd(2, '0').toLong()
            whole.multiply(BigInteger.valueOf(SCALE)).add(BigInteger.valueOf(fraction))
                .let { if (negative) it.negate() else it }
                .longValueExact()
        }.getOrNull() ?: return null

        return cents
    }

    /**
     * Resolves the `.` ambiguity: with a comma present the dot is a thousands
     * separator; a lone dot followed by exactly three digits is also thousands,
     * otherwise it is a decimal separator.
     */
    private fun normalizeSeparators(body: String): String? {
        return when {
            body.contains(',') -> body.replace(".", "").replace(',', '.')
            body.count { it == '.' } == 1 -> {
                val integer = body.substringBefore('.')
                val fraction = body.substringAfter('.')
                val isThousands = fraction.length == 3 && integer.isNotEmpty() && integer != "0"
                if (isThousands) body.replace(".", "") else body
            }
            body.contains('.') -> body.replace(".", "")
            else -> body
        }
    }

    fun format(cents: Long): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents

        val whole = absolute / SCALE
        val fraction = absolute % SCALE
        val sign = if (negative) "-" else ""

        val grouped = groupThousands(whole)
        return if (fraction == 0L) {
            "${sign}Rp$grouped"
        } else {
            "${sign}Rp$grouped,${fraction.toString().padStart(2, '0')}"
        }
    }

    /** Formats a value with an explicit `+` for gains. */
    fun formatSigned(cents: Long): String = if (cents > 0) "+${format(cents)}" else format(cents)

    /**
     * Text for an editable price field that already shows an `Rp` prefix, so the
     * currency symbol is not printed twice. Rounds trip through the same parser
     * used on save.
     */
    fun formatForInput(cents: Long): String = format(cents).removePrefix("Rp")

    /** Percentage for display, e.g. `12,34%`. Returns `null` when undefined. */
    fun formatPercent(percent: BigDecimal?): String? =
        percent?.setScale(2, RoundingMode.HALF_UP)
            ?.toPlainString()
            ?.replace('.', ',')
            ?.let { "$it%" }

    fun percentOf(part: Long, whole: Long): BigDecimal? {
        if (whole <= 0L) return null
        return BigDecimal.valueOf(part)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP)
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val builder = StringBuilder()
        var counter = 0
        for (index in digits.indices.reversed()) {
            builder.append(digits[index])
            counter++
            if (counter % 3 == 0 && index != 0) builder.append('.')
        }
        return builder.reverse().toString()
    }
}
