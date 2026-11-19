package com.socialvest.app.ui.invest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.data.model.Asset
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.model.PriceSource
import com.socialvest.app.data.model.Transaction
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.databinding.ActivityAssetDetailBinding
import com.socialvest.app.databinding.DialogPriceBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.AssetDetailEvent
import com.socialvest.app.viewmodel.AssetDetailUiState
import com.socialvest.app.viewmodel.AssetDetailViewModel
import kotlinx.coroutines.launch

/**
 * Asset detail: manual price, holdings/P&L derived from transactions, transaction
 * history, and the entry points for BUY/SELL/edit/delete.
 */
class AssetDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssetDetailBinding
    private val assetId: String by lazy { intent.getStringExtra(EXTRA_ASSET_ID).orEmpty() }
    private val viewModel: AssetDetailViewModel by viewModels {
        AssetDetailViewModel.factory(assetId)
    }

    private var transactionAdapter: TransactionAdapter? = null
    private var priceDialog: AlertDialog? = null
    private var priceDialogBinding: DialogPriceBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (assetId.isBlank()) {
            finish()
            return
        }

        binding = ActivityAssetDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = TransactionAdapter { transaction -> confirmDeleteTransaction(transaction) }
        transactionAdapter = adapter
        binding.listTransactions.layoutManager = LinearLayoutManager(this)
        binding.listTransactions.adapter = adapter

        binding.buttonEdit.setOnClickListener {
            startActivity(AssetFormActivity.editIntent(this, assetId))
        }
        binding.buttonBuy.setOnClickListener {
            startActivity(TransactionActivity.intent(this, assetId, TransactionType.BUY))
        }
        binding.buttonSell.setOnClickListener {
            startActivity(TransactionActivity.intent(this, assetId, TransactionType.SELL))
        }
        binding.buttonUpdatePrice.setOnClickListener { showPriceDialog() }
        binding.buttonDeleteAsset.setOnClickListener { confirmDeleteAsset() }

        observeState()
    }

    override fun onDestroy() {
        priceDialog?.dismiss()
        priceDialog = null
        priceDialogBinding = null
        if (::binding.isInitialized) {
            binding.listTransactions.adapter = null
        }
        transactionAdapter = null
        super.onDestroy()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: AssetDetailUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressDetail.isVisible = state.isBusy

        val asset = state.asset
        binding.buttonBuy.isEnabled = asset != null && !state.isBusy
        binding.buttonSell.isEnabled = asset != null && !state.isBusy
        binding.buttonEdit.isEnabled = asset != null && !state.isBusy
        binding.buttonUpdatePrice.isEnabled = asset != null && !state.isBusy
        binding.buttonDeleteAsset.isEnabled = asset != null && !state.isBusy

        priceDialogBinding?.inputPrice?.error = state.priceErrorRes?.let(::getString)

        transactionAdapter?.submitList(state.transactions)
        binding.textNoTransactions.isVisible = state.transactions.isEmpty() && asset != null
        binding.listTransactions.isVisible = state.transactions.isNotEmpty()

        // Handle one-shot events before the null-asset guard: deleting the asset
        // clears it from the stream, and that event must still close the screen.
        handleEvent(state.event)

        if (asset == null) return

        binding.textName.text = asset.assetName
        binding.textMeta.text = listOf(asset.ticker, getString(asset.category.labelRes))
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        binding.textCurrentPrice.text = Money.format(asset.currentPrice)
        binding.textPriceLabel.setText(
            if (asset.hasAutomaticPrice) {
                R.string.asset_detail_current_price_auto
            } else {
                R.string.asset_detail_current_price
            }
        )
        binding.textPriceNote.text = priceNote(asset)
        binding.textQuantity.text = if (asset.quantity > 0L) {
            getString(R.string.invest_qty_format, Quantity.format(asset.quantity))
        } else {
            getString(R.string.value_undefined)
        }
        binding.textMarketValue.text = Display.rupiah(state.marketValue)
        binding.textAveragePrice.text = if (asset.quantity > 0L && asset.totalCost > 0L) {
            Money.format(asset.averageBuyPrice)
        } else {
            getString(R.string.value_undefined)
        }

        binding.textRealized.text = Display.signedRupiah(asset.realizedPl)
        binding.textRealized.setTextColor(
            ContextCompat.getColor(this, Display.plColorRes(asset.realizedPl))
        )

        binding.textUnrealized.text = Display.signedRupiah(state.unrealizedPl)
        binding.textUnrealized.setTextColor(
            ContextCompat.getColor(this, Display.plColorRes(state.unrealizedPl))
        )
        binding.textReturn.text = Display.percent(state.returnPercent)
            ?: getString(R.string.value_undefined)
    }

    private fun priceNote(asset: Asset): String {
        if (!asset.hasAutomaticPrice) {
            return getString(R.string.asset_detail_manual_note)
        }

        val sourceRes = when (asset.priceSource) {
            PriceSource.COINGECKO -> R.string.price_source_coingecko
            PriceSource.YAHOO -> R.string.price_source_yahoo
            PriceSource.MANUAL -> R.string.asset_detail_current_price
        }
        val symbol = asset.priceSymbol.ifBlank { asset.ticker }
        val updated = asset.priceUpdatedAt?.toDate()?.time
            ?.let(Display::dateTime)
            ?: getString(R.string.asset_detail_auto_time_unknown)

        val base = getString(
            R.string.asset_detail_auto_note,
            symbol,
            getString(sourceRes),
            updated
        )
        return if (asset.category == AssetCategory.GOLD) {
            base + "\n" + getString(R.string.asset_detail_gold_note)
        } else {
            base
        }
    }

    private fun handleEvent(event: AssetDetailEvent?) {
        if (event == null) return

        when (event) {
            AssetDetailEvent.PriceUpdated -> {
                priceDialog?.dismiss()
                toast(R.string.asset_detail_price_updated)
            }

            AssetDetailEvent.AssetDeleted -> {
                toast(R.string.asset_detail_deleted)
                setResult(RESULT_OK)
                finish()
            }

            AssetDetailEvent.TransactionDeleted -> toast(R.string.asset_detail_transaction_deleted)

            is AssetDetailEvent.Failure -> {
                toast(event.messageRes)
                if (event.messageRes == R.string.error_asset_not_found) finish()
            }
        }

        viewModel.consumeEvent()
    }

    private fun showPriceDialog() {
        val dialogBinding = DialogPriceBinding.inflate(layoutInflater)
        val currentPrice = viewModel.uiState.value.asset?.currentPrice ?: 0L
        // The layout already renders an "Rp" prefix, so only the number is set.
        dialogBinding.editPrice.setText(Money.formatForInput(currentPrice))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.asset_detail_price_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_button, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.updatePrice(dialogBinding.editPrice.text?.toString().orEmpty())
            }
        }
        dialog.setOnDismissListener {
            priceDialog = null
            priceDialogBinding = null
            viewModel.clearPriceError()
        }

        priceDialog = dialog
        priceDialogBinding = dialogBinding
        dialog.show()
    }

    private fun confirmDeleteAsset() {
        val asset = viewModel.uiState.value.asset ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.asset_delete_confirm_title)
            .setMessage(getString(R.string.asset_delete_confirm_message, asset.assetName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAsset() }
            .show()
    }

    private fun confirmDeleteTransaction(transaction: Transaction) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.transaction_delete_confirm_title)
            .setMessage(R.string.transaction_delete_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTransaction(transaction.id)
            }
            .show()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_ASSET_ID = "extra_asset_id"

        fun intent(context: Context, assetId: String): Intent =
            Intent(context, AssetDetailActivity::class.java)
                .putExtra(EXTRA_ASSET_ID, assetId)
    }
}
