package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/** One portfolio per user. Created automatically the first time an asset is added. */
data class Portfolio(
    val id: String = "",
    val userId: String = "",
    val portfolioName: String = "My Investment",
    val currency: String = Asset.CURRENCY_IDR,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val COLLECTION = "portfolios"
        const val FIELD_USER_ID = "userId"
        const val FIELD_NAME = "portfolioName"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_CREATED_AT = "createdAt"

        fun fromSnapshot(document: DocumentSnapshot): Portfolio = Portfolio(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            portfolioName = document.getString(FIELD_NAME) ?: "My Investment",
            currency = document.getString(FIELD_CURRENCY) ?: Asset.CURRENCY_IDR,
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )
    }
}
