package com.socialvest.app.domain.learning

import com.google.firebase.Timestamp
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.data.model.ModuleProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class LearningTest {

    private fun course(id: String, millis: Long?) = Course(
        id = id,
        title = id,
        createdAt = millis?.let { Timestamp(Date(it)) }
    )

    private fun module(id: String, order: Long) = CourseModule(
        id = id,
        title = id,
        order = order
    )

    private fun progress(moduleId: String, completed: Boolean) = ModuleProgress(
        moduleId = moduleId,
        completed = completed
    )

    @Test
    fun `courses are ordered newest first with a stable id tie-break`() {
        val courses = listOf(
            course("old", 1_000L),
            course("b-new", 3_000L),
            course("a-new", 3_000L),
            course("middle", 2_000L)
        )

        val ordered = Learning.sortNewestFirst(courses)

        assertEquals(
            listOf("b-new", "a-new", "middle", "old"),
            ordered.map { it.id }
        )
    }

    @Test
    fun `courses without a timestamp go last`() {
        val courses = listOf(
            course("undated", null),
            course("dated", 1_000L)
        )

        assertEquals(
            listOf("dated", "undated"),
            Learning.sortNewestFirst(courses).map { it.id }
        )
    }

    @Test
    fun `modules are ordered by their order field, then id`() {
        val modules = listOf(
            module("third", 3),
            module("second-b", 2),
            module("second-a", 2),
            module("first", 1)
        )

        assertEquals(
            listOf("first", "second-a", "second-b", "third"),
            Learning.orderedModules(modules).map { it.id }
        )
    }

    @Test
    fun `progress percent never divides by zero`() {
        assertEquals(0, Learning.progressPercent(0, 0))
        assertEquals(0, Learning.progressPercent(2, 0))
    }

    @Test
    fun `progress percent is integer and clamped`() {
        assertEquals(33, Learning.progressPercent(1, 3))
        assertEquals(100, Learning.progressPercent(3, 3))
        assertEquals(100, Learning.progressPercent(5, 2))
        assertEquals(0, Learning.progressPercent(-1, 3))
    }

    @Test
    fun `completed count ignores foreign and duplicate progress rows`() {
        val modules = listOf(module("m1", 1), module("m2", 2), module("m3", 3))
        val progress = listOf(
            progress("m1", true),
            progress("m1", true),
            progress("m2", false),
            progress("other-course", true)
        )

        assertEquals(1, Learning.countCompleted(modules, progress))
    }

    @Test
    fun `course is complete only when every module is done`() {
        val modules = listOf(module("m1", 1), module("m2", 2))

        assertFalse(Learning.isCourseComplete(modules, emptyList()))
        assertFalse(
            Learning.isCourseComplete(modules, listOf(progress("m1", true)))
        )
        assertTrue(
            Learning.isCourseComplete(
                modules,
                listOf(progress("m1", true), progress("m2", true))
            )
        )
        assertFalse(Learning.isCourseComplete(emptyList(), emptyList()))
    }

    @Test
    fun `duration labels are short Indonesian strings`() {
        assertEquals("", Learning.durationLabel(0))
        assertEquals("", Learning.durationLabel(-10))
        assertEquals("45 detik", Learning.durationLabel(45))
        assertEquals("1 menit", Learning.durationLabel(60))
        assertEquals("10 menit", Learning.durationLabel(600))
        assertEquals("1 jam", Learning.durationLabel(3_600))
        assertEquals("1 jam 5 menit", Learning.durationLabel(3_900))
    }

    @Test
    fun `youtube id can be parsed from every common url shape`() {
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.extractId(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"
        ))
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.extractId(
            "https://youtu.be/dQw4w9WgXcQ"
        ))
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.extractId(
            "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        ))
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.extractId(
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"
        ))
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.extractId(
            "https://www.youtube.com/live/dQw4w9WgXcQ"
        ))
    }

    @Test
    fun `invalid youtube urls and ids are rejected`() {
        assertNull(YoutubeVideo.extractId(null))
        assertNull(YoutubeVideo.extractId(""))
        assertNull(YoutubeVideo.extractId("https://example.com/video"))
        assertNull(YoutubeVideo.extractId("https://youtu.be/short"))
        assertFalse(YoutubeVideo.isValidId("abc"))
        assertTrue(YoutubeVideo.isValidId("dQw4w9WgXcQ"))
    }

    @Test
    fun `module video id prefers the explicit field then the url`() {
        val explicit = CourseModule(id = "m", youtubeVideoId = "dQw4w9WgXcQ")
        val fromUrl = CourseModule(
            id = "m",
            videoUrl = "https://youtu.be/dQw4w9WgXcQ"
        )
        val missing = CourseModule(id = "m", youtubeVideoId = "bad")

        assertEquals("dQw4w9WgXcQ", YoutubeVideo.idOf(explicit))
        assertEquals("dQw4w9WgXcQ", YoutubeVideo.idOf(fromUrl))
        assertNull(YoutubeVideo.idOf(missing))
    }

    @Test
    fun `embed url carries the app origin instead of a youtube referrer`() {
        val url = YoutubeVideo.embedUrl("dQw4w9WgXcQ")

        assertTrue(url.startsWith("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(url.contains("origin=https%3A%2F%2Fsocialvest.app"))
        assertTrue(url.contains("widget_referrer=https%3A%2F%2Fsocialvest.app"))
    }
}
