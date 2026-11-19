package com.socialvest.app.utils

import androidx.annotation.ColorRes
import com.socialvest.app.R
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small display helpers shared by the Invest screens. */
object Display {

    private val idLocale: Locale = Locale.forLanguageTag("id-ID")

    fun rupiah(cents: Long): String = Money.format(cents)

    fun signedRupiah(cents: Long): String = Money.formatSigned(cents)

    fun percent(value: BigDecimal?): String? = Money.formatPercent(value)

    fun date(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", idLocale).format(Date(millis))

    fun dateTime(millis: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", idLocale).format(Date(millis))

    /** Clock time used by the Search quote labels. */
    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm", idLocale).format(Date(millis))

    /** Semantic colour: always paired with a `+`/`-` sign and a label in the UI. */
    @ColorRes
    fun plColorRes(value: Long): Int = when {
        value > 0L -> R.color.gain
        value < 0L -> R.color.loss
        else -> R.color.on_surface_variant
    }
}
