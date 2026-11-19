package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.domain.social.SocialFeed
import com.socialvest.app.domain.social.SocialTextError
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val content: String = "",
    val category: AssetCategory = AssetCategory.STOCK,
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val contentErrorRes: Int? = null,
    val isSubmitting: Boolean = false,
    val errorRes: Int? = null,
    val isPosted: Boolean = false
)

/**
 * Create/edit post form. Validation is shared with the feed through [SocialFeed].
 * When [postId] is set the form loads that post once and saves an owner edit.
 */
class CreatePostViewModel(
    private val repository: SocialRepository,
    private val postId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreatePostUiState(isEdit = postId != null, isLoading = postId != null)
    )
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    init {
        if (postId != null) loadPost(postId)
    }

    private fun loadPost(postId: String) {
        viewModelScope.launch {
            runCatching { repository.getPost(postId) }
                .onSuccess { post ->
                    _uiState.update {
                        if (post == null) {
                            it.copy(
                                isLoading = false,
                                errorRes = R.string.social_detail_missing
                            )
                        } else {
                            it.copy(
                                isLoading = false,
                                content = post.content,
                                category = post.category
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun onContentChanged(value: String) {
        _uiState.update { it.copy(content = value, contentErrorRes = null) }
    }

    fun onCategorySelected(category: AssetCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.isLoading) return

        val validation = SocialFeed.postError(state.content)
        if (validation != null) {
            _uiState.update { it.copy(contentErrorRes = validation.toMessageRes()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorRes = null) }
            val result = runCatching {
                if (postId == null) {
                    repository.createPost(state.content, state.category)
                } else {
                    repository.updatePost(postId, state.content, state.category)
                }
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isPosted = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun SocialTextError.toMessageRes(): Int = when (this) {
        SocialTextError.EMPTY -> R.string.social_post_error_empty
        SocialTextError.TOO_LONG -> R.string.social_post_error_long
    }

    companion object {
        fun factory(postId: String? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                CreatePostViewModel(app.socialRepository, postId)
            }
        }
    }
}
