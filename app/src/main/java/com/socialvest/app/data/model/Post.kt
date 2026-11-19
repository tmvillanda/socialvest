package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * A community post stored at `posts/{postId}`.
 *
 * The author name/username are copied into the post when it is created so the
 * feed needs no extra reads and a later profile edit cannot rewrite history.
 * Ownership always comes from the authenticated user, never from UI input.
 *
 * Posts are user opinions, not verified financial advice.
 */
data class Post(
    val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val category: AssetCategory = AssetCategory.OTHER,
    val status: String = STATUS_PUBLISHED,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val createdAt: Timestamp? = null
) {
    val isPublished: Boolean get() = status == STATUS_PUBLISHED

    val authorDisplayName: String
        get() = authorName.ifBlank {
            authorUsername.ifBlank { FALLBACK_AUTHOR }
        }

    val createdAtMillis: Long? get() = createdAt?.toDate()?.time

    companion object {
        const val COLLECTION = "posts"
        const val STATUS_PUBLISHED = "published"
        const val FALLBACK_AUTHOR = "Pengguna Socialvest"

        const val FIELD_USER_ID = "userId"
        const val FIELD_AUTHOR_NAME = "authorName"
        const val FIELD_AUTHOR_USERNAME = "authorUsername"
        const val FIELD_CONTENT = "content"
        const val FIELD_IMAGE_URL = "imageUrl"
        const val FIELD_CATEGORY = "category"
        const val FIELD_STATUS = "status"
        const val FIELD_LIKE_COUNT = "likeCount"
        const val FIELD_COMMENT_COUNT = "commentCount"
        const val FIELD_CREATED_AT = "createdAt"

        fun fromSnapshot(document: DocumentSnapshot): Post = Post(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            authorName = document.getString(FIELD_AUTHOR_NAME).orEmpty(),
            authorUsername = document.getString(FIELD_AUTHOR_USERNAME).orEmpty(),
            content = document.getString(FIELD_CONTENT).orEmpty(),
            imageUrl = document.getString(FIELD_IMAGE_URL).orEmpty(),
            category = AssetCategory.fromId(document.getString(FIELD_CATEGORY)),
            status = document.getString(FIELD_STATUS) ?: STATUS_PUBLISHED,
            likeCount = document.getLong(FIELD_LIKE_COUNT) ?: 0L,
            commentCount = document.getLong(FIELD_COMMENT_COUNT) ?: 0L,
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )
    }
}
