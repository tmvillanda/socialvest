package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Post
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.domain.social.SocialFeed
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialUiState(
    val isLoading: Boolean = true,
    val posts: List<Post> = emptyList(),
    val visibleCount: Int = SocialFeed.PAGE_SIZE,
    val likedIds: Set<String> = emptySet(),
    val likingIds: Set<String> = emptySet(),
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
) {
    val visiblePosts: List<Post> get() = SocialFeed.visiblePage(posts, visibleCount)
    val hasMore: Boolean get() = SocialFeed.hasMore(posts, visibleCount)

    /** Everything the list needs, pre-computed so the adapter stays dumb. */
    val rows: List<SocialPostRow>
        get() = visiblePosts.map { post ->
            SocialPostRow(
                post = post,
                liked = post.id in likedIds,
                isLiking = post.id in likingIds
            )
        }
}

data class SocialPostRow(
    val post: Post,
    val liked: Boolean,
    val isLiking: Boolean
)

/**
 * Phase 4 feed. Posts arrive through a snapshot listener; ordering and paging
 * are done in memory so the query needs no composite index. The like state of
 * each visible post is fetched once and then kept in sync locally.
 */
class SocialViewModel(
    private val repository: SocialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private val probedLikeIds = mutableSetOf<String>()

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            observeFeed()
        }
    }

    private fun observeFeed() {
        repository.postsFlow()
            .onEach { posts ->
                val ordered = SocialFeed.sortNewestFirst(posts)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        posts = ordered,
                        visibleCount = state.visibleCount.coerceAtLeast(SocialFeed.PAGE_SIZE),
                        errorRes = null
                    )
                }
                probeLikes(ordered.map { it.id })
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

    /** One read per post, only the first time it appears in the feed. */
    private fun probeLikes(postIds: List<String>) {
        val pending = postIds.filter { it.isNotBlank() && probedLikeIds.add(it) }
        if (pending.isEmpty()) return

        viewModelScope.launch {
            runCatching { repository.likedPostIds(pending) }
                .onSuccess { liked ->
                    _uiState.update { it.copy(likedIds = it.likedIds + liked) }
                }
                .onFailure { throwable ->
                    pending.forEach(probedLikeIds::remove)
                    _uiState.update { it.copy(errorRes = ErrorMapper.messageRes(throwable)) }
                }
        }
    }

    /**
     * Re-reads the like state of every loaded post. Called when the screen
     * resumes: the feed cannot observe other posts' like documents, so a like
     * made on the detail screen would otherwise leave the heart stale.
     */
    fun refreshLikedState() {
        val ids = _uiState.value.posts.map { it.id }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return

        probedLikeIds.clear()
        probedLikeIds.addAll(ids)

        viewModelScope.launch {
            runCatching { repository.likedPostIds(ids) }
                .onSuccess { liked ->
                    _uiState.update { it.copy(likedIds = liked) }
                }
                .onFailure {
                    // Keep the current state; the next resume retries.
                    probedLikeIds.clear()
                }
        }
    }

    fun toggleLike(postId: String) {
        val state = _uiState.value
        if (postId.isBlank() || postId in state.likingIds) return

        viewModelScope.launch {
            _uiState.update { it.copy(likingIds = it.likingIds + postId) }
            runCatching { repository.toggleLike(postId) }
                .onSuccess { liked ->
                    _uiState.update {
                        it.copy(
                            likedIds = if (liked) {
                                it.likedIds + postId
                            } else {
                                it.likedIds - postId
                            },
                            likingIds = it.likingIds - postId
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            likingIds = it.likingIds - postId,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun loadMore() {
        _uiState.update {
            it.copy(
                visibleCount = SocialFeed.nextVisibleCount(it.visibleCount, it.posts.size)
            )
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                SocialViewModel(app.socialRepository)
            }
        }
    }
}
