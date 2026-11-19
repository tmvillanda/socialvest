package com.socialvest.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.data.model.Enrollment
import com.socialvest.app.data.model.ModuleProgress
import com.socialvest.app.domain.learning.Learning
import com.socialvest.app.domain.learning.StarterCatalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Read-only access to the seeded `courses` collection plus the per-user
 * `enrollments` and `progress` documents.
 *
 * Progress/enrollment documents are never fetched by id when they might not
 * exist: a missing document fails `resource.data.userId == uid()` and returns
 * `PERMISSION_DENIED`. All reads here use equality-filtered queries instead.
 */
class LearningRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val courses get() = firestore.collection(Course.COLLECTION)

    fun currentUserId(): String? = auth.currentUser?.uid

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sesi berakhir. Silakan masuk kembali.")

    /** Published courses only, with the bundled playlist catalogue appended. */
    fun coursesFlow(limit: Long = Learning.MAX_COURSES.toLong()): Flow<List<Course>> = callbackFlow {
        val registration = courses
            .whereEqualTo(FIELD_IS_PUBLISHED, true)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(Course::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { remote -> StarterCatalog.mergeCourses(remote) }

    fun courseFlow(courseId: String): Flow<Course?> {
        StarterCatalog.course(courseId)?.let { return flowOf(it) }
        return callbackFlow {
            val registration = courses.document(courseId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.takeIf { it.exists() }?.let(Course::fromSnapshot))
                }
            awaitClose { registration.remove() }
        }
    }

    fun modulesFlow(courseId: String): Flow<List<CourseModule>> {
        StarterCatalog.modules(courseId)?.let { return flowOf(it) }
        return callbackFlow {
            val registration = courses.document(courseId)
                .collection(CourseModule.COLLECTION)
                .orderBy(FIELD_ORDER, Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.map(CourseModule::fromSnapshot).orEmpty())
                }
            awaitClose { registration.remove() }
        }
    }

    fun moduleFlow(courseId: String, moduleId: String): Flow<CourseModule?> {
        StarterCatalog.module(courseId, moduleId)?.let { return flowOf(it) }
        return callbackFlow {
            val registration = courses.document(courseId)
                .collection(CourseModule.COLLECTION)
                .document(moduleId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.takeIf { it.exists() }?.let(CourseModule::fromSnapshot))
                }
            awaitClose { registration.remove() }
        }
    }

    fun enrollmentsFlow(): Flow<List<Enrollment>> = callbackFlow {
        val uid = requireUserId()
        val registration = firestore.collection(Enrollment.COLLECTION)
            .whereEqualTo(FIELD_USER_ID, uid)
            .limit(MAX_ENROLLMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(Enrollment::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /** The caller's enrollment for one course, or null when not enrolled yet. */
    fun enrollmentFlow(courseId: String): Flow<Enrollment?> = callbackFlow {
        val uid = requireUserId()
        val registration = firestore.collection(Enrollment.COLLECTION)
            .whereEqualTo(FIELD_USER_ID, uid)
            .whereEqualTo(FIELD_COURSE_ID, courseId)
            .limit(1L)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents?.firstOrNull()?.let(Enrollment::fromSnapshot)
                )
            }
        awaitClose { registration.remove() }
    }

    /** Every lesson the caller has touched, used for catalog counters. */
    fun progressFlow(): Flow<List<ModuleProgress>> = callbackFlow {
        val uid = requireUserId()
        val registration = firestore.collection(ModuleProgress.COLLECTION)
            .whereEqualTo(FIELD_USER_ID, uid)
            .limit(MAX_PROGRESS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(ModuleProgress::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun courseProgressFlow(courseId: String): Flow<List<ModuleProgress>> = callbackFlow {
        val uid = requireUserId()
        val registration = firestore.collection(ModuleProgress.COLLECTION)
            .whereEqualTo(FIELD_USER_ID, uid)
            .whereEqualTo(FIELD_COURSE_ID, courseId)
            .limit(MAX_PROGRESS_PER_COURSE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map(ModuleProgress::fromSnapshot).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Creates or refreshes the enrollment. `set(merge)` avoids a read on a
     * possibly missing document, which the rules would reject.
     */
    suspend fun enroll(courseId: String, status: String) {
        val uid = requireUserId()
        val documentId = Enrollment.documentId(uid, courseId)
        firestore.collection(Enrollment.COLLECTION)
            .document(documentId)
            .set(
                mapOf(
                    FIELD_USER_ID to uid,
                    FIELD_COURSE_ID to courseId,
                    FIELD_STATUS to status,
                    FIELD_ENROLLED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateEnrollmentStatus(courseId: String, status: String) {
        val uid = requireUserId()
        firestore.collection(Enrollment.COLLECTION)
            .document(Enrollment.documentId(uid, courseId))
            .set(
                mapOf(FIELD_STATUS to status),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun setModuleCompleted(courseId: String, moduleId: String, completed: Boolean) {
        val uid = requireUserId()
        firestore.collection(ModuleProgress.COLLECTION)
            .document(ModuleProgress.documentId(uid, courseId, moduleId))
            .set(
                mapOf(
                    FIELD_USER_ID to uid,
                    FIELD_COURSE_ID to courseId,
                    FIELD_MODULE_ID to moduleId,
                    FIELD_COMPLETED to completed,
                    FIELD_LAST_POSITION to 0L,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    companion object {
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_COURSE_ID = "courseId"
        private const val FIELD_MODULE_ID = "moduleId"
        private const val FIELD_STATUS = "status"
        private const val FIELD_ENROLLED_AT = "enrolledAt"
        private const val FIELD_COMPLETED = "completed"
        private const val FIELD_LAST_POSITION = "lastPositionSeconds"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_ORDER = "order"
        private const val FIELD_IS_PUBLISHED = "isPublished"

        private const val MAX_ENROLLMENTS = 100L
        private const val MAX_PROGRESS = 500L
        private const val MAX_PROGRESS_PER_COURSE = 100L
    }
}
