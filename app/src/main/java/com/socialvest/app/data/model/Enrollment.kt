package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Enrollment stored at `enrollments/{uid}_{courseId}`.
 *
 * The document id format is enforced by `firestore.rules`; it also makes the
 * "am I enrolled?" check a single document read.
 */
data class Enrollment(
    val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val status: String = STATUS_IN_PROGRESS,
    val enrolledAt: Timestamp? = null
) {
    val isCompleted: Boolean get() = status == STATUS_COMPLETED

    companion object {
        const val COLLECTION = "enrollments"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"

        private const val FIELD_USER_ID = "userId"
        private const val FIELD_COURSE_ID = "courseId"
        private const val FIELD_STATUS = "status"
        private const val FIELD_ENROLLED_AT = "enrolledAt"

        fun documentId(userId: String, courseId: String): String = "${userId}_$courseId"

        fun fromSnapshot(document: DocumentSnapshot): Enrollment = Enrollment(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            courseId = document.getString(FIELD_COURSE_ID).orEmpty(),
            status = document.getString(FIELD_STATUS) ?: STATUS_IN_PROGRESS,
            enrolledAt = document.getTimestamp(FIELD_ENROLLED_AT)
        )
    }
}
