package com.socialvest.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityEditProfileBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.EditProfileUiState
import com.socialvest.app.viewmodel.EditProfileViewModel
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: EditProfileViewModel by viewModels { EditProfileViewModel.Factory }

    private var formPrefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { submit() }

        listOf(binding.editFullName, binding.editUsername, binding.editBio).forEach { field ->
            field.doAfterTextChanged { viewModel.clearErrors() }
        }

        observeState()
    }

    private fun submit() {
        if (binding.buttonSave.isEnabled.not()) return
        viewModel.save(
            fullName = binding.editFullName.text?.toString().orEmpty(),
            username = binding.editUsername.text?.toString().orEmpty(),
            bio = binding.editBio.text?.toString().orEmpty()
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: EditProfileUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressSave.isVisible = state.isSaving
        binding.buttonSave.isEnabled = !state.isSaving && !state.isLoading
        binding.editFullName.isEnabled = !state.isSaving
        binding.editUsername.isEnabled = !state.isSaving
        binding.editBio.isEnabled = !state.isSaving

        binding.textEmail.text = state.email

        if (state.isLoaded && !formPrefilled) {
            binding.editFullName.setText(state.fullName)
            binding.editUsername.setText(state.username)
            binding.editBio.setText(state.bio)
            formPrefilled = true
        }

        binding.inputFullName.error = state.fullNameErrorRes?.let(::getString)
        binding.inputUsername.error = state.usernameErrorRes?.let(::getString)
        binding.inputBio.error = state.bioErrorRes?.let(::getString)

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }

        if (state.isSaved) {
            binding.buttonSave.isEnabled = false
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
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
}
