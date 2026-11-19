package com.socialvest.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.MainActivity
import com.socialvest.app.databinding.ActivityRegisterBinding
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.RegisterUiState
import com.socialvest.app.viewmodel.RegisterViewModel
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels { RegisterViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.buttonRegister.setOnClickListener { submit() }
        binding.linkLogin.setOnClickListener { finish() }

        listOf(
            binding.editFullName,
            binding.editUsername,
            binding.editEmail,
            binding.editPassword,
            binding.editConfirmPassword
        ).forEach { field ->
            field.doAfterTextChanged { viewModel.clearErrors() }
        }

        observeState()
    }

    private fun submit() {
        if (binding.buttonRegister.isEnabled.not()) return
        viewModel.register(
            fullName = binding.editFullName.text?.toString().orEmpty(),
            username = binding.editUsername.text?.toString().orEmpty(),
            email = binding.editEmail.text?.toString().orEmpty(),
            password = binding.editPassword.text?.toString().orEmpty(),
            confirmPassword = binding.editConfirmPassword.text?.toString().orEmpty()
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: RegisterUiState) {
        binding.progressRegister.isVisible = state.isLoading
        binding.buttonRegister.isEnabled = !state.isLoading
        binding.linkLogin.isEnabled = !state.isLoading

        binding.inputFullName.error = state.fullNameErrorRes?.let(::getString)
        binding.inputUsername.error = state.usernameErrorRes?.let(::getString)
        binding.inputEmail.error = state.emailErrorRes?.let(::getString)
        binding.inputPassword.error = state.passwordErrorRes?.let(::getString)
        binding.inputConfirmPassword.error = state.confirmPasswordErrorRes?.let(::getString)

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        if (state.isRegistered) {
            binding.buttonRegister.isEnabled = false
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
