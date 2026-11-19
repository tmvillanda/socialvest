package com.socialvest.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.model.Comment
import com.socialvest.app.data.model.Post
import com.socialvest.app.data.model.User
import com.socialvest.app.domain.social.SocialFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The only place that talks to the `posts` collection and its subcollections.
 *
 * Likes use the UID as document ID, which makes duplicate likes impossible at
 * the database level. Counters are changed in the same transaction/batch as the
 * child document so the card never drifts from the real number of likes or
 * comments.
 */
class SocialRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val posts get() = firestore.collection(Post.COLLECTION)

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentUserName(): String =
        auth.currentUser?.displayName.orEmpty()

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sesi berakhir. Silakan masuk kembali.")

    /** Published posts, newest first handled by [SocialFeed]. Index-free query. */
    fun postsFlow(limit: Long = SocialFeed.MAX_POSTS.toLong()): Flow<List<Post>> = callbackFlow {
        val registration = posts
            .whereEqualTo(Post.FIELD_STATUS, Post.STATUS_PUBLISHED)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(Post::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun postFlow(postId: String): Flow<Post?> = callbackFlow {
        val registration = posts.document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.let(Post::fromSnapshot))
            }
        awaitClose { registration.remove() }
    }

    fun commentsFlow(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = posts.document(postId)
            .collection(Comment.COLLECTION)
            .orderBy(Comment.FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(Comment::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /** Which of [postIds] the signed-in user has already liked. */
    suspend fun likedPostIds(postIds: Collection<String>): Set<String> = coroutineScope {
        val uid = requireUserId()
        postIds.distinct()
            .filter { it.isNotBlank() }
            .map { postId ->
                async {
                    val exists = posts.document(postId)
                        .collection(LIKE_COLLECTION)
                        .document(uid)
                        .get()
                        .await()
                        .exists()
                    postId to exists
                }
            }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
            .toSet()
    }

    /** Adds or removes the caller's like and moves the counter by exactly one. */
    suspend fun toggleLike(postId: String): Boolean {
        val uid = requireUserId()
        val postRef = posts.document(postId)
        val likeRef = postRef.collection(LIKE_COLLECTION).document(uid)

        return firestore.runTransaction { transaction ->
            val post = transaction.get(postRef)
            if (!post.exists()) {
                throw IllegalStateException("Postingan tidak ditemukan atau sudah dihapus.")
            }
            val current = post.getLong(Post.FIELD_LIKE_COUNT) ?: 0L
            val like = transaction.get(likeRef)

            if (like.exists()) {
                transaction.delete(likeRef)
                transaction.update(
                    postRef,
                    Post.FIELD_LIKE_COUNT,
                    (current - 1L).coerceAtLeast(0L)
                )
                false
            } else {
                transaction.set(
                    likeRef,
                    mapOf(
                        Post.FIELD_USER_ID to uid,
                        Post.FIELD_CREATED_AT to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(postRef, Post.FIELD_LIKE_COUNT, current + 1L)
                true
            }
        }.await()
    }

    suspend fun addComment(postId: String, content: String) {
        val uid = requireUserId()
        val author = loadAuthor(uid)
        val postRef = posts.document(postId)
        val commentRef = postRef.collection(Comment.COLLECTION).document()

        firestore.runTransaction { transaction ->
            val post = transaction.get(postRef)
            if (!post.exists()) {
                throw IllegalStateException("Postingan tidak ditemukan atau sudah dihapus.")
            }
            val count = post.getLong(Post.FIELD_COMMENT_COUNT) ?: 0L

            transaction.set(
                commentRef,
                mapOf(
                    Comment.FIELD_USER_ID to uid,
                    Comment.FIELD_AUTHOR_NAME to author.first,
                    Comment.FIELD_AUTHOR_USERNAME to author.second,
                    Comment.FIELD_CONTENT to content.trim(),
                    Comment.FIELD_CREATED_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.update(postRef, Post.FIELD_COMMENT_COUNT, count + 1L)
        }.await()
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        requireUserId()
        val postRef = posts.document(postId)

        firestore.runTransaction { transaction ->
            val post = transaction.get(postRef)
            val count = post.getLong(Post.FIELD_COMMENT_COUNT) ?: 0L

            transaction.delete(postRef.collection(Comment.COLLECTION).document(commentId))
            if (post.exists()) {
                transaction.update(
                    postRef,
                    Post.FIELD_COMMENT_COUNT,
                    (count - 1L).coerceAtLeast(0L)
                )
            }
        }.await()
    }

    /** Creates a published post and returns its id. */
    suspend fun createPost(content: String, category: AssetCategory): String {
        val uid = requireUserId()
        val author = loadAuthor(uid)
        val document = posts.document()

        document.set(
            mapOf(
                Post.FIELD_USER_ID to uid,
                Post.FIELD_AUTHOR_NAME to author.first,
                Post.FIELD_AUTHOR_USERNAME to author.second,
                Post.FIELD_CONTENT to content.trim(),
                Post.FIELD_IMAGE_URL to "",
                Post.FIELD_CATEGORY to category.id,
                Post.FIELD_STATUS to Post.STATUS_PUBLISHED,
                Post.FIELD_LIKE_COUNT to 0L,
                Post.FIELD_COMMENT_COUNT to 0L,
                Post.FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
        ).await()

        return document.id
    }

    /** Reads one post once (used to prefill the edit form). */
    suspend fun getPost(postId: String): Post? =
        posts.document(postId).get().await()
            .takeIf { it.exists() }
            ?.let(Post::fromSnapshot)

    /**
     * Owner-only edit. The rules only allow `content`/`category` to change while
     * the counters and `createdAt` stay untouched, so likes/comments stay valid.
     */
    suspend fun updatePost(postId: String, content: String, category: AssetCategory) {
        requireUserId()
        posts.document(postId).update(
            mapOf(
                Post.FIELD_CONTENT to content.trim(),
                Post.FIELD_CATEGORY to category.id
            )
        ).await()
    }

    /**
     * Deletes the post together with its comments. Likes of other users cannot
     * be deleted by the post owner under the current rules, so they are left
     * behind (unreachable from the UI) — documented as a limitation.
     */
    suspend fun deletePost(postId: String) {
        requireUserId()
        val postRef = posts.document(postId)

        val comments = postRef.collection(Comment.COLLECTION).get().await().documents
        comments.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        postRef.delete().await()
    }

    fun profileFlow(uid: String): Flow<User?> = callbackFlow {
        val registration = firestore.collection(User.COLLECTION).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.let(User::fromSnapshot))
            }
        awaitClose { registration.remove() }
    }

    private suspend fun loadAuthor(uid: String): Pair<String, String> {
        val snapshot = firestore.collection(User.COLLECTION).document(uid).get().await()
        if (!snapshot.exists()) {
            return Post.FALLBACK_AUTHOR to ""
        }
        val user = User.fromSnapshot(snapshot)
        val name = user.fullName.ifBlank { user.username }.ifBlank { Post.FALLBACK_AUTHOR }
        return name to user.username
    }

    companion object {
        const val LIKE_COLLECTION = "likes"
        private const val BATCH_LIMIT = 400
    }
}
