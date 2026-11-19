package com.socialvest.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.model.Portfolio
import com.socialvest.app.data.model.PriceSource
import com.socialvest.app.data.model.Transaction
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.domain.PortfolioCalculator
import com.socialvest.app.domain.price.FetchedPrice
import com.socialvest.app.domain.toEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Raised when a SELL (or a history change) would exceed the units actually held. */
class OversellException(
    val available: Long,
    val requested: Long
) : IllegalStateException("Jumlah jual melebihi kepemilikan.")

/** Input for creating an asset, with an optional first BUY recorded in the same batch. */
data class NewAssetInput(
    val assetName: String,
    val ticker: String,
    val category: AssetCategory,
    val currentPrice: Long
)

data class OpeningBuyInput(
    val quantity: Long,
    val pricePerUnit: Long,
    val fee: Long
)

data class NewTransactionInput(
    val type: TransactionType,
    val quantity: Long,
    val pricePerUnit: Long,
    val fee: Long,
    val transactionDate: Date,
    val notes: String
)

/**
 * All reads/writes for the private Invest data: `portfolios`, `assets` and
 * `transactions`.
 *
 * Invariants:
 * - `transactions` are the source of truth; the `assets` document only caches
 *   the aggregate produced by [PortfolioCalculator.replay].
 * - A transaction and its recomputed aggregate are written in one
 *   [com.google.firebase.firestore.WriteBatch], so P/L can never disagree with
 *   the transaction list.
 * - Queries always filter by `userId`, which both matches the security rules and
 *   keeps reads private.
 */
class InvestmentRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val portfolios get() = firestore.collection(Portfolio.COLLECTION)
    private val assets get() = firestore.collection(Asset.COLLECTION)
    private val transactions get() = firestore.collection(Transaction.COLLECTION)

    fun currentUserId(): String? = auth.currentUser?.uid

    // ---------------- reads ----------------

    /** All assets of the signed-in user. Ordered client-side to avoid an index. */
    fun assetsFlow(): Flow<List<Asset>> = callbackFlow {
        val userId = currentUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = assets
            .whereEqualTo(Asset.FIELD_USER_ID, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.map(Asset::fromSnapshot)
                    ?.sortedBy { it.assetName.lowercase() }
                    .orEmpty()
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    fun assetFlow(assetId: String): Flow<Asset?> = callbackFlow {
        val registration = assets.document(assetId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.let(Asset::fromSnapshot))
            }
        awaitClose { registration.remove() }
    }

    /** Transactions of one asset, newest first for display. */
    fun transactionsFlow(assetId: String): Flow<List<Transaction>> = callbackFlow {
        val userId = currentUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = transactions
            .whereEqualTo(Transaction.FIELD_USER_ID, userId)
            .whereEqualTo(Transaction.FIELD_ASSET_ID, assetId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.map(Transaction::fromSnapshot)
                    ?.sortedByDescending { it.dateMillis() }
                    .orEmpty()
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    private suspend fun loadTransactions(userId: String, assetId: String): List<Transaction> =
        transactions
            .whereEqualTo(Transaction.FIELD_USER_ID, userId)
            .whereEqualTo(Transaction.FIELD_ASSET_ID, assetId)
            .get()
            .await()
            .documents
            .map(Transaction::fromSnapshot)

    // ---------------- asset writes ----------------

    /**
     * Creates the asset and, when [openingBuy] is provided, its first BUY in the
     * same batch so the aggregate already reflects the purchase.
     */
    suspend fun createAsset(
        input: NewAssetInput,
        openingBuy: OpeningBuyInput?
    ): String {
        val userId = requireUserId()
        val portfolioId = ensurePortfolio(userId)

        val assetRef = assets.document()
        val holding = openingBuy?.let {
            PortfolioCalculator.applyBuy(
                PortfolioCalculator.Holding(),
                it.quantity,
                it.pricePerUnit,
                it.fee
            )
        } ?: PortfolioCalculator.Holding()

        val batch = firestore.batch()
        batch.set(
            assetRef,
            buildMap<String, Any> {
                put(Asset.FIELD_USER_ID, userId)
                put(Asset.FIELD_PORTFOLIO_ID, portfolioId)
                put(Asset.FIELD_NAME, input.assetName.trim())
                put(Asset.FIELD_TICKER, input.ticker.trim().uppercase())
                put(Asset.FIELD_CATEGORY, input.category.id)
                put(Asset.FIELD_CURRENCY, Asset.CURRENCY_IDR)
                put(Asset.FIELD_CURRENT_PRICE, input.currentPrice)
                put(Asset.FIELD_PRICE_SOURCE, PriceSource.MANUAL.id)
                put(Asset.FIELD_PRICE_SYMBOL, "")
                putAll(aggregateFields(holding))
                put(Asset.FIELD_CREATED_AT, FieldValue.serverTimestamp())
            }
        )

        if (openingBuy != null && openingBuy.quantity > 0L) {
            val transactionRef = transactions.document()
            batch.set(transactionRef, transactionFields(userId, assetRef.id, TransactionType.BUY, openingBuy.quantity, openingBuy.pricePerUnit, openingBuy.fee, Date(), ""))
        }

        batch.commit().await()
        return assetRef.id
    }

    /** Updates the editable descriptive fields. The price lives on the detail screen. */
    suspend fun updateAsset(
        assetId: String,
        assetName: String,
        ticker: String,
        category: AssetCategory
    ) {
        val userId = requireUserId()
        val assetRef = assets.document(assetId)
        val snapshot = assetRef.get().await()
        if (!snapshot.exists()) throw IllegalStateException("Aset tidak ditemukan.")
        if (snapshot.getString(Asset.FIELD_USER_ID) != userId) {
            throw IllegalStateException("Aset bukan milik akun ini.")
        }

        val previousTicker = snapshot.getString(Asset.FIELD_TICKER).orEmpty().trim().uppercase()
        val previousCategory = snapshot.getString(Asset.FIELD_CATEGORY).orEmpty()
        val newTicker = ticker.trim().uppercase()
        val identityChanged = previousTicker != newTicker || previousCategory != category.id

        val fields = mutableMapOf<String, Any>(
            Asset.FIELD_NAME to assetName.trim(),
            Asset.FIELD_TICKER to newTicker,
            Asset.FIELD_CATEGORY to category.id,
            Asset.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )

        // A different ticker (or a category that no longer supports automatic
        // quotes) means the stored price can no longer be attributed to the
        // old symbol. Fall back to "manual" instead of claiming a stale quote.
        if (identityChanged) {
            fields[Asset.FIELD_PRICE_SOURCE] = PriceSource.MANUAL.id
            fields[Asset.FIELD_PRICE_SYMBOL] = ""
            fields[Asset.FIELD_PRICE_UPDATED_AT] = FieldValue.serverTimestamp()
        }

        assetRef.update(fields).await()
    }

    /** Manual price refresh, only reachable from the asset detail screen. */
    suspend fun updateCurrentPrice(assetId: String, price: Long) {
        requireUserId()
        assets.document(assetId).update(
            mapOf(
                Asset.FIELD_CURRENT_PRICE to price,
                Asset.FIELD_PRICE_SOURCE to PriceSource.MANUAL.id,
                Asset.FIELD_PRICE_SYMBOL to "",
                Asset.FIELD_PRICE_UPDATED_AT to FieldValue.serverTimestamp(),
                Asset.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Writes the prices obtained by the automatic refresh in one batch.
     *
     * Only assets that still belong to the signed-in user are written: an asset
     * deleted while the network calls were running must not fail the whole
     * batch. Prices that failed to resolve are simply absent from [fetched] and
     * therefore keep their previous value.
     */
    suspend fun applyFetchedPrices(fetched: List<FetchedPrice>): Int {
        if (fetched.isEmpty()) return 0
        val userId = requireUserId()

        val ownedIds = assets
            .whereEqualTo(Asset.FIELD_USER_ID, userId)
            .get()
            .await()
            .documents
            .map { it.id }
            .toSet()
        val writable = fetched.filter { it.assetId in ownedIds }
        if (writable.isEmpty()) return 0

        val batch = firestore.batch()
        writable.forEach { update ->
            batch.update(
                assets.document(update.assetId),
                mapOf(
                    Asset.FIELD_CURRENT_PRICE to update.price,
                    Asset.FIELD_PRICE_SOURCE to update.source.id,
                    Asset.FIELD_PRICE_SYMBOL to update.symbol,
                    Asset.FIELD_PRICE_UPDATED_AT to FieldValue.serverTimestamp(),
                    Asset.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        return writable.size
    }

    /**
     * Cascading delete: every transaction of the asset is removed together with
     * the asset document. The portfolio is kept because it is just a container.
     */
    suspend fun deleteAsset(assetId: String) {
        val userId = requireUserId()
        val assetRef = assets.document(assetId)
        val related = loadTransactions(userId, assetId)

        val batch = firestore.batch()
        related.forEach { transaction -> batch.delete(transactions.document(transaction.id)) }
        batch.delete(assetRef)
        batch.commit().await()
    }

    // ---------------- transaction writes ----------------

    /**
     * Records a BUY/SELL after replaying the whole history (including the new
     * entry) so the aggregate and the SELL limit are always consistent.
     */
    suspend fun addTransaction(assetId: String, input: NewTransactionInput) {
        val userId = requireUserId()
        val assetRef = assets.document(assetId)
        val assetSnapshot = assetRef.get().await()
        if (!assetSnapshot.exists()) throw IllegalStateException("Aset tidak ditemukan.")
        if (assetSnapshot.getString(Asset.FIELD_USER_ID) != userId) {
            throw IllegalStateException("Aset bukan milik akun ini.")
        }

        val existing = loadTransactions(userId, assetId)
        val transactionRef = transactions.document()
        val candidate = Transaction(
            id = transactionRef.id,
            userId = userId,
            assetId = assetId,
            type = input.type,
            quantity = input.quantity,
            pricePerUnit = input.pricePerUnit,
            fee = input.fee,
            transactionDate = Timestamp(input.transactionDate),
            // Client timestamp is only used to order same-day entries during the
            // replay; the stored document still uses serverTimestamp().
            createdAt = Timestamp(Date()),
            notes = input.notes.trim()
        )

        val holding = replayOrThrow(existing, candidate)

        val batch = firestore.batch()
        batch.set(
            transactionRef,
            transactionFields(
                userId = userId,
                assetId = assetId,
                type = input.type,
                quantity = input.quantity,
                pricePerUnit = input.pricePerUnit,
                fee = input.fee,
                date = input.transactionDate,
                notes = input.notes.trim()
            )
        )
        batch.update(assetRef, aggregateFields(holding))
        batch.commit().await()
    }

    /** Deletes a transaction and recomputes the aggregate from the rest. */
    suspend fun deleteTransaction(assetId: String, transactionId: String) {
        val userId = requireUserId()
        val assetRef = assets.document(assetId)
        val existing = loadTransactions(userId, assetId)
        if (existing.none { it.id == transactionId }) {
            throw IllegalStateException("Transaksi tidak ditemukan.")
        }

        val remaining = existing.filterNot { it.id == transactionId }
        val holding = when (val result = PortfolioCalculator.replay(remaining.map(Transaction::toEntry))) {
            is PortfolioCalculator.ReplayResult.Success -> result.holding
            is PortfolioCalculator.ReplayResult.Oversell -> throw OversellException(
                available = result.available,
                requested = result.requested
            )
        }

        val batch = firestore.batch()
        batch.delete(transactions.document(transactionId))
        batch.update(assetRef, aggregateFields(holding))
        batch.commit().await()
    }

    // ---------------- helpers ----------------

    private suspend fun ensurePortfolio(userId: String): String {
        // Query instead of reading a fixed id: a `get` on a missing document
        // cannot satisfy `resource.data.userId == uid()` in the rules.
        val existing = portfolios
            .whereEqualTo(Portfolio.FIELD_USER_ID, userId)
            .limit(1)
            .get()
            .await()
        val existingId = existing.documents.firstOrNull()?.id
        if (existingId != null) return existingId

        // Deterministic id keeps the single-portfolio rule idempotent.
        val portfolioRef = portfolios.document(userId)
        portfolioRef.set(
            mapOf(
                Portfolio.FIELD_USER_ID to userId,
                Portfolio.FIELD_NAME to DEFAULT_PORTFOLIO_NAME,
                Portfolio.FIELD_CURRENCY to Asset.CURRENCY_IDR,
                Portfolio.FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
        return portfolioRef.id
    }

    private fun replayOrThrow(
        existing: List<Transaction>,
        candidate: Transaction
    ): PortfolioCalculator.Holding {
        val entries = existing.map(Transaction::toEntry) + candidate.toEntry()
        return when (val result = PortfolioCalculator.replay(entries)) {
            is PortfolioCalculator.ReplayResult.Success -> result.holding
            is PortfolioCalculator.ReplayResult.Oversell -> throw OversellException(
                available = result.available,
                requested = result.requested
            )
        }
    }

    private fun aggregateFields(holding: PortfolioCalculator.Holding): Map<String, Any> = mapOf(
        Asset.FIELD_QUANTITY to holding.quantity,
        Asset.FIELD_TOTAL_COST to holding.totalCost,
        Asset.FIELD_AVERAGE_BUY_PRICE to holding.averageBuyPrice,
        Asset.FIELD_REALIZED_PL to holding.realizedPl,
        Asset.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
    )

    private fun transactionFields(
        userId: String,
        assetId: String,
        type: TransactionType,
        quantity: Long,
        pricePerUnit: Long,
        fee: Long,
        date: Date,
        notes: String
    ): Map<String, Any> = mapOf(
        Transaction.FIELD_USER_ID to userId,
        Transaction.FIELD_ASSET_ID to assetId,
        Transaction.FIELD_TYPE to type.id,
        Transaction.FIELD_QUANTITY to quantity,
        Transaction.FIELD_PRICE_PER_UNIT to pricePerUnit,
        Transaction.FIELD_FEE to fee,
        Transaction.FIELD_TRANSACTION_DATE to Timestamp(date),
        Transaction.FIELD_NOTES to notes,
        Transaction.FIELD_CREATED_AT to FieldValue.serverTimestamp()
    )

    private fun requireUserId(): String =
        currentUserId() ?: throw IllegalStateException("Sesi berakhir. Silakan masuk kembali.")

    companion object {
        const val DEFAULT_PORTFOLIO_NAME = "My Investment"
    }
}

/** Ordering key shared by the UI and the calculator. */
fun Transaction.dateMillis(): Long = transactionDate?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L
