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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
)

/**
 * Account details shown on the Settings/privacy screen. Read-only: editing lives
 * on the Profile tab. The email comes from the Auth session and is never read
 * from (or written to) the public `users/{uid}` document.
 */
class SettingsViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    /** Re-subscribes after a failure (a caught flow stops emitting for good). */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        repository.currentUserId()?.let(::observe)
    }

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(isSignedOut = true) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                SettingsViewModel(app.authRepository)
            }
        }
    }
}
