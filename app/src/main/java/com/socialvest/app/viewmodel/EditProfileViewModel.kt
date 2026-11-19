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

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false,
    val fullName: String = "",
    val username: String = "",
    val bio: String = "",
    val email: String = "",
    val fullNameErrorRes: Int? = null,
    val usernameErrorRes: Int? = null,
    val bioErrorRes: Int? = null,
    val errorRes: Int? = null,
    val isSaved: Boolean = false,
    val isSignedOut: Boolean = false
)

class EditProfileViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val uid: String? = repository.currentUserId()

    init {
        val userId = uid
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            _uiState.update { it.copy(email = repository.currentEmail()) }
            viewModelScope.launch {
                runCatching { repository.loadProfile(userId) }
                    .onSuccess { user ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoaded = true,
                                fullName = user?.fullName
                                    ?: repository.currentUser()?.displayName.orEmpty(),
                                username = user?.username.orEmpty(),
                                bio = user?.bio.orEmpty()
                            )
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoaded = true,
                                errorRes = ErrorMapper.messageRes(throwable)
                            )
                        }
                    }
            }
        }
    }

    fun save(fullName: String, username: String, bio: String) {
        val fullNameError = Validators.fullNameError(fullName)
        val usernameError = Validators.usernameError(username)
        val bioError = Validators.bioError(bio)

        if (fullNameError != null || usernameError != null || bioError != null) {
            _uiState.update {
                it.copy(
                    fullNameErrorRes = fullNameError,
                    usernameErrorRes = usernameError,
                    bioErrorRes = bioError,
                    errorRes = null
                )
            }
            return
        }

        val userId = uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorRes = null) }
            runCatching { repository.saveProfile(userId, fullName, username, bio) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSaving = false, errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
        }
    }

    fun clearErrors() {
        _uiState.update {
            it.copy(
                fullNameErrorRes = null,
                usernameErrorRes = null,
                bioErrorRes = null,
                errorRes = null
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                EditProfileViewModel(app.authRepository)
            }
        }
    }
}
