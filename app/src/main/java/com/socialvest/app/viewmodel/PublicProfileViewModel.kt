package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.User
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class PublicProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
)

/** Public profile of another signed-in user (name, username, bio, join date). */
class PublicProfileViewModel(
    private val repository: SocialRepository,
    private val uid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            repository.profileFlow(uid)
                .onEach { user ->
                    _uiState.update {
                        it.copy(isLoading = false, user = user, errorRes = null)
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

    companion object {
        fun factory(uid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                PublicProfileViewModel(app.socialRepository, uid)
            }
        }
    }
}
