package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.repository.AuthRepository
import com.socialvest.app.utils.ErrorMapper
import com.socialvest.app.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val isLoading: Boolean = false,
    val fullNameErrorRes: Int? = null,
    val usernameErrorRes: Int? = null,
    val emailErrorRes: Int? = null,
    val passwordErrorRes: Int? = null,
    val confirmPasswordErrorRes: Int? = null,
    val errorRes: Int? = null,
    val isRegistered: Boolean = false
)

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(
        fullName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        val fullNameError = Validators.fullNameError(fullName)
        val usernameError = Validators.usernameError(username)
        val emailError = Validators.emailError(email)
        val passwordError = Validators.passwordError(password)
        val confirmError = Validators.confirmPasswordError(password, confirmPassword)

        if (listOf(fullNameError, usernameError, emailError, passwordError, confirmError)
                .any { it != null }
        ) {
            _uiState.update {
                it.copy(
                    fullNameErrorRes = fullNameError,
                    usernameErrorRes = usernameError,
                    emailErrorRes = emailError,
                    passwordErrorRes = passwordError,
                    confirmPasswordErrorRes = confirmError,
                    errorRes = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }

            runCatching { repository.register(fullName, username, email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
        }
    }

    fun clearErrors() {
        _uiState.update {
            it.copy(
                fullNameErrorRes = null,
                usernameErrorRes = null,
                emailErrorRes = null,
                passwordErrorRes = null,
                confirmPasswordErrorRes = null,
                errorRes = null
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                RegisterViewModel(app.authRepository)
            }
        }
    }
}
