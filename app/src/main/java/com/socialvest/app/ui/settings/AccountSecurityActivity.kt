package com.socialvest.app.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityAccountSecurityBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.AccountSecurityUiState
import com.socialvest.app.viewmodel.AccountSecurityViewModel
import kotlinx.coroutines.launch

/**
 * Account details plus password reset. The reset is a real Firebase call; when
 * it fails the screen shows the mapped error instead of pretending success.
 */
class AccountSecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSecurityBinding
    private val viewModel: AccountSecurityViewModel by viewModels {
        AccountSecurityViewModel.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAccountSecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }
        binding.buttonResetPassword.setOnClickListener { confirmReset() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: AccountSecurityUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressAccount.isVisible = state.isLoading
        binding.groupAccount.isVisible = !state.isLoading && state.errorRes == null
        binding.textError.isVisible = state.errorRes != null
        binding.buttonRetry.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.setText(it) }

        binding.textName.text = state.name.ifBlank { getString(R.string.value_undefined) }
        binding.textUsername.text = if (state.username.isBlank()) {
            getString(R.string.value_undefined)
        } else {
            getString(R.string.profile_username_format, state.username)
        }
        binding.textEmail.text = state.email.ifBlank { getString(R.string.value_undefined) }

        binding.buttonResetPassword.isEnabled =
            !state.isSendingReset && state.email.isNotBlank()
        binding.progressReset.isVisible = state.isSendingReset

        val status = when {
            state.resetSent -> getString(R.string.account_security_reset_sent, state.email)
            state.resetErrorRes != null -> getString(state.resetErrorRes)
            else -> null
        }
        binding.textResetStatus.isVisible = status != null
        binding.textResetStatus.text = status
        binding.textResetStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.resetErrorRes != null) R.color.loss else R.color.on_surface_variant
            )
        )
    }

    private fun confirmReset() {
        if (binding.buttonResetPassword.isEnabled.not()) return
        val email = viewModel.uiState.value.email
        if (email.isBlank()) {
            viewModel.sendPasswordReset()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.account_security_reset_confirm_title)
            .setMessage(getString(R.string.account_security_reset_confirm_message, email))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.account_security_reset_action) { _, _ ->
                viewModel.sendPasswordReset()
            }
            .show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
