package com.socialvest.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.MainActivity
import com.socialvest.app.databinding.ActivityLoginBinding
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.LoginUiState
import com.socialvest.app.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.buttonLogin.setOnClickListener { submit() }
        binding.editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.linkRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.editEmail.doAfterTextChanged { viewModel.clearErrors() }
        binding.editPassword.doAfterTextChanged { viewModel.clearErrors() }

        observeState()
    }

    private fun submit() {
        if (binding.buttonLogin.isEnabled.not()) return
        viewModel.login(
            email = binding.editEmail.text?.toString().orEmpty(),
            password = binding.editPassword.text?.toString().orEmpty()
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: LoginUiState) {
        binding.progressLogin.isVisible = state.isLoading
        binding.buttonLogin.isEnabled = !state.isLoading
        binding.editEmail.isEnabled = !state.isLoading
        binding.editPassword.isEnabled = !state.isLoading
        binding.linkRegister.isEnabled = !state.isLoading

        binding.inputEmail.error = state.emailErrorRes?.let(::getString)
        binding.inputPassword.error = state.passwordErrorRes?.let(::getString)

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        if (state.isLoggedIn) {
            binding.buttonLogin.isEnabled = false
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
