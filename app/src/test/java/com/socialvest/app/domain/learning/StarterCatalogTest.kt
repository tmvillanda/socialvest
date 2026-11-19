package com.socialvest.app.domain.learning

import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCatalogTest {

    private val course get() = StarterCatalog.course(StarterCatalog.COURSE_ID)
    private val modules get() = StarterCatalog.modules(StarterCatalog.COURSE_ID).orEmpty()

    @Test
    fun `catalogue contains exactly one bundled playlist course`() {
        assertEquals(1, StarterCatalog.courses.size)
        assertEquals(StarterCatalog.COURSE_ID, StarterCatalog.courses.single().id)
    }

    @Test
    fun `bundled course is published and points at the source playlist`() {
        val value = requireNotNull(course)

        assertTrue(value.isPublished)
        assertEquals("Belajar Saham dari Nol!", value.title)
        assertEquals(StarterCatalog.CHANNEL_NAME, value.instructorName)
        assertEquals(StarterCatalog.CHANNEL_ID, value.instructorId)
        assertEquals(StarterCatalog.PLAYLIST_URL, value.sourceUrl)
        assertEquals(AssetCategory.STOCK, value.category)
        assertEquals(CourseLevel.BEGINNER, value.level)
        assertEquals(1, value.sortOrder)
        assertTrue(value.thumbnailUrl.startsWith("https://i.ytimg.com/vi/"))
        assertTrue(value.description.isNotBlank())
    }

    @Test
    fun `all 36 playlist videos are present in playlist order`() {
        assertEquals(36, modules.size)

        modules.forEachIndexed { index, module ->
            assertEquals(index + 1L, module.order)
            assertEquals(
                "mod-" + (index + 1).toString().padStart(2, '0'),
                module.id
            )
        }
    }

    @Test
    fun `module ids are unique`() {
        val ids = modules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every module has a valid unique youtube id and watch url`() {
        val videoIds = modules.map { it.youtubeVideoId }

        assertEquals(videoIds.size, videoIds.toSet().size)
        videoIds.forEach { id ->
            assertTrue("invalid youtube id: $id", YoutubeVideo.isValidId(id))
        }
        assertEquals("uGzToPCX8nU", videoIds.first())
        assertEquals("26zVgDh7-x0", videoIds.last())

        modules.forEach { module ->
            assertEquals(
                YoutubeVideo.watchUrl(module.youtubeVideoId),
                module.videoUrl
            )
            assertTrue(module.title.isNotBlank())
        }
    }

    @Test
    fun `every module has a positive duration close to the playlist total`() {
        assertTrue(modules.all { it.durationSeconds > 0 })
        assertEquals(34111L, modules.sumOf { it.durationSeconds })
    }

    @Test
    fun `lookup helpers resolve bundled course module and reject unknown ids`() {
        assertEquals(StarterCatalog.COURSE_ID, course?.id)
        assertEquals("mod-01", StarterCatalog.module(StarterCatalog.COURSE_ID, "mod-01")?.id)
        assertEquals("mod-36", StarterCatalog.module(StarterCatalog.COURSE_ID, "mod-36")?.id)

        assertNull(StarterCatalog.course("missing-course"))
        assertNull(StarterCatalog.modules("missing-course"))
        assertNull(StarterCatalog.module(StarterCatalog.COURSE_ID, "mod-99"))
    }

    @Test
    fun `merge keeps remote courses and appends the bundled one without duplicates`() {
        val remote = listOf(
            Course(id = "remote-a", title = "Remote A"),
            Course(id = "remote-b", title = "Remote B")
        )

        val merged = StarterCatalog.mergeCourses(remote)

        assertEquals(3, merged.size)
        assertEquals(listOf("remote-a", "remote-b", StarterCatalog.COURSE_ID), merged.map { it.id })
    }

    @Test
    fun `merge never duplicates a bundled id already returned by firestore`() {
        val shadow = Course(id = StarterCatalog.COURSE_ID, title = "Shadow copy")
        val merged = StarterCatalog.mergeCourses(listOf(shadow))

        assertEquals(1, merged.size)
        assertEquals("Shadow copy", merged.single().title)
        assertFalse(merged.any { it === StarterCatalog.courses.single() })
    }

    @Test
    fun `merge returns the bundled catalogue when firestore is empty`() {
        assertEquals(StarterCatalog.courses, StarterCatalog.mergeCourses(emptyList()))
    }
}
