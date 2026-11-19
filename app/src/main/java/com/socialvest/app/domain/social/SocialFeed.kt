package com.socialvest.app.domain.social

import com.socialvest.app.data.model.Comment
import com.socialvest.app.data.model.Post

/** Why a post/comment cannot be submitted. Rendered as Indonesian copy. */
enum class SocialTextError { EMPTY, TOO_LONG }

/**
 * Pure feed rules shared by the ViewModel and the unit tests.
 *
 * The feed query is kept index-free (only an equality filter on `status`), so
 * ordering and paging happen on the client. This is deliberate: a composite
 * index would have to be created manually before the feed could load at all.
 */
object SocialFeed {

    /** How many posts are revealed per "Muat lebih banyak" tap. */
    const val PAGE_SIZE = 10

    /** Upper bound of documents kept in memory for one feed session. */
    const val MAX_POSTS = 100

    const val MAX_POST_LENGTH = 2000
    const val MAX_COMMENT_LENGTH = Comment.MAX_CONTENT_LENGTH

    fun sortNewestFirst(posts: List<Post>): List<Post> = posts.sortedWith(
        compareByDescending<Post> { it.createdAt?.seconds ?: 0L }
            .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
            .thenByDescending { it.id }
    )

    fun visiblePage(sorted: List<Post>, visibleCount: Int): List<Post> =
        sorted.take(visibleCount.coerceAtLeast(PAGE_SIZE))

    fun hasMore(sorted: List<Post>, visibleCount: Int): Boolean =
        visibleCount < sorted.size

    fun nextVisibleCount(visibleCount: Int, size: Int): Int =
        (visibleCount + PAGE_SIZE).coerceAtMost(maxOf(size, PAGE_SIZE))

    fun postError(content: String): SocialTextError? = when {
        content.isBlank() -> SocialTextError.EMPTY
        content.trim().length > MAX_POST_LENGTH -> SocialTextError.TOO_LONG
        else -> null
    }

    fun commentError(content: String): SocialTextError? = when {
        content.isBlank() -> SocialTextError.EMPTY
        content.trim().length > MAX_COMMENT_LENGTH -> SocialTextError.TOO_LONG
        else -> null
    }
}
