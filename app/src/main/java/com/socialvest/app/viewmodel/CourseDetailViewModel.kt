package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.data.model.Enrollment
import com.socialvest.app.data.model.ModuleProgress
import com.socialvest.app.data.repository.LearningRepository
import com.socialvest.app.domain.learning.Learning
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

data class CourseDetailUiState(
    val isLoading: Boolean = true,
    val course: Course? = null,
    val modules: List<CourseModule> = emptyList(),
    val progress: List<ModuleProgress> = emptyList(),
    val enrollment: Enrollment? = null,
    val isEnrolling: Boolean = false,
    val isUpdatingProgress: Boolean = false,
    val messageRes: Int? = null,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
) {
    val enrolled: Boolean get() = enrollment != null
    val completedCount: Int get() = Learning.countCompleted(modules, progress)
    val totalModules: Int get() = modules.size
    val percent: Int get() = Learning.progressPercent(completedCount, totalModules)
    val isComplete: Boolean get() = Learning.isCourseComplete(modules, progress)

    fun isModuleCompleted(moduleId: String): Boolean =
        progress.any { it.moduleId == moduleId && it.completed }
}

/** One course with its modules, enrollment, and per-module progress. */
class CourseDetailViewModel(
    private val repository: LearningRepository,
    private val courseId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

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
            repository.courseFlow(courseId),
            repository.modulesFlow(courseId),
            repository.courseProgressFlow(courseId),
            repository.enrollmentFlow(courseId)
        ) { course, modules, progress, enrollment ->
            CourseDetailUiState(
                isLoading = false,
                course = course,
                modules = Learning.orderedModules(modules),
                progress = progress,
                enrollment = enrollment
            )
        }
            .onEach { state ->
                _uiState.update { current ->
                    state.copy(
                        isEnrolling = current.isEnrolling,
                        isUpdatingProgress = current.isUpdatingProgress,
                        messageRes = current.messageRes
                    )
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

    fun enroll() {
        val state = _uiState.value
        if (state.isEnrolling || state.enrolled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEnrolling = true) }
            val status = if (state.isComplete) {
                Enrollment.STATUS_COMPLETED
            } else {
                Enrollment.STATUS_IN_PROGRESS
            }
            runCatching { repository.enroll(courseId, status) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isEnrolling = false,
                            messageRes = R.string.course_detail_enroll_success
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isEnrolling = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    fun toggleModuleCompleted(moduleId: String, completed: Boolean) {
        val state = _uiState.value
        if (moduleId.isBlank() || state.isUpdatingProgress) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingProgress = true) }
            runCatching {
                if (!state.enrolled) {
                    repository.enroll(courseId, Enrollment.STATUS_IN_PROGRESS)
                }
                repository.setModuleCompleted(courseId, moduleId, completed)
                syncEnrollmentStatus(state.modules, state.progress, moduleId, completed)
            }
                .onSuccess { _uiState.update { it.copy(isUpdatingProgress = false) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isUpdatingProgress = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    private suspend fun syncEnrollmentStatus(
        modules: List<CourseModule>,
        progress: List<ModuleProgress>,
        moduleId: String,
        completed: Boolean
    ) {
        if (modules.isEmpty()) return
        val completedIds = progress.filter { it.completed }.map { it.moduleId }.toMutableSet()
        if (completed) completedIds.add(moduleId) else completedIds.remove(moduleId)

        val status = if (modules.all { it.id in completedIds }) {
            Enrollment.STATUS_COMPLETED
        } else {
            Enrollment.STATUS_IN_PROGRESS
        }
        repository.updateEnrollmentStatus(courseId, status)
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(messageRes = null) }
    }

    /** Re-subscribes after a failure (a caught flow stops emitting for good). */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        observe()
    }

    companion object {
        fun factory(courseId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                CourseDetailViewModel(app.learningRepository, courseId)
            }
        }
    }
}
