package com.socialvest.app.data.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * One lesson stored at `courses/{courseId}/modules/{moduleId}`.
 *
 * Only YouTube playback is supported, so `youtubeVideoId` is the field the
 * player uses. `videoUrl` is accepted as a fallback for seeds that store the
 * full watch URL instead (the id is extracted client-side).
 */
data class CourseModule(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val youtubeVideoId: String = "",
    val videoUrl: String = "",
    val durationSeconds: Long = 0L,
    val order: Long = 0L
) {
    companion object {
        const val COLLECTION = "modules"

        private const val FIELD_TITLE = "title"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_YOUTUBE_VIDEO_ID = "youtubeVideoId"
        private const val FIELD_VIDEO_URL = "videoUrl"
        private const val FIELD_DURATION_SECONDS = "durationSeconds"
        private const val FIELD_ORDER = "order"

        fun fromSnapshot(document: DocumentSnapshot): CourseModule = CourseModule(
            id = document.id,
            title = document.getString(FIELD_TITLE).orEmpty(),
            description = document.getString(FIELD_DESCRIPTION).orEmpty(),
            youtubeVideoId = document.getString(FIELD_YOUTUBE_VIDEO_ID).orEmpty(),
            videoUrl = document.getString(FIELD_VIDEO_URL).orEmpty(),
            durationSeconds = document.getLong(FIELD_DURATION_SECONDS) ?: 0L,
            order = document.getLong(FIELD_ORDER) ?: 0L
        )
    }
}
