package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.data.repository.NewTransactionInput
import com.socialvest.app.data.repository.OversellException
import com.socialvest.app.domain.PortfolioCalculator
import com.socialvest.app.utils.ErrorMapper
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class TransactionUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val type: TransactionType = TransactionType.BUY,
    val assetName: String = "",
    val holding: PortfolioCalculator.Holding = PortfolioCalculator.Holding(),
    val quantityText: String = "",
    val priceText: String = "",
    val feeText: String = "",
    val notes: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val previewAverage: Long? = null,
    val previewRealized: Long? = null,
    val quantityErrorRes: Int? = null,
    val oversellAvailable: Long? = null,
    val priceErrorRes: Int? = null,
    val feeErrorRes: Int? = null,
    val errorRes: Int? = null,
    val isSaved: Boolean = false,
    val isSignedOut: Boolean = false
)

/**
 * Records one BUY or SELL. The preview shown before saving uses the same
 * calculator as the repository, so what the user sees is what gets stored.
 */
class TransactionViewModel(
    private val repository: InvestmentRepository,
    private val assetId: String,
    initialType: TransactionType
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TransactionUiState(isLoading = true, type = initialType)
    )
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            viewModelScope.launch {
                runCatching { repository.assetFlow(assetId).first() }
                    .onSuccess { asset ->
                        if (asset == null) {
                            _uiState.update {
                                it.copy(isLoading = false, errorRes = R.string.error_asset_not_found)
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    assetName = asset.assetName,
                                    holding = PortfolioCalculator.Holding(
                                        quantity = asset.quantity,
                                        totalCost = asset.totalCost,
                                        realizedPl = asset.realizedPl
                                    )
                                )
                            }
                            recomputePreview()
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorRes = ErrorMapper.messageRes(throwable)
                            )
                        }
                    }
            }
        }
    }

    fun setType(type: TransactionType) {
        _uiState.update { it.copy(type = type, quantityErrorRes = null, oversellAvailable = null) }
        recomputePreview()
    }

    fun setQuantity(value: String) {
        _uiState.update { it.copy(quantityText = value, quantityErrorRes = null, oversellAvailable = null) }
        recomputePreview()
    }

    fun setPrice(value: String) {
        _uiState.update { it.copy(priceText = value, priceErrorRes = null) }
        recomputePreview()
    }

    fun setFee(value: String) {
        _uiState.update { it.copy(feeText = value, feeErrorRes = null) }
        recomputePreview()
    }

    fun setNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun setDate(millis: Long) {
        _uiState.update { it.copy(dateMillis = millis) }
    }

    fun clearErrors() {
        _uiState.update {
            it.copy(
                quantityErrorRes = null,
                oversellAvailable = null,
                priceErrorRes = null,
                feeErrorRes = null,
                errorRes = null
            )
        }
    }

    private fun recomputePreview() {
        val state = _uiState.value
        val quantity = Quantity.parse(state.quantityText)
        val price = Money.parseToCents(state.priceText)
        val fee = if (state.feeText.isBlank()) 0L else Money.parseToCents(state.feeText)

        if (quantity == null || quantity <= 0L || price == null || fee == null) {
            _uiState.update { it.copy(previewAverage = null, previewRealized = null) }
            return
        }

        when (state.type) {
            TransactionType.BUY -> {
                val updated = PortfolioCalculator.applyBuy(state.holding, quantity, price, fee)
                _uiState.update {
                    it.copy(previewAverage = updated.averageBuyPrice, previewRealized = null)
                }
            }

            TransactionType.SELL -> {
                val updated = PortfolioCalculator.applySell(state.holding, quantity, price, fee)
                _uiState.update {
                    it.copy(
                        previewAverage = state.holding.averageBuyPrice,
                        previewRealized = updated?.let { sell ->
                            sell.realizedPl - state.holding.realizedPl
                        }
                    )
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val quantity = Quantity.parse(state.quantityText)
        val price = Money.parseToCents(state.priceText)
        val fee = if (state.feeText.isBlank()) 0L else Money.parseToCents(state.feeText)

        val quantityError = when {
            state.quantityText.isBlank() -> R.string.error_quantity_required
            quantity == null -> R.string.error_quantity_invalid
            quantity <= 0L -> R.string.error_quantity_positive
            state.type == TransactionType.SELL && quantity > state.holding.quantity ->
                R.string.error_sell_exceeds

            else -> null
        }
        val priceError = when {
            state.priceText.isBlank() -> R.string.error_price_required
            price == null -> R.string.error_price_invalid
            price <= 0L -> R.string.error_price_positive
            else -> null
        }
        val feeError = if (fee == null || fee < 0L) R.string.error_fee_invalid else null

        if (quantityError != null || priceError != null || feeError != null) {
            _uiState.update {
                it.copy(
                    quantityErrorRes = quantityError,
                    oversellAvailable = if (quantityError == R.string.error_sell_exceeds) {
                        state.holding.quantity
                    } else {
                        null
                    },
                    priceErrorRes = priceError,
                    feeErrorRes = feeError,
                    errorRes = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorRes = null) }
            runCatching {
                repository.addTransaction(
                    assetId = assetId,
                    input = NewTransactionInput(
                        type = state.type,
                        quantity = quantity ?: 0L,
                        pricePerUnit = price ?: 0L,
                        fee = fee ?: 0L,
                        transactionDate = Date(state.dateMillis),
                        notes = state.notes
                    )
                )
            }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                }
                .onFailure { throwable ->
                    if (throwable is OversellException) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                quantityErrorRes = R.string.error_sell_exceeds,
                                oversellAvailable = throwable.available
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorRes = ErrorMapper.messageRes(throwable)
                            )
                        }
                    }
                }
        }
    }

    companion object {
        fun factory(assetId: String, initialType: TransactionType): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as SocialvestApp
                    TransactionViewModel(app.investmentRepository, assetId, initialType)
                }
            }
    }
}
