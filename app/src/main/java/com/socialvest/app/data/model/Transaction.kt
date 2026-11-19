package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

enum class TransactionType(val id: String) {
    BUY("BUY"),
    SELL("SELL");

    companion object {
        fun fromId(id: String?): TransactionType = if (id == SELL.id) SELL else BUY
    }
}

/** One manual BUY or SELL record. Monetary values are in sen, quantity scaled by 1e6. */
data class Transaction(
    val id: String = "",
    val userId: String = "",
    val assetId: String = "",
    val type: TransactionType = TransactionType.BUY,
    val quantity: Long = 0L,
    val pricePerUnit: Long = 0L,
    val fee: Long = 0L,
    val transactionDate: Timestamp? = null,
    val notes: String = "",
    val createdAt: Timestamp? = null
) {
    companion object {
        const val COLLECTION = "transactions"

        const val FIELD_ID = "id"
        const val FIELD_USER_ID = "userId"
        const val FIELD_ASSET_ID = "assetId"
        const val FIELD_TYPE = "type"
        const val FIELD_QUANTITY = "quantity"
        const val FIELD_PRICE_PER_UNIT = "pricePerUnit"
        const val FIELD_FEE = "fee"
        const val FIELD_TRANSACTION_DATE = "transactionDate"
        const val FIELD_NOTES = "notes"
        const val FIELD_CREATED_AT = "createdAt"

        fun fromSnapshot(document: DocumentSnapshot): Transaction = Transaction(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            assetId = document.getString(FIELD_ASSET_ID).orEmpty(),
            type = TransactionType.fromId(document.getString(FIELD_TYPE)),
            quantity = document.getLong(FIELD_QUANTITY) ?: 0L,
            pricePerUnit = document.getLong(FIELD_PRICE_PER_UNIT) ?: 0L,
            fee = document.getLong(FIELD_FEE) ?: 0L,
            transactionDate = document.getTimestamp(FIELD_TRANSACTION_DATE),
            notes = document.getString(FIELD_NOTES).orEmpty(),
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )
    }
}
