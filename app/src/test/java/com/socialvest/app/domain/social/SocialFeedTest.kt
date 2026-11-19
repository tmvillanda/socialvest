package com.socialvest.app.domain.social

import com.google.firebase.Timestamp
import com.socialvest.app.data.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SocialFeedTest {

    private fun post(id: String, millis: Long?, content: String = "isi") = Post(
        id = id,
        content = content,
        createdAt = millis?.let { Timestamp(Date(it)) }
    )

    @Test
    fun `feed is ordered newest first`() {
        val posts = listOf(
            post("old", 1_000L),
            post("new", 3_000L),
            post("middle", 2_000L)
        )

        val ordered = SocialFeed.sortNewestFirst(posts)

        assertEquals(listOf("new", "middle", "old"), ordered.map { it.id })
    }

    @Test
    fun `posts without a timestamp go last and ties use the id`() {
        val posts = listOf(
            post("no-time", null),
            post("b", 5_000L),
            post("a", 5_000L)
        )

        val ordered = SocialFeed.sortNewestFirst(posts)

        assertEquals("no-time", ordered.last().id)
        assertEquals(listOf("b", "a"), ordered.take(2).map { it.id })
    }

    @Test
    fun `only one page is revealed at first`() {
        val posts = (1..25).map { post("p$it", it.toLong()) }

        val visible = SocialFeed.visiblePage(posts, SocialFeed.PAGE_SIZE)

        assertEquals(SocialFeed.PAGE_SIZE, visible.size)
        assertTrue(SocialFeed.hasMore(posts, SocialFeed.PAGE_SIZE))
        assertFalse(SocialFeed.hasMore(posts, posts.size))
    }

    @Test
    fun `load more grows by one page and stops at the end`() {
        assertEquals(20, SocialFeed.nextVisibleCount(10, 25))
        assertEquals(25, SocialFeed.nextVisibleCount(20, 25))
        assertEquals(10, SocialFeed.nextVisibleCount(10, 3))
    }

    @Test
    fun `blank post content is rejected`() {
        assertEquals(SocialTextError.EMPTY, SocialFeed.postError("   "))
        assertNull(SocialFeed.postError("Diskusi singkat."))
    }

    @Test
    fun `post content is capped at two thousand characters`() {
        val exact = "a".repeat(SocialFeed.MAX_POST_LENGTH)
        val tooLong = "a".repeat(SocialFeed.MAX_POST_LENGTH + 1)

        assertNull(SocialFeed.postError(exact))
        assertEquals(SocialTextError.TOO_LONG, SocialFeed.postError(tooLong))
    }

    @Test
    fun `comment content is capped at one thousand characters`() {
        val exact = "b".repeat(SocialFeed.MAX_COMMENT_LENGTH)
        val tooLong = "b".repeat(SocialFeed.MAX_COMMENT_LENGTH + 1)

        assertNull(SocialFeed.commentError(exact))
        assertEquals(SocialTextError.TOO_LONG, SocialFeed.commentError(tooLong))
        assertEquals(SocialTextError.EMPTY, SocialFeed.commentError(""))
    }
}
