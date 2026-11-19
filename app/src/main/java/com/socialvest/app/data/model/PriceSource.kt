package com.socialvest.app.data.model

/**
 * Where the stored `currentPrice` of an asset came from.
 *
 * Prices are always stored in IDR per unit (sen) so the portfolio math does not
 * care about the origin. This enum only exists so the UI can state, honestly,
 * whether a number was typed by the user or fetched from a public source.
 */
enum class PriceSource(val id: String) {
    MANUAL("manual"),
    COINGECKO("coingecko"),
    YAHOO("yahoo");

    val isAutomatic: Boolean get() = this != MANUAL

    companion object {
        fun fromId(id: String?): PriceSource =
            entries.firstOrNull { it.id == id } ?: MANUAL
    }
}
