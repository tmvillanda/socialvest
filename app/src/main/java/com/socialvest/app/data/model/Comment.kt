package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/** One comment stored at `posts/{postId}/comments/{commentId}`. */
data class Comment(
    val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val content: String = "",
    val createdAt: Timestamp? = null
) {
    val authorDisplayName: String
        get() = authorName.ifBlank {
            authorUsername.ifBlank { Post.FALLBACK_AUTHOR }
        }

    val createdAtMillis: Long? get() = createdAt?.toDate()?.time

    companion object {
        const val COLLECTION = "comments"
        const val MAX_CONTENT_LENGTH = 1000

        const val FIELD_USER_ID = "userId"
        const val FIELD_AUTHOR_NAME = "authorName"
        const val FIELD_AUTHOR_USERNAME = "authorUsername"
        const val FIELD_CONTENT = "content"
        const val FIELD_CREATED_AT = "createdAt"

        fun fromSnapshot(document: DocumentSnapshot): Comment = Comment(
            id = document.id,
            userId = document.getString(FIELD_USER_ID).orEmpty(),
            authorName = document.getString(FIELD_AUTHOR_NAME).orEmpty(),
            authorUsername = document.getString(FIELD_AUTHOR_USERNAME).orEmpty(),
            content = document.getString(FIELD_CONTENT).orEmpty(),
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )
    }
}
