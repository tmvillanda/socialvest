package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.repository.LearningRepository
import com.socialvest.app.domain.learning.Learning
import com.socialvest.app.domain.learning.StarterCatalog
import com.socialvest.app.utils.ErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CourseRow(
    val course: Course,
    val enrolled: Boolean,
    val completedModules: Int,
    /** Known for bundled courses; remote course detail is loaded when opened. */
    val totalModules: Int = 0
)

data class LearnUiState(
    val isLoading: Boolean = true,
    val rows: List<CourseRow> = emptyList(),
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
)

/**
 * Course catalogue. Courses, enrollments and progress are combined in memory so
 * the screen needs three index-free listeners and no joins.
 */
class LearnViewModel(
    private val repository: LearningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            observe()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = combine(
            repository.coursesFlow(),
            repository.enrollmentsFlow(),
            repository.progressFlow()
        ) { courses, enrollments, progress ->
            val enrolledIds = enrollments.map { it.courseId }.toSet()
            val completedByCourse = progress
                .filter { it.completed }
                .groupingBy { it.courseId }
                .eachCount()

            Learning.sortNewestFirst(courses).map { course ->
                CourseRow(
                    course = course,
                    enrolled = course.id in enrolledIds,
                    completedModules = completedByCourse[course.id] ?: 0,
                    totalModules = StarterCatalog.modules(course.id)?.size ?: 0
                )
            }
        }
            .onEach { rows ->
                _uiState.update {
                    it.copy(isLoading = false, rows = rows, errorRes = null)
                }
            }
            .catch { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorRes = ErrorMapper.messageRes(throwable)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Re-subscribes after a failure (a caught flow stops emitting for good). */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        observe()
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                LearnViewModel(app.learningRepository)
            }
        }
    }
}
