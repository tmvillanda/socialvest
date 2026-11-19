package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.repository.AuthRepository
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountSecurityUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val isSendingReset: Boolean = false,
    val resetSent: Boolean = false,
    val errorRes: Int? = null,
    val resetErrorRes: Int? = null,
    val isSignedOut: Boolean = false
)

/**
 * Account details plus the only security action the app really supports:
 * asking Firebase to email a password-reset link. Nothing about the password
 * itself is read, stored, or validated here.
 */
class AccountSecurityViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSecurityUiState())
    val uiState: StateFlow<AccountSecurityUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        val uid = repository.currentUserId()
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            observe(uid)
        }
    }

    private fun observe(uid: String) {
        observeJob?.cancel()
        observeJob = repository.profileFlow(uid)
            .onEach { user ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = user?.fullName.orEmpty(),
                        username = user?.username.orEmpty(),
                        email = repository.currentEmail(),
                        errorRes = null
                    )
                }
            }
            .catch { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, errorRes = ErrorMapper.messageRes(throwable))
                }
            }
            .launchIn(viewModelScope)
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        repository.currentUserId()?.let(::observe)
    }

    fun sendPasswordReset() {
        if (_uiState.value.isSendingReset) return

        if (repository.currentEmail().isBlank()) {
            _uiState.update { it.copy(resetErrorRes = R.string.account_security_reset_no_email) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSendingReset = true, resetSent = false, resetErrorRes = null)
            }

            runCatching { repository.sendPasswordResetEmail() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSendingReset = false,
                            resetSent = true,
                            resetErrorRes = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSendingReset = false,
                            resetSent = false,
                            resetErrorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                AccountSecurityViewModel(app.authRepository)
            }
        }
    }
}
