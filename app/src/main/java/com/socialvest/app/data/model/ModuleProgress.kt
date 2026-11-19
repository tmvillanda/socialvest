package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Per-lesson progress stored at `progress/{uid}_{courseId}_{moduleId}`.
 *
 * Only the completed state is tracked (playback position is kept at 0); the
 * brief allows "completed state and/or last playback position".
 */
data class ModuleProgress(
    val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val moduleId: String = "",
    val lastPositionSeconds: Long = 0L,
    val completed: Boolean = false,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val COLLECTION = "progress"

        private const val FIELD_USER_ID = "userId"
        private const val FIELD_COURSE_ID = "courseId"
        private const val FIELD_MODULE_ID = "moduleId"
        private const val FIELD_LAST_POSITION = "lastPositionSeconds"
        private const val FIELD_COMPLETED = "completed"
        private const val FIELD_UPDATED_AT = "updatedAt"

        fun documentId(userId: String, courseId: String, moduleId: String): String =
            "${userId}_${courseId}_$moduleId"

        fun fromSnapshot(document: DocumentSnapshot): ModuleProgress = ModuleProgress(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            courseId = document.getString(FIELD_COURSE_ID).orEmpty(),
            moduleId = document.getString(FIELD_MODULE_ID).orEmpty(),
            lastPositionSeconds = document.getLong(FIELD_LAST_POSITION) ?: 0L,
            completed = document.getBoolean(FIELD_COMPLETED) == true,
            updatedAt = document.getTimestamp(FIELD_UPDATED_AT)
        )
    }
}
