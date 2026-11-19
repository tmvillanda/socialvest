package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.R
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.data.model.Enrollment
import com.socialvest.app.data.model.ModuleProgress
import com.socialvest.app.data.repository.LearningRepository
import com.socialvest.app.domain.learning.Learning
import com.socialvest.app.domain.learning.YoutubeVideo
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

data class ModulePlayerUiState(
    val isLoading: Boolean = true,
    val courseTitle: String = "",
    val module: CourseModule? = null,
    val modules: List<CourseModule> = emptyList(),
    val progress: List<ModuleProgress> = emptyList(),
    val videoId: String? = null,
    val isSaving: Boolean = false,
    val messageRes: Int? = null,
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
) {
    val completed: Boolean
        get() = module != null && progress.any { it.moduleId == module.id && it.completed }

    val durationLabel: String
        get() = Learning.durationLabel(module?.durationSeconds ?: 0L)
}

/**
 * One lesson player. Only completion state is stored; the video itself is
 * embedded from YouTube and never downloaded.
 */
class ModulePlayerViewModel(
    private val repository: LearningRepository,
    private val courseId: String,
    private val moduleId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModulePlayerUiState())
    val uiState: StateFlow<ModulePlayerUiState> = _uiState.asStateFlow()

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
            repository.moduleFlow(courseId, moduleId),
            repository.modulesFlow(courseId),
            repository.courseProgressFlow(courseId)
        ) { course, module, modules, progress ->
            ModulePlayerUiState(
                isLoading = false,
                courseTitle = course?.title.orEmpty(),
                module = module,
                modules = Learning.orderedModules(modules),
                progress = progress,
                videoId = module?.let(YoutubeVideo::idOf)
            )
        }
            .onEach { state ->
                _uiState.update { current ->
                    state.copy(
                        isSaving = current.isSaving,
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

    fun setCompleted(completed: Boolean) {
        val state = _uiState.value
        val module = state.module ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                repository.enroll(courseId, Enrollment.STATUS_IN_PROGRESS)
                repository.setModuleCompleted(courseId, module.id, completed)
                syncEnrollmentStatus(state, module.id, completed)
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            messageRes = if (completed) {
                                R.string.module_marked_done
                            } else {
                                R.string.module_marked_undone
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorRes = ErrorMapper.messageRes(throwable)
                        )
                    }
                }
        }
    }

    private suspend fun syncEnrollmentStatus(
        state: ModulePlayerUiState,
        moduleId: String,
        completed: Boolean
    ) {
        if (state.modules.isEmpty()) return
        val completedIds = state.progress
            .filter { it.completed }
            .map { it.moduleId }
            .toMutableSet()
        if (completed) completedIds.add(moduleId) else completedIds.remove(moduleId)

        val status = if (state.modules.all { it.id in completedIds }) {
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
        fun factory(courseId: String, moduleId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as SocialvestApp
                    ModulePlayerViewModel(app.learningRepository, courseId, moduleId)
                }
            }
    }
}
