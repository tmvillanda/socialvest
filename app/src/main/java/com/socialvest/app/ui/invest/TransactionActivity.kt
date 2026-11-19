package com.socialvest.app.ui.invest

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.databinding.ActivityTransactionBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.TransactionUiState
import com.socialvest.app.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

/** Records one BUY or SELL for an asset, with a preview of the resulting average. */
class TransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionBinding

    private val assetId: String by lazy { intent.getStringExtra(EXTRA_ASSET_ID).orEmpty() }
    private val initialType: TransactionType by lazy {
        TransactionType.fromId(intent.getStringExtra(EXTRA_TYPE))
    }
    private val viewModel: TransactionViewModel by viewModels {
        TransactionViewModel.factory(assetId, initialType)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (assetId.isBlank()) {
            finish()
            return
        }

        binding = ActivityTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.groupType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.button_buy -> viewModel.setType(TransactionType.BUY)
                R.id.button_sell -> viewModel.setType(TransactionType.SELL)
            }
        }

        binding.editQuantity.doAfterTextChanged { viewModel.setQuantity(it?.toString().orEmpty()) }
        binding.editPrice.doAfterTextChanged { viewModel.setPrice(it?.toString().orEmpty()) }
        binding.editFee.doAfterTextChanged { viewModel.setFee(it?.toString().orEmpty()) }
        binding.editNotes.doAfterTextChanged { viewModel.setNotes(it?.toString().orEmpty()) }
        binding.editDate.setOnClickListener { showDatePicker() }
        binding.inputDate.setOnClickListener { showDatePicker() }

        binding.buttonSave.setOnClickListener { viewModel.save() }

        observeState()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = viewModel.uiState.value.dateMillis
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.setDate(picked.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: TransactionUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.toolbar.setTitle(
            if (state.type == TransactionType.BUY) {
                R.string.transaction_buy_title
            } else {
                R.string.transaction_sell_title
            }
        )

        val desiredButton = if (state.type == TransactionType.BUY) R.id.button_buy else R.id.button_sell
        if (binding.groupType.checkedButtonId != desiredButton) {
            binding.groupType.check(desiredButton)
        }

        binding.textAsset.text = state.assetName
        binding.textAvailable.text = getString(
            R.string.transaction_available,
            Quantity.format(state.holding.quantity)
        )

        binding.editDate.setText(Display.date(state.dateMillis))

        binding.progressTransaction.isVisible = state.isSaving
        binding.buttonSave.isEnabled = !state.isSaving && !state.isLoading
        binding.groupType.isEnabled = !state.isSaving

        binding.inputQuantity.error = state.quantityErrorRes?.let { res ->
            state.oversellAvailable
                ?.let { available -> getString(res, Quantity.format(available)) }
                ?: getString(res)
        }
        binding.inputPrice.error = state.priceErrorRes?.let(::getString)
        binding.inputFee.error = state.feeErrorRes?.let(::getString)

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        renderPreview(state)

        if (state.isSaved) {
            binding.buttonSave.isEnabled = false
            Toast.makeText(this, R.string.transaction_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun renderPreview(state: TransactionUiState) {
        val average = state.previewAverage
        val realized = state.previewRealized

        when {
            state.type == TransactionType.BUY && average != null -> {
                binding.textPreview.isVisible = true
                binding.textPreview.setTextColor(
                    ContextCompat.getColor(this, R.color.on_secondary_container)
                )
                binding.textPreview.text = getString(
                    R.string.transaction_preview_average,
                    Money.format(average)
                )
            }

            state.type == TransactionType.SELL && realized != null -> {
                binding.textPreview.isVisible = true
                binding.textPreview.setTextColor(
                    ContextCompat.getColor(this, Display.plColorRes(realized))
                )
                binding.textPreview.text = getString(
                    R.string.transaction_preview_realized,
                    Display.signedRupiah(realized)
                )
            }

            else -> binding.textPreview.isVisible = false
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
        private const val EXTRA_TYPE = "extra_type"

        fun intent(context: Context, assetId: String, type: TransactionType): Intent =
            Intent(context, TransactionActivity::class.java)
                .putExtra(EXTRA_ASSET_ID, assetId)
                .putExtra(EXTRA_TYPE, type.id)
    }
}
