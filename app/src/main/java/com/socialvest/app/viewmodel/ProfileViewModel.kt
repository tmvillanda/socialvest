package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.User
import com.socialvest.app.data.repository.AuthRepository
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val email: String = "",
    val profileMissing: Boolean = false,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
)

class ProfileViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val uid = repository.currentUserId()
        val email = repository.currentEmail()

        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            repository.profileFlow(uid)
                .onEach { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            email = email,
                            profileMissing = user == null,
                            errorRes = null
                        )
                    }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(isSignedOut = true) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                ProfileViewModel(app.authRepository)
            }
        }
    }
}
