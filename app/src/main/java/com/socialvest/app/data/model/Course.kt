package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * A course stored at `courses/{courseId}`, or bundled in
 * [com.socialvest.app.domain.learning.StarterCatalog].
 *
 * Firestore courses are seeded manually through the Console and the app only
 * reads them (the rules reject client writes). The bundled playlist course
 * needs no seed and is merged into the same list. `category` stores an
 * [AssetCategory] id so the same chips/labels are used across Invest and Learn.
 */
data class Course(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val instructorId: String = "",
    val instructorName: String = "",
    val category: AssetCategory = AssetCategory.OTHER,
    val level: CourseLevel? = null,
    val isPublished: Boolean = false,
    /** Optional public source, e.g. the YouTube playlist a course was built from. */
    val sourceUrl: String = "",
    /** Manual order for bundled courses; Firestore courses keep 0. */
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null
) {
    val createdAtMillis: Long? get() = createdAt?.toDate()?.time

    val instructorDisplayName: String
        get() = instructorName.ifBlank { DEFAULT_INSTRUCTOR }

    companion object {
        const val COLLECTION = "courses"
        const val DEFAULT_INSTRUCTOR = "Tim Socialvest"

        private const val FIELD_TITLE = "title"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_THUMBNAIL_URL = "thumbnailUrl"
        private const val FIELD_INSTRUCTOR_ID = "instructorId"
        private const val FIELD_INSTRUCTOR_NAME = "instructorName"
        private const val FIELD_CATEGORY = "category"
        private const val FIELD_LEVEL = "level"
        private const val FIELD_IS_PUBLISHED = "isPublished"
        private const val FIELD_SOURCE_URL = "sourceUrl"
        private const val FIELD_SORT_ORDER = "sortOrder"
        private const val FIELD_CREATED_AT = "createdAt"

        fun fromSnapshot(document: DocumentSnapshot): Course = Course(
            id = document.id,
            title = document.getString(FIELD_TITLE).orEmpty(),
            description = document.getString(FIELD_DESCRIPTION).orEmpty(),
            thumbnailUrl = document.getString(FIELD_THUMBNAIL_URL).orEmpty(),
            instructorId = document.getString(FIELD_INSTRUCTOR_ID).orEmpty(),
            instructorName = document.getString(FIELD_INSTRUCTOR_NAME).orEmpty(),
            category = AssetCategory.fromId(document.getString(FIELD_CATEGORY)),
            level = CourseLevel.fromId(document.getString(FIELD_LEVEL)),
            isPublished = document.getBoolean(FIELD_IS_PUBLISHED) == true,
            sourceUrl = document.getString(FIELD_SOURCE_URL).orEmpty(),
            sortOrder = document.getLong(FIELD_SORT_ORDER)?.toInt() ?: 0,
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )
    }
}
