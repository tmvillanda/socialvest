package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Comment
import com.socialvest.app.data.model.Post
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.domain.social.SocialFeed
import com.socialvest.app.domain.social.SocialTextError
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val isLoading: Boolean = true,
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val liked: Boolean = false,
    val isTogglingLike: Boolean = false,
    val isSubmittingComment: Boolean = false,
    val commentErrorRes: Int? = null,
    val isDeletingPost: Boolean = false,
    val isDeleted: Boolean = false,
    val commentSaved: Boolean = false,
    val commentDeleted: Boolean = false,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false,
    val currentUserId: String = ""
) {
    val isOwner: Boolean get() = post != null && post.userId == currentUserId
}

/**
 * One post plus its comments. A deleted post (or one hidden by the rules)
 * closes the screen instead of showing stale numbers.
 */
class PostDetailViewModel(
    private val repository: SocialRepository,
    private val postId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PostDetailUiState(currentUserId = repository.currentUserId().orEmpty())
    )
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var likedLoaded = false

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            observePost()
            observeComments()
        }
    }

    private fun observePost() {
        repository.postFlow(postId)
            .onEach { post ->
                if (post == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@onEach
                }

                _uiState.update { it.copy(isLoading = false, post = post, errorRes = null) }
                if (!likedLoaded) {
                    likedLoaded = true
                    loadLiked()
                }
            }
            .catch { throwable ->
                val deleted = throwable is FirebaseFirestoreException &&
                    throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                    _uiState.value.post != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDeleted = it.isDeleted || deleted,
                        errorRes = if (deleted) {
                            it.errorRes
                        } else {
                            ErrorMapper.messageRes(throwable)
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeComments() {
        repository.commentsFlow(postId)
            .onEach { comments ->
                _uiState.update { it.copy(comments = comments) }
            }
            .catch { throwable ->
                // The post flow already reports a deleted post; do not overwrite
                // that state with a second toast/dialog.
                if (_uiState.value.post != null && !_uiState.value.isDeleted) {
                    _uiState.update {
                        it.copy(errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadLiked() {
        viewModelScope.launch {
            runCatching { repository.likedPostIds(listOf(postId)) }
                .onSuccess { liked ->
                    _uiState.update { it.copy(liked = postId in liked) }
                }
        }
    }

    fun toggleLike() {
        val state = _uiState.value
        if (state.post == null || state.isTogglingLike) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingLike = true) }
            runCatching { repository.toggleLike(postId) }
                .onSuccess { liked ->
                    _uiState.update { it.copy(liked = liked, isTogglingLike = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isTogglingLike = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun addComment(content: String) {
        val state = _uiState.value
        if (state.isSubmittingComment) return

        val validation = SocialFeed.commentError(content)
        if (validation != null) {
            _uiState.update { it.copy(commentErrorRes = validation.toMessageRes()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingComment = true, commentErrorRes = null) }
            runCatching { repository.addComment(postId, content) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmittingComment = false, commentSaved = true)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmittingComment = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun deleteComment(commentId: String) {
        if (commentId.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.deleteComment(postId, commentId) }
                .onSuccess {
                    _uiState.update { it.copy(commentDeleted = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorRes = ErrorMapper.messageRes(throwable))
                    }
                }
        }
    }

    fun deletePost() {
        val state = _uiState.value
        if (!state.isOwner || state.isDeletingPost) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingPost = true) }
            runCatching { repository.deletePost(postId) }
                .onSuccess {
                    _uiState.update { it.copy(isDeletingPost = false, isDeleted = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isDeletingPost = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    fun consumeCommentSaved() {
        _uiState.update { it.copy(commentSaved = false) }
    }

    fun consumeCommentDeleted() {
        _uiState.update { it.copy(commentDeleted = false) }
    }

    private fun SocialTextError.toMessageRes(): Int = when (this) {
        SocialTextError.EMPTY -> R.string.social_comment_error_empty
        SocialTextError.TOO_LONG -> R.string.social_comment_error_long
    }

    companion object {
        fun factory(postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                PostDetailViewModel(app.socialRepository, postId)
            }
        }
    }
}
