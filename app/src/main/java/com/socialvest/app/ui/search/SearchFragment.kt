package com.socialvest.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.socialvest.app.R
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.databinding.FragmentSearchBinding
import com.socialvest.app.ui.invest.AssetFormActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.viewmodel.MarketRow
import com.socialvest.app.viewmodel.SearchRefreshSummary
import com.socialvest.app.viewmodel.SearchUiState
import com.socialvest.app.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

/**
 * Phase 8 Search tab: a fixed catalogue of IDX stocks and crypto with a filter
 * and an explicitly manual price refresh. Selecting a symbol only prefills the
 * asset form; the purchase is still confirmed by the user.
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels { SearchViewModel.Factory }
    private var adapter: MarketAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val marketAdapter = MarketAdapter { row -> openAssetForm(row) }
        adapter = marketAdapter
        binding.listMarket.layoutManager = LinearLayoutManager(requireContext())
        binding.listMarket.adapter = marketAdapter

        binding.editSearch.doAfterTextChanged { text ->
            viewModel.setQuery(text?.toString().orEmpty())
        }

        binding.groupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setCategory(
                when (checkedIds.firstOrNull()) {
                    R.id.chip_stock -> AssetCategory.STOCK
                    R.id.chip_crypto -> AssetCategory.CRYPTO
                    else -> null
                }
            )
        }

        binding.buttonRefresh.setOnClickListener { viewModel.refreshQuotes(forceRefresh = true) }

        observeState()
    }

    override fun onDestroyView() {
        binding.listMarket.adapter = null
        adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SearchUiState) {
        val query = binding.editSearch.text?.toString().orEmpty()
        if (query != state.query) {
            binding.editSearch.setText(state.query)
            binding.editSearch.setSelection(state.query.length)
        }

        val checkedChip = when (state.category) {
            AssetCategory.STOCK -> R.id.chip_stock
            AssetCategory.CRYPTO -> R.id.chip_crypto
            else -> R.id.chip_all
        }
        if (binding.groupFilter.checkedChipId != checkedChip) {
            binding.groupFilter.check(checkedChip)
        }

        binding.textCount.text = getString(R.string.search_count, state.rows.size)

        val empty = state.rows.isEmpty()
        binding.listMarket.isVisible = !empty
        binding.textEmpty.isVisible = empty
        adapter?.submitList(state.rows)

        binding.buttonRefresh.isEnabled = !state.isRefreshing
        binding.progressQuotes.isVisible = state.isRefreshing
        if (state.isRefreshing) {
            binding.progressQuotes.max = state.refreshTotal.coerceAtLeast(1)
            binding.progressQuotes.setProgressCompat(state.refreshCompleted, true)
        }

        renderStatus(state)
    }

    private fun renderStatus(state: SearchUiState) {
        val text = when {
            state.isRefreshing -> getString(
                R.string.search_quote_progress,
                state.refreshCompleted,
                state.refreshTotal
            )

            state.summary != null -> summaryText(state.summary)
            state.pendingCount > 0 -> getString(R.string.search_quote_pending, state.pendingCount)
            else -> null
        }

        binding.textStatus.isVisible = text != null
        binding.textStatus.text = text
    }

    private fun summaryText(summary: SearchRefreshSummary): String = buildString {
        append(getString(R.string.search_quote_done, Display.time(summary.fetchedAt)))
        if (summary.failed > 0) {
            append('\n')
            append(getString(R.string.search_quote_failed, summary.failed))
        }
        if (summary.pending > 0) {
            append('\n')
            append(getString(R.string.search_quote_pending, summary.pending))
        }
        if (summary.stockTargets > summary.batchSize) {
            append('\n')
            append(getString(R.string.search_quote_batched, summary.batchSize))
        }
    }

    private fun openAssetForm(row: MarketRow) {
        val instrument = row.instrument
        startActivity(
            AssetFormActivity.marketIntent(
                context = requireContext(),
                assetName = instrument.name,
                ticker = instrument.ticker,
                category = instrument.category,
                priceSen = row.quote?.priceSen
            )
        )
    }
}
