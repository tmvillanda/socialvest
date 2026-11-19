package com.socialvest.app.domain.learning

import com.socialvest.app.data.model.CourseModule

/**
 * YouTube helpers. Videos are only ever embedded/streamed from YouTube — the
 * app never downloads nor re-uploads a creator's content.
 */
object YoutubeVideo {

    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{6,20}$")

    /** Uses `youtubeVideoId` when present, otherwise parses `videoUrl`. */
    fun idOf(module: CourseModule): String? =
        module.youtubeVideoId.takeIf(::isValidId) ?: extractId(module.videoUrl)

    /** Accepts watch, youtu.be, shorts, embed and live URLs. */
    fun extractId(url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null

        val candidates = listOf(
            Regex("""[?&]v=([A-Za-z0-9_-]{6,20})"""),
            Regex("""youtu\.be/([A-Za-z0-9_-]{6,20})"""),
            Regex("""/shorts/([A-Za-z0-9_-]{6,20})"""),
            Regex("""/embed/([A-Za-z0-9_-]{6,20})"""),
            Regex("""/live/([A-Za-z0-9_-]{6,20})""")
        )

        return candidates.firstNotNullOfOrNull { pattern ->
            pattern.find(value)?.groupValues?.get(1)?.takeIf(::isValidId)
        }
    }

    fun isValidId(id: String?): Boolean =
        !id.isNullOrBlank() && ID_PATTERN.matches(id)

    /**
     * Origin/referrer YouTube expects for embedded playback. It must not be a
     * youtube.com host: the player treats that as an identity conflict (error
     * 152), while a missing referrer produces error 153.
     */
    const val EMBED_ORIGIN = "https://socialvest.app"

    private const val EMBED_ORIGIN_ENCODED = "https%3A%2F%2Fsocialvest.app"

    fun embedUrl(id: String): String =
        "https://www.youtube.com/embed/$id" +
            "?rel=0&modestbranding=1" +
            "&origin=$EMBED_ORIGIN_ENCODED&widget_referrer=$EMBED_ORIGIN_ENCODED"

    fun watchUrl(id: String): String = "https://www.youtube.com/watch?v=$id"
}
