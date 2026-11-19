package com.socialvest.app.ui.invest

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.databinding.FragmentInvestBinding
import com.socialvest.app.domain.price.ManualReason
import com.socialvest.app.domain.price.PriceFailureReason
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.viewmodel.InvestUiState
import com.socialvest.app.viewmodel.InvestViewModel
import com.socialvest.app.viewmodel.PriceRefreshSummary
import kotlinx.coroutines.launch

/**
 * Phase 3 Invest tab: portfolio summary plus the asset list. Numbers are
 * derived from transactions and the last stored price, which is either typed
 * by the user or fetched from a labelled public source by "Refresh semua".
 */
class InvestFragment : Fragment() {

    private var _binding: FragmentInvestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InvestViewModel by viewModels { InvestViewModel.Factory }
    private var assetAdapter: AssetAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AssetAdapter { assetId ->
            startActivity(AssetDetailActivity.intent(requireContext(), assetId))
        }
        assetAdapter = adapter
        binding.listAssets.layoutManager = LinearLayoutManager(requireContext())
        binding.listAssets.adapter = adapter

        binding.fabAddAsset.setOnClickListener {
            startActivity(Intent(requireContext(), AssetFormActivity::class.java))
        }

        binding.buttonRefreshPrices.setOnClickListener { confirmRefresh() }

        observeState()
    }

    override fun onDestroyView() {
        binding.listAssets.adapter = null
        assetAdapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: InvestUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressInvest.isVisible = state.isLoading

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        val hasAssets = state.rows.isNotEmpty()
        binding.cardSummary.isVisible = hasAssets
        binding.listAssets.isVisible = hasAssets
        binding.groupEmpty.isVisible = !hasAssets && !state.isLoading
        binding.textAssetCount.isVisible = hasAssets
        binding.textAssetCount.text = getString(R.string.invest_asset_count, state.rows.size)

        binding.buttonRefreshPrices.isVisible = hasAssets
        binding.buttonRefreshPrices.isEnabled =
            hasAssets && !state.isRefreshing && !state.isLoading
        binding.progressRefresh.isVisible = state.isRefreshing
        binding.textRefreshStatus.isVisible = state.isRefreshing
        if (state.isRefreshing) {
            binding.progressRefresh.max = state.refreshTotal.coerceAtLeast(1)
            binding.progressRefresh.setProgressCompat(state.refreshCompleted, true)
            binding.textRefreshStatus.text = getString(
                R.string.invest_refresh_progress,
                state.refreshCompleted,
                state.refreshTotal
            )
        }

        assetAdapter?.submitList(state.rows)

        state.refreshSummary?.let { summary ->
            showRefreshSummary(summary)
            viewModel.consumeRefreshSummary()
        }
        state.refreshErrorRes?.let { messageRes ->
            toast(messageRes)
            viewModel.consumeRefreshError()
        }

        if (!hasAssets) return

        binding.textTotalValue.text = Display.rupiah(state.totalValue)
        binding.textTotalCost.text = Display.rupiah(state.totalCost)
        binding.textUnrealized.text = Display.signedRupiah(state.totalUnrealized)
        binding.textUnrealized.setTextColor(
            ContextCompat.getColor(requireContext(), Display.plColorRes(state.totalUnrealized))
        )

        val percent = Display.percent(state.returnPercent)
        binding.textReturn.text = getString(
            R.string.invest_return_format,
            percent ?: getString(R.string.value_undefined)
        )

        binding.textRealized.text = getString(
            R.string.invest_realized_format,
            Display.signedRupiah(state.totalRealized)
        )
    }

    private fun confirmRefresh() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invest_refresh_confirm_title)
            .setMessage(R.string.invest_refresh_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.invest_refresh_confirm_action) { _, _ ->
                viewModel.refreshPrices()
            }
            .show()
    }

    private fun showRefreshSummary(summary: PriceRefreshSummary) {
        val message = buildString {
            append(
                getString(
                    R.string.invest_refresh_summary,
                    summary.updatedCount,
                    summary.failures.size,
                    summary.manual.size
                )
            )
            append('\n')
            append(
                getString(
                    R.string.invest_refresh_updated_at,
                    Display.dateTime(summary.updatedAtMillis)
                )
            )

            if (summary.updatedCount == 0 &&
                summary.failures.isEmpty() &&
                summary.manual.isNotEmpty()
            ) {
                append("\n\n")
                append(getString(R.string.invest_refresh_none_supported))
            }

            if (summary.failures.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.invest_refresh_failed_header))
                summary.failures.take(MAX_SUMMARY_LINES).forEach { failure ->
                    append('\n')
                    append(
                        getString(
                            R.string.invest_refresh_line,
                            failure.label,
                            failureText(failure.reason)
                        )
                    )
                }
                val hidden = summary.failures.size - MAX_SUMMARY_LINES
                if (hidden > 0) {
                    append('\n')
                    append(getString(R.string.invest_refresh_more, hidden))
                }
            }

            if (summary.manual.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.invest_refresh_manual_header))
                summary.manual.take(MAX_SUMMARY_LINES).forEach { manual ->
                    append('\n')
                    append(
                        getString(
                            R.string.invest_refresh_line,
                            manual.label,
                            manualText(manual.reason)
                        )
                    )
                }
                val hidden = summary.manual.size - MAX_SUMMARY_LINES
                if (hidden > 0) {
                    append('\n')
                    append(getString(R.string.invest_refresh_more, hidden))
                }
            }

            append("\n\n")
            append(getString(R.string.invest_refresh_attribution))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invest_refresh_result_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun failureText(reason: PriceFailureReason): String = getString(
        when (reason) {
            PriceFailureReason.SYMBOL_NOT_FOUND -> R.string.invest_refresh_failure_not_found
            PriceFailureReason.NO_DATA -> R.string.invest_refresh_failure_no_data
            PriceFailureReason.NETWORK -> R.string.invest_refresh_failure_network
            PriceFailureReason.RATE_LIMITED -> R.string.invest_refresh_failure_rate_limited
            PriceFailureReason.UNSUPPORTED_CURRENCY -> R.string.invest_refresh_failure_currency
            PriceFailureReason.INVALID_RESPONSE -> R.string.invest_refresh_failure_invalid
            PriceFailureReason.PROVIDER_ERROR -> R.string.invest_refresh_failure_provider
        }
    )

    private fun manualText(reason: ManualReason): String = getString(
        when (reason) {
            ManualReason.UNSUPPORTED_CATEGORY -> R.string.invest_refresh_manual_unsupported
            ManualReason.MISSING_TICKER -> R.string.invest_refresh_manual_missing_ticker
            ManualReason.LIMIT_REACHED -> R.string.invest_refresh_manual_limit
        }
    )

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        requireActivity().finish()
    }

    companion object {
        /** Keeps the result dialog readable when many assets were refreshed. */
        private const val MAX_SUMMARY_LINES = 6
    }
}
