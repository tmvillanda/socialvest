package com.socialvest.app.domain.price

import com.socialvest.app.data.model.PriceSource

/** A price that is ready to be written to Firestore. */
data class FetchedPrice(
    val assetId: String,
    val price: Long,
    val source: PriceSource,
    val symbol: String
)

data class PriceFailure(
    val label: String,
    val reason: PriceFailureReason
)

data class PriceRefreshOutcome(
    val updates: List<FetchedPrice>,
    val failures: List<PriceFailure>
)
