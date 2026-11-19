package com.socialvest.app.domain.learning

import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.data.model.ModuleProgress

/**
 * Pure learning rules shared by the ViewModels and the unit tests.
 *
 * Like the social feed, the course list is queried without `orderBy` so no
 * composite index has to be created before the screen can load.
 */
object Learning {

    /** Upper bound of courses kept in memory for one session. */
    const val MAX_COURSES = 50

    fun sortNewestFirst(courses: List<Course>): List<Course> = courses.sortedWith(
        compareByDescending<Course> { it.createdAt != null }
            .thenByDescending { it.createdAt?.seconds ?: 0L }
            .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
            .thenBy { it.sortOrder }
            .thenByDescending { it.id }
    )

    fun orderedModules(modules: List<CourseModule>): List<CourseModule> =
        modules.sortedWith(compareBy<CourseModule> { it.order }.thenBy { it.id })

    /** Never divides by zero: a course without modules is 0% complete. */
    fun progressPercent(completed: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((completed.toLong() * 100L) / total)
            .coerceIn(0L, 100L)
            .toInt()
    }

    fun countCompleted(modules: List<CourseModule>, progress: List<ModuleProgress>): Int {
        if (modules.isEmpty()) return 0
        val completedIds = progress.filter { it.completed }.map { it.moduleId }.toSet()
        return modules.count { it.id in completedIds }
    }

    fun isCourseComplete(modules: List<CourseModule>, progress: List<ModuleProgress>): Boolean =
        modules.isNotEmpty() && countCompleted(modules, progress) == modules.size

    /**
     * Short Indonesian duration label, e.g. `45 detik`, `10 menit`, `1 jam 5 menit`.
     * Unknown durations are rendered as an empty string (the UI hides the label).
     */
    fun durationLabel(seconds: Long): String {
        if (seconds <= 0L) return ""
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return when {
            hours > 0L && minutes > 0L -> "$hours jam $minutes menit"
            hours > 0L -> "$hours jam"
            minutes > 0L -> "$minutes menit"
            else -> "$seconds detik"
        }
    }
}
