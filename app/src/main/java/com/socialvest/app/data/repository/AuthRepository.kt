package com.socialvest.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.socialvest.app.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The only place that talks to Firebase Authentication and the `users` collection.
 * Ownership is always derived from the authenticated UID, never from UI input.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val usersCollection get() = firestore.collection(User.COLLECTION)

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentEmail(): String = auth.currentUser?.email.orEmpty()

    /** Live profile document. Emits `null` when the document does not exist. */
    fun profileFlow(uid: String): Flow<User?> = callbackFlow {
        val registration = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.takeIf { it.exists() }?.let(User::fromSnapshot)
                trySend(user)
            }
        awaitClose { registration.remove() }
    }

    suspend fun loadProfile(uid: String): User? {
        val snapshot = usersCollection.document(uid).get().await()
        return if (snapshot.exists()) User.fromSnapshot(snapshot) else null
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /**
     * Creates the Authentication account and then the profile document.
     * If the profile write fails the Authentication account is rolled back so the
     * user is not left in a half-registered state.
     */
    suspend fun register(fullName: String, username: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = result.user
            ?: throw IllegalStateException("Akun dibuat tetapi data pengguna tidak tersedia.")

        try {
            usersCollection.document(firebaseUser.uid)
                .set(newUserDocument(firebaseUser.uid, fullName, username))
                .await()

            firebaseUser.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName.trim())
                    .build()
            ).await()
        } catch (throwable: Throwable) {
            runCatching { firebaseUser.delete().await() }
            runCatching { auth.signOut() }
            throw throwable
        }
    }

    /**
     * Updates only the editable profile fields. `uid`, `role`, `isActive` and
     * `createdAt` are left untouched so the security rules stay satisfied.
     * Creates the document when it is missing (e.g. registration was interrupted).
     */
    suspend fun saveProfile(uid: String, fullName: String, username: String, bio: String) {
        val document = usersCollection.document(uid)
        val snapshot = document.get().await()

        if (snapshot.exists()) {
            document.update(
                mapOf(
                    User.FIELD_FULL_NAME to fullName.trim(),
                    User.FIELD_USERNAME to username.trim().lowercase(),
                    User.FIELD_BIO to bio.trim()
                )
            ).await()
        } else {
            document.set(newUserDocument(uid, fullName, username, bio)).await()
        }

        runCatching {
            auth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName.trim())
                    .build()
            )?.await()
        }
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * Sends a Firebase password-reset email to the signed-in account. The app
     * never sees or stores the password; the link is handled by Firebase.
     */
    suspend fun sendPasswordResetEmail() {
        val email = currentEmail()
        check(email.isNotBlank()) { "Email akun tidak tersedia." }
        auth.sendPasswordResetEmail(email).await()
    }

    private fun newUserDocument(
        uid: String,
        fullName: String,
        username: String,
        bio: String = ""
    ): Map<String, Any> = mapOf(
        User.FIELD_UID to uid,
        User.FIELD_FULL_NAME to fullName.trim(),
        User.FIELD_USERNAME to username.trim().lowercase(),
        User.FIELD_PHOTO_URL to "",
        User.FIELD_BIO to bio.trim(),
        User.FIELD_ROLE to User.ROLE_USER,
        User.FIELD_IS_ACTIVE to true,
        User.FIELD_CREATED_AT to FieldValue.serverTimestamp()
    )
}
