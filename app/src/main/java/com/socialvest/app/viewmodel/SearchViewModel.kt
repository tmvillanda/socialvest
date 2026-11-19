package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.remote.MarketQuote
import com.socialvest.app.data.remote.MarketQuoteService
import com.socialvest.app.domain.market.MarketCatalog
import com.socialvest.app.domain.market.MarketInstrument
import com.socialvest.app.domain.market.QuoteFetchPlanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One catalogue row plus its latest labelled quote, when one was requested. */
data class MarketRow(
    val instrument: MarketInstrument,
    val quote: MarketQuote? = null,
    val unavailable: Boolean = false
)

data class SearchRefreshSummary(
    val updated: Int,
    val failed: Int,
    /** Visible symbols still without a price after this refresh. */
    val pending: Int,
    val fetchedAt: Long,
    val stockTargets: Int,
    val batchSize: Int
)

data class SearchUiState(
    val query: String = "",
    val category: AssetCategory? = null,
    val rows: List<MarketRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val refreshCompleted: Int = 0,
    val refreshTotal: Int = 0,
    val pendingCount: Int = 0,
    val summary: SearchRefreshSummary? = null
)

/**
 * Search catalogue plus on-demand quotes.
 *
 * Quotes are fetched once when the screen is first opened and afterwards only
 * when the user presses "Perbarui harga". Changing the filter never triggers a
 * network call; it only reuses quotes that were already fetched.
 *
 * A refresh covers every visible symbol. Stocks are requested in small bursts
 * by [MarketQuoteService]; a manual press forces fresh data while the automatic
 * first load may reuse the shared cache.
 */
class SearchViewModel(
    private val quoteService: MarketQuoteService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val quotes = mutableMapOf<String, MarketQuote>()
    private val failures = mutableSetOf<String>()
    private var refreshJob: Job? = null

    init {
        quotes.putAll(quoteService.cachedQuotes())
        rebuildRows()
        refreshQuotes(forceRefresh = false)
    }

    fun setQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        rebuildRows()
    }

    fun setCategory(category: AssetCategory?) {
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category) }
        rebuildRows()
    }

    /**
     * Requests quotes for every visible symbol. Symbols without a price (never
     * fetched or previously failed) are requested first so a second press only
     * retries the gaps.
     *
     * @param forceRefresh `true` for the button: ignore cached quotes and fetch
     * fresh data. `false` for the automatic first load.
     */
    fun refreshQuotes(forceRefresh: Boolean = true) {
        if (refreshJob?.isActive == true) return

        val plan = buildPlan()
        if (plan.targets.isEmpty()) return

        _uiState.update {
            it.copy(
                isRefreshing = true,
                refreshCompleted = 0,
                refreshTotal = plan.targets.size,
                summary = null
            )
        }

        failures.removeAll(plan.targets.map { it.ticker }.toSet())

        refreshJob = viewModelScope.launch {
            val batch = quoteService.fetch(plan.targets, forceRefresh) { completed, _ ->
                _uiState.update { it.copy(refreshCompleted = completed) }
            }

            quotes.putAll(batch.quotes)
            failures.addAll(batch.failures.map { it.ticker })

            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    refreshCompleted = plan.targets.size,
                    summary = SearchRefreshSummary(
                        updated = batch.quotes.size,
                        failed = batch.failures.size,
                        pending = pendingCount(),
                        fetchedAt = batch.fetchedAt,
                        stockTargets = plan.stockTargetCount,
                        batchSize = QuoteFetchPlanner.STOCK_BATCH_SIZE
                    )
                )
            }
            rebuildRows()
        }
    }

    fun consumeSummary() {
        if (_uiState.value.summary == null) return
        _uiState.update { it.copy(summary = null) }
    }

    private fun buildPlan(): QuoteFetchPlanner.Plan {
        val state = _uiState.value
        return QuoteFetchPlanner.plan(
            visible = MarketCatalog.search(state.query, state.category),
            quotedTickers = quotes.keys.toSet(),
            failedTickers = failures.toSet()
        )
    }

    /** Visible symbols that currently have no quote at all. */
    private fun pendingCount(): Int {
        val state = _uiState.value
        return MarketCatalog.search(state.query, state.category).count { it.ticker !in quotes }
    }

    private fun rebuildRows() {
        val state = _uiState.value
        val rows = MarketCatalog.search(state.query, state.category).map { instrument ->
            val quote = quotes[instrument.ticker]
            MarketRow(
                instrument = instrument,
                quote = quote,
                unavailable = quote == null && instrument.ticker in failures
            )
        }
        _uiState.update {
            it.copy(rows = rows, pendingCount = rows.count { row -> row.quote == null })
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                SearchViewModel(app.marketQuoteService)
            }
        }
    }
}
