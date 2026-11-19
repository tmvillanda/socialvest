package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.remote.PriceService
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.domain.PortfolioCalculator
import com.socialvest.app.domain.PortfolioSummary
import com.socialvest.app.domain.price.ManualAsset
import com.socialvest.app.domain.price.PriceFailure
import com.socialvest.app.domain.price.RefreshPlanner
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** One row of the asset list, with its derived numbers already computed. */
data class AssetRow(
    val asset: Asset,
    val marketValue: Long,
    val unrealizedPl: Long,
    val returnPercent: BigDecimal?
)

data class InvestUiState(
    val isLoading: Boolean = true,
    val rows: List<AssetRow> = emptyList(),
    val totalValue: Long = 0L,
    val totalCost: Long = 0L,
    val totalUnrealized: Long = 0L,
    val totalRealized: Long = 0L,
    val returnPercent: BigDecimal? = null,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshCompleted: Int = 0,
    val refreshTotal: Int = 0,
    val refreshSummary: PriceRefreshSummary? = null,
    val refreshErrorRes: Int? = null
)

/** One-shot result of "Refresh semua", shown as a dialog and then consumed. */
data class PriceRefreshSummary(
    val updatedCount: Int,
    val failures: List<PriceFailure>,
    val manual: List<ManualAsset>,
    val updatedAtMillis: Long
)

class InvestViewModel(
    private val repository: InvestmentRepository,
    private val priceService: PriceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvestUiState())
    val uiState: StateFlow<InvestUiState> = _uiState.asStateFlow()

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            repository.assetsFlow()
                .onEach { assets -> _uiState.update { it.copy(isLoading = false, errorRes = null).withAssets(assets) } }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun InvestUiState.withAssets(assets: List<Asset>): InvestUiState {
        val rows = assets.map { asset ->
            val holding = PortfolioCalculator.Holding(
                quantity = asset.quantity,
                totalCost = asset.totalCost,
                realizedPl = asset.realizedPl
            )
            AssetRow(
                asset = asset,
                marketValue = PortfolioCalculator.marketValue(holding, asset.currentPrice),
                unrealizedPl = PortfolioCalculator.unrealizedPl(holding, asset.currentPrice),
                returnPercent = PortfolioCalculator.returnPercent(holding, asset.currentPrice)
            )
        }

        val summary = PortfolioSummary.of(assets)

        return copy(
            rows = rows,
            totalValue = summary.totalValue,
            totalCost = summary.totalCost,
            totalUnrealized = summary.totalUnrealized,
            totalRealized = summary.totalRealized,
            returnPercent = summary.returnPercent
        )
    }

    /**
     * Refreshes every asset that has a supported public source. The plan is
     * built from the current list; assets that cannot be priced automatically
     * (mutual funds, bonds, missing tickers, over the per-run limit) are
     * reported as manual instead of being silently skipped.
     */
    fun refreshPrices() {
        val current = _uiState.value
        if (current.isRefreshing || current.isLoading) return

        val plan = RefreshPlanner.plan(current.rows.map { it.asset })
        if (plan.isEmpty) {
            _uiState.update {
                it.copy(
                    refreshSummary = PriceRefreshSummary(
                        updatedCount = 0,
                        failures = emptyList(),
                        manual = plan.manual,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    refreshCompleted = 0,
                    refreshTotal = plan.targetCount,
                    refreshSummary = null,
                    refreshErrorRes = null
                )
            }

            val outcome = runCatching {
                priceService.refresh(plan) { completed, total ->
                    _uiState.update {
                        it.copy(refreshCompleted = completed, refreshTotal = total)
                    }
                }
            }.getOrElse { throwable ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        refreshErrorRes = ErrorMapper.messageRes(throwable)
                    )
                }
                return@launch
            }

            var written = 0
            if (outcome.updates.isNotEmpty()) {
                val writeResult = runCatching {
                    repository.applyFetchedPrices(outcome.updates)
                }
                if (writeResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            refreshErrorRes = ErrorMapper.messageRes(
                                writeResult.exceptionOrNull() ?: IllegalStateException()
                            )
                        )
                    }
                    return@launch
                }
                written = writeResult.getOrDefault(0)
            }

            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    refreshCompleted = plan.targetCount,
                    refreshTotal = plan.targetCount,
                    refreshSummary = PriceRefreshSummary(
                        updatedCount = written,
                        failures = outcome.failures,
                        manual = plan.manual,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun consumeRefreshSummary() {
        _uiState.update { it.copy(refreshSummary = null) }
    }

    fun consumeRefreshError() {
        _uiState.update { it.copy(refreshErrorRes = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                InvestViewModel(app.investmentRepository, app.priceService)
            }
        }
    }
}
