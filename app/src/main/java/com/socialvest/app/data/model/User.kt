package com.socialvest.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Public profile document stored at `users/{uid}`.
 * `email` is intentionally not stored here: Firebase Authentication already
 * owns it, and this document is readable by other signed-in users.
 */
data class User(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val role: String = ROLE_USER,
    val isActive: Boolean = true,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val COLLECTION = "users"

        fun fromSnapshot(document: DocumentSnapshot): User = User(
            uid = document.getString(FIELD_UID) ?: document.id,
            fullName = document.getString(FIELD_FULL_NAME).orEmpty(),
            username = document.getString(FIELD_USERNAME).orEmpty(),
            photoUrl = document.getString(FIELD_PHOTO_URL).orEmpty(),
            bio = document.getString(FIELD_BIO).orEmpty(),
            role = document.getString(FIELD_ROLE) ?: ROLE_USER,
            isActive = document.getBoolean(FIELD_IS_ACTIVE) ?: true,
            createdAt = document.getTimestamp(FIELD_CREATED_AT)
        )

        const val FIELD_UID = "uid"
        const val FIELD_FULL_NAME = "fullName"
        const val FIELD_USERNAME = "username"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_BIO = "bio"
        const val FIELD_ROLE = "role"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
