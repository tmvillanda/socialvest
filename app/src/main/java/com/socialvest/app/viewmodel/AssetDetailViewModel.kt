package com.socialvest.app.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.model.Transaction
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.data.repository.OversellException
import com.socialvest.app.domain.PortfolioCalculator
import com.socialvest.app.utils.ErrorMapper
import com.socialvest.app.utils.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** One-shot results that the Activity turns into a toast, dialog or navigation. */
sealed interface AssetDetailEvent {
    data object PriceUpdated : AssetDetailEvent
    data object AssetDeleted : AssetDetailEvent
    data object TransactionDeleted : AssetDetailEvent
    data class Failure(@StringRes val messageRes: Int) : AssetDetailEvent
}

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val asset: Asset? = null,
    val transactions: List<Transaction> = emptyList(),
    val marketValue: Long = 0L,
    val unrealizedPl: Long = 0L,
    val returnPercent: BigDecimal? = null,
    val priceErrorRes: Int? = null,
    val isSignedOut: Boolean = false,
    val event: AssetDetailEvent? = null
)

class AssetDetailViewModel(
    private val repository: InvestmentRepository,
    private val assetId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDetailUiState())
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    /** Set while the user's own delete is in flight, so the disappearing asset is not reported as an error. */
    private var deleteRequested = false

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            combine(
                repository.assetFlow(assetId),
                repository.transactionsFlow(assetId)
            ) { asset, transactions -> asset to transactions }
                .onEach { (asset, transactions) ->
                    _uiState.update { state -> state.withData(asset, transactions) }
                }
                .catch { throwable ->
                    // A listener on a deleted document comes back as
                    // PERMISSION_DENIED because the rules cannot evaluate a
                    // missing `resource`. Treat that as "asset is gone" so the
                    // screen closes instead of showing stale numbers.
                    val removed = throwable is FirebaseFirestoreException &&
                        throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isBusy = false,
                            asset = null,
                            event = when {
                                deleteRequested -> it.event
                                removed -> AssetDetailEvent.Failure(R.string.error_asset_not_found)
                                else -> AssetDetailEvent.Failure(ErrorMapper.messageRes(throwable))
                            }
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun AssetDetailUiState.withData(
        asset: Asset?,
        transactions: List<Transaction>
    ): AssetDetailUiState {
        if (asset == null) {
            return copy(
                isLoading = false,
                isBusy = false,
                asset = null,
                transactions = transactions,
                // The asset vanishing is expected while a confirmed delete is in
                // flight; only report it when nobody asked for the delete.
                event = event ?: if (deleteRequested) {
                    null
                } else {
                    AssetDetailEvent.Failure(R.string.error_asset_not_found)
                }
            )
        }

        val holding = PortfolioCalculator.Holding(
            quantity = asset.quantity,
            totalCost = asset.totalCost,
            realizedPl = asset.realizedPl
        )

        return copy(
            isLoading = false,
            asset = asset,
            transactions = transactions,
            marketValue = PortfolioCalculator.marketValue(holding, asset.currentPrice),
            unrealizedPl = PortfolioCalculator.unrealizedPl(holding, asset.currentPrice),
            returnPercent = PortfolioCalculator.returnPercent(holding, asset.currentPrice)
        )
    }

    fun updatePrice(input: String) {
        val cents = Money.parseToCents(input)
        val error = when {
            input.isBlank() -> R.string.error_price_required
            cents == null -> R.string.error_price_invalid
            cents < 0L -> R.string.error_price_positive
            else -> null
        }

        if (error != null || cents == null) {
            _uiState.update { it.copy(priceErrorRes = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, priceErrorRes = null) }
            runCatching { repository.updateCurrentPrice(assetId, cents) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, event = AssetDetailEvent.PriceUpdated) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            event = AssetDetailEvent.Failure(ErrorMapper.messageRes(throwable))
                        )
                    }
                }
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            deleteRequested = true
            _uiState.update { it.copy(isBusy = true) }
            runCatching { repository.deleteAsset(assetId) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, event = AssetDetailEvent.AssetDeleted) }
                }
                .onFailure { throwable ->
                    deleteRequested = false
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            event = AssetDetailEvent.Failure(ErrorMapper.messageRes(throwable))
                        )
                    }
                }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching { repository.deleteTransaction(assetId, transactionId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isBusy = false, event = AssetDetailEvent.TransactionDeleted)
                    }
                }
                .onFailure { throwable ->
                    val message = if (throwable is OversellException) {
                        R.string.transaction_delete_blocked
                    } else {
                        ErrorMapper.messageRes(throwable)
                    }
                    _uiState.update {
                        it.copy(isBusy = false, event = AssetDetailEvent.Failure(message))
                    }
                }
        }
    }

    fun clearPriceError() {
        _uiState.update { it.copy(priceErrorRes = null) }
    }

    fun consumeEvent() {
        _uiState.update { it.copy(event = null) }
    }

    companion object {
        fun factory(assetId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                AssetDetailViewModel(app.investmentRepository, assetId)
            }
        }
    }
}
