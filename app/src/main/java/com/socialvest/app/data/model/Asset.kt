package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Aggregate view of one asset. Derived from [Transaction] documents:
 * `quantity`, `totalCost` and `realizedPl` are always recomputed, never typed by hand.
 * Monetary values are in sen (1/100 IDR); quantity is scaled by 1_000_000.
 */
data class Asset(
    val id: String = "",
    val userId: String = "",
    val portfolioId: String = "",
    val assetName: String = "",
    val ticker: String = "",
    val category: AssetCategory = AssetCategory.OTHER,
    val currency: String = CURRENCY_IDR,
    val quantity: Long = 0L,
    val totalCost: Long = 0L,
    val averageBuyPrice: Long = 0L,
    val currentPrice: Long = 0L,
    val realizedPl: Long = 0L,
    val priceSource: PriceSource = PriceSource.MANUAL,
    val priceSymbol: String = "",
    val priceUpdatedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val hasHoldings: Boolean get() = quantity > 0L

    /** True when the last price came from a public provider instead of typing. */
    val hasAutomaticPrice: Boolean get() = priceSource.isAutomatic

    companion object {
        const val COLLECTION = "assets"
        const val CURRENCY_IDR = "IDR"

        const val FIELD_ID = "id"
        const val FIELD_USER_ID = "userId"
        const val FIELD_PORTFOLIO_ID = "portfolioId"
        const val FIELD_NAME = "assetName"
        const val FIELD_TICKER = "ticker"
        const val FIELD_CATEGORY = "category"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_QUANTITY = "quantity"
        const val FIELD_TOTAL_COST = "totalCost"
        const val FIELD_AVERAGE_BUY_PRICE = "averageBuyPrice"
        const val FIELD_CURRENT_PRICE = "currentPrice"
        const val FIELD_REALIZED_PL = "realizedPl"
        const val FIELD_PRICE_SOURCE = "priceSource"
        const val FIELD_PRICE_SYMBOL = "priceSymbol"
        const val FIELD_PRICE_UPDATED_AT = "priceUpdatedAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        fun fromSnapshot(document: DocumentSnapshot): Asset = Asset(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            portfolioId = document.getString(FIELD_PORTFOLIO_ID).orEmpty(),
            assetName = document.getString(FIELD_NAME).orEmpty(),
            ticker = document.getString(FIELD_TICKER).orEmpty(),
            category = AssetCategory.fromId(document.getString(FIELD_CATEGORY)),
            currency = document.getString(FIELD_CURRENCY) ?: CURRENCY_IDR,
            quantity = (document.getLong(FIELD_QUANTITY) ?: 0L),
            totalCost = (document.getLong(FIELD_TOTAL_COST) ?: 0L),
            averageBuyPrice = (document.getLong(FIELD_AVERAGE_BUY_PRICE) ?: 0L),
            currentPrice = (document.getLong(FIELD_CURRENT_PRICE) ?: 0L),
            realizedPl = (document.getLong(FIELD_REALIZED_PL) ?: 0L),
            priceSource = PriceSource.fromId(document.getString(FIELD_PRICE_SOURCE)),
            priceSymbol = document.getString(FIELD_PRICE_SYMBOL).orEmpty(),
            priceUpdatedAt = document.getTimestamp(FIELD_PRICE_UPDATED_AT),
            createdAt = document.getTimestamp(FIELD_CREATED_AT),
            updatedAt = document.getTimestamp(FIELD_UPDATED_AT)
        )
    }
}
