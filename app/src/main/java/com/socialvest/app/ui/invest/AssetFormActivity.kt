package com.socialvest.app.ui.invest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.databinding.ActivityAssetFormBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.AssetFormPrefill
import com.socialvest.app.viewmodel.AssetFormUiState
import com.socialvest.app.viewmodel.AssetFormViewModel
import kotlinx.coroutines.launch

/** Creates an asset (optionally with its first BUY) or edits its description. */
class AssetFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssetFormBinding
    private val assetId: String? by lazy { intent.getStringExtra(EXTRA_ASSET_ID) }
    private val prefill: AssetFormPrefill? by lazy {
        val name = intent.getStringExtra(EXTRA_PREFILL_NAME) ?: return@lazy null
        val ticker = intent.getStringExtra(EXTRA_PREFILL_TICKER) ?: return@lazy null
        AssetFormPrefill(
            assetName = name,
            ticker = ticker,
            category = AssetCategory.fromId(intent.getStringExtra(EXTRA_PREFILL_CATEGORY)),
            priceSen = intent.getLongExtra(EXTRA_PREFILL_PRICE_SEN, 0L).takeIf { it > 0L }
        )
    }
    private val viewModel: AssetFormViewModel by viewModels {
        AssetFormViewModel.factory(assetId, prefill)
    }

    private var formPrefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAssetFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val labels = AssetCategory.entries.map { getString(it.labelRes) }
        binding.dropdownCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        binding.dropdownCategory.setOnItemClickListener { _, _, position, _ ->
            AssetCategory.entries.getOrNull(position)?.let(viewModel::selectCategory)
        }

        binding.switchOpening.setOnCheckedChangeListener { _, checked ->
            viewModel.setOpeningEnabled(checked)
            // The opening price defaults to whatever the user typed as the
            // current price, so the aggregate is correct without retyping.
            if (checked && binding.editBuyPrice.text.isNullOrBlank()) {
                binding.editBuyPrice.setText(binding.editPrice.text?.toString().orEmpty())
            }
        }

        listOf(
            binding.editName,
            binding.editTicker,
            binding.editPrice,
            binding.editQuantity,
            binding.editBuyPrice,
            binding.editFee
        ).forEach { field ->
            field.doAfterTextChanged { viewModel.clearErrors() }
        }

        binding.buttonSave.setOnClickListener { submit() }

        observeState()
    }

    private fun submit() {
        if (binding.buttonSave.isEnabled.not()) return
        viewModel.save(
            assetName = binding.editName.text?.toString().orEmpty(),
            ticker = binding.editTicker.text?.toString().orEmpty(),
            currentPriceText = binding.editPrice.text?.toString().orEmpty(),
            openingQuantity = binding.editQuantity.text?.toString().orEmpty(),
            openingPrice = binding.editBuyPrice.text?.toString().orEmpty(),
            openingFee = binding.editFee.text?.toString().orEmpty()
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: AssetFormUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.toolbar.setTitle(
            if (state.isEditMode) R.string.asset_form_edit_title else R.string.asset_form_add_title
        )

        binding.progressForm.isVisible = state.isSaving
        binding.buttonSave.isEnabled = !state.isSaving && !state.isLoading

        // Edit mode: description only. The manual price lives on the detail screen.
        binding.inputPrice.isVisible = !state.isEditMode
        binding.switchOpening.isVisible = !state.isEditMode
        binding.textPriceNote.isVisible = state.isEditMode
        binding.groupOpening.isVisible = state.openingEnabled && !state.isEditMode

        if (binding.switchOpening.isChecked != state.openingEnabled) {
            binding.switchOpening.isChecked = state.openingEnabled
        }

        binding.dropdownCategory.setText(getString(state.category.labelRes), false)

        if (!state.isLoading && !formPrefilled) {
            binding.editName.setText(state.assetName)
            binding.editTicker.setText(state.ticker)
            if (state.currentPriceText.isNotBlank()) {
                binding.editPrice.setText(state.currentPriceText)
            }
            if (state.openingPrice.isNotBlank()) {
                binding.editBuyPrice.setText(state.openingPrice)
            }
            formPrefilled = true
        }

        binding.inputName.error = state.nameErrorRes?.let(::getString)
        binding.inputTicker.error = state.tickerErrorRes?.let(::getString)
        binding.inputPrice.error = state.priceErrorRes?.let(::getString)
        binding.inputQuantity.error = state.quantityErrorRes?.let(::getString)
        binding.inputBuyPrice.error = state.openingPriceErrorRes?.let(::getString)
        binding.inputFee.error = state.feeErrorRes?.let(::getString)

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        if (state.isSaved) {
            binding.buttonSave.isEnabled = false
            Toast.makeText(this, R.string.asset_form_saved, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
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
        private const val EXTRA_PREFILL_NAME = "extra_prefill_name"
        private const val EXTRA_PREFILL_TICKER = "extra_prefill_ticker"
        private const val EXTRA_PREFILL_CATEGORY = "extra_prefill_category"
        private const val EXTRA_PREFILL_PRICE_SEN = "extra_prefill_price_sen"

        fun editIntent(context: Context, assetId: String): Intent =
            Intent(context, AssetFormActivity::class.java)
                .putExtra(EXTRA_ASSET_ID, assetId)

        /**
         * Opens the create form with a catalogue symbol already filled in. A
         * [priceSen] is only attached when the user refreshed quotes; otherwise
         * the price field stays empty and must be typed manually.
         */
        fun marketIntent(
            context: Context,
            assetName: String,
            ticker: String,
            category: AssetCategory,
            priceSen: Long?
        ): Intent = Intent(context, AssetFormActivity::class.java)
            .putExtra(EXTRA_PREFILL_NAME, assetName)
            .putExtra(EXTRA_PREFILL_TICKER, ticker)
            .putExtra(EXTRA_PREFILL_CATEGORY, category.id)
            .putExtra(EXTRA_PREFILL_PRICE_SEN, priceSen ?: 0L)
    }
}
