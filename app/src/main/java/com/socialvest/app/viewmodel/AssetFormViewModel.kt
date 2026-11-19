package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.data.repository.NewAssetInput
import com.socialvest.app.data.repository.OpeningBuyInput
import com.socialvest.app.utils.ErrorMapper
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import com.socialvest.app.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Values copied from the Search catalogue. The user can still edit everything
 * before saving; the price is a snapshot, not a live quote.
 */
data class AssetFormPrefill(
    val assetName: String,
    val ticker: String,
    val category: AssetCategory,
    val priceSen: Long?
)

data class AssetFormUiState(    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val assetName: String = "",
    val ticker: String = "",
    val category: AssetCategory = AssetCategory.STOCK,
    val currentPriceText: String = "",
    val openingEnabled: Boolean = false,
    val openingQuantity: String = "",
    val openingPrice: String = "",
    val openingFee: String = "",
    val nameErrorRes: Int? = null,
    val tickerErrorRes: Int? = null,
    val priceErrorRes: Int? = null,
    val quantityErrorRes: Int? = null,
    val openingPriceErrorRes: Int? = null,
    val feeErrorRes: Int? = null,
    val errorRes: Int? = null,
    val isSaved: Boolean = false,
    val isSignedOut: Boolean = false
)

/**
 * Create/edit an asset. In create mode the optional opening BUY is recorded in
 * the same batch; in edit mode only the descriptive fields are editable because
 * the manual price is owned by the detail screen.
 */
class AssetFormViewModel(
    private val repository: InvestmentRepository,
    private val assetId: String?,
    prefill: AssetFormPrefill? = null
) : ViewModel() {

    private val prefillPriceText = prefill?.priceSen
        ?.takeIf { it > 0L }
        ?.let(Money::formatForInput)
        .orEmpty()

    private val _uiState = MutableStateFlow(
        AssetFormUiState(
            isEditMode = assetId != null,
            isLoading = assetId != null,
            assetName = prefill?.assetName.orEmpty(),
            ticker = prefill?.ticker.orEmpty(),
            category = prefill?.category ?: AssetCategory.STOCK,
            currentPriceText = prefillPriceText,
            openingEnabled = prefillPriceText.isNotBlank(),
            openingPrice = prefillPriceText
        )
    )
    val uiState: StateFlow<AssetFormUiState> = _uiState.asStateFlow()

    init {
        when {
            repository.currentUserId() == null ->
                _uiState.update { it.copy(isLoading = false, isSignedOut = true) }

            assetId == null -> _uiState.update { it.copy(isLoading = false) }

            else -> loadAsset(assetId)
        }
    }

    private fun loadAsset(id: String) {
        viewModelScope.launch {
            runCatching { repository.assetFlow(id).first() }
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
                                ticker = asset.ticker,
                                category = asset.category,
                                currentPriceText = Money.format(asset.currentPrice)
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
        }
    }

    fun selectCategory(category: AssetCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun setOpeningEnabled(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                openingEnabled = enabled,
                openingPrice = if (enabled && state.openingPrice.isBlank()) state.currentPriceText else state.openingPrice,
                errorRes = null
            )
        }
    }

    fun clearErrors() {
        _uiState.update {
            it.copy(
                nameErrorRes = null,
                tickerErrorRes = null,
                priceErrorRes = null,
                quantityErrorRes = null,
                openingPriceErrorRes = null,
                feeErrorRes = null,
                errorRes = null
            )
        }
    }

    fun save(
        assetName: String,
        ticker: String,
        currentPriceText: String,
        openingQuantity: String,
        openingPrice: String,
        openingFee: String
    ) {
        val nameError = Validators.assetNameError(assetName)
        val tickerError = Validators.tickerError(ticker)

        val isEditMode = _uiState.value.isEditMode
        val openingEnabled = _uiState.value.openingEnabled && !isEditMode

        val priceCents = if (isEditMode) {
            null
        } else {
            Money.parseToCents(currentPriceText)
        }
        val priceError = when {
            isEditMode -> null
            currentPriceText.isBlank() -> R.string.error_price_required
            priceCents == null -> R.string.error_price_invalid
            priceCents < 0L -> R.string.error_price_positive
            else -> null
        }

        var quantityError: Int? = null
        var openingPriceError: Int? = null
        var feeError: Int? = null
        var openingQuantityValue = 0L
        var openingPriceValue = 0L
        var openingFeeValue = 0L

        if (openingEnabled) {
            val quantity = Quantity.parse(openingQuantity)
            val price = Money.parseToCents(openingPrice)
            val fee = if (openingFee.isBlank()) 0L else Money.parseToCents(openingFee)

            quantityError = when {
                openingQuantity.isBlank() -> R.string.error_quantity_required
                quantity == null -> R.string.error_quantity_invalid
                quantity <= 0L -> R.string.error_quantity_positive
                else -> null
            }
            openingPriceError = when {
                openingPrice.isBlank() -> R.string.error_price_required
                price == null -> R.string.error_price_invalid
                price <= 0L -> R.string.error_price_positive
                else -> null
            }
            feeError = when {
                fee == null -> R.string.error_fee_invalid
                fee < 0L -> R.string.error_fee_invalid
                else -> null
            }

            if (quantityError == null) openingQuantityValue = quantity ?: 0L
            if (openingPriceError == null) openingPriceValue = price ?: 0L
            if (feeError == null) openingFeeValue = fee ?: 0L
        }

        if (nameError != null || tickerError != null || priceError != null ||
            quantityError != null || openingPriceError != null || feeError != null
        ) {
            _uiState.update {
                it.copy(
                    nameErrorRes = nameError,
                    tickerErrorRes = tickerError,
                    priceErrorRes = priceError,
                    quantityErrorRes = quantityError,
                    openingPriceErrorRes = openingPriceError,
                    feeErrorRes = feeError,
                    errorRes = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorRes = null) }

            val result = runCatching {
                if (isEditMode) {
                    val id = assetId ?: error("missing asset id")
                    repository.updateAsset(
                        assetId = id,
                        assetName = assetName,
                        ticker = ticker,
                        category = _uiState.value.category
                    )
                } else {
                    repository.createAsset(
                        input = NewAssetInput(
                            assetName = assetName,
                            ticker = ticker,
                            category = _uiState.value.category,
                            currentPrice = priceCents ?: 0L
                        ),
                        openingBuy = if (openingEnabled && openingQuantityValue > 0L) {
                            OpeningBuyInput(
                                quantity = openingQuantityValue,
                                pricePerUnit = openingPriceValue,
                                fee = openingFeeValue
                            )
                        } else {
                            null
                        }
                    )
                }
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSaving = false, errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
        }
    }

    companion object {
        fun factory(
            assetId: String?,
            prefill: AssetFormPrefill? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                AssetFormViewModel(app.investmentRepository, assetId, prefill)
            }
        }
    }
}
