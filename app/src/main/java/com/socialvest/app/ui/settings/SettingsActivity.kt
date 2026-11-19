package com.socialvest.app.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivitySettingsBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.SettingsUiState
import com.socialvest.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Settings & privacy: read-only account details, a plain-language privacy
 * summary, the required financial disclaimer, the data-deletion policy and
 * logout. Account editing stays on the Profile tab.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }
        binding.buttonLogout.setOnClickListener { confirmLogout() }

        binding.textVersion.text = getString(
            R.string.settings_version_format,
            appVersionName()
        )

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SettingsUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressSettings.isVisible = state.isLoading
        binding.groupSettings.isVisible = !state.isLoading && state.errorRes == null
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
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout_button) { _, _ -> viewModel.logout() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
