package com.socialvest.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.socialvest.app.SocialvestApp
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.Post
import com.socialvest.app.data.repository.AuthRepository
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.data.repository.LearningRepository
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.domain.PortfolioSummary
import com.socialvest.app.domain.learning.Learning
import com.socialvest.app.domain.social.SocialFeed
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val summary: PortfolioSummary = PortfolioSummary(),
    val posts: List<Post> = emptyList(),
    val courses: List<Course> = emptyList(),
    val errorRes: Int? = null,
    val isSignedOut: Boolean = false
)

/**
 * Home dashboard: greeting, the caller's own portfolio totals, the newest
 * community posts and featured courses.
 *
 * Only the signed-in user's portfolio is read (filtered by UID), and nothing
 * from it is ever written to a public document. Posts/courses are fetched with
 * small limits because Home only shows a preview of each.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val investmentRepository: InvestmentRepository,
    private val socialRepository: SocialRepository,
    private val learningRepository: LearningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        if (authRepository.currentUserId() == null) {
            _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
        } else {
            observe()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = combine(
            authRepository.profileFlow(authRepository.currentUserId().orEmpty()),
            investmentRepository.assetsFlow(),
            socialRepository.postsFlow(HOME_POSTS.toLong()),
            learningRepository.coursesFlow(HOME_COURSES.toLong())
        ) { profile, assets, posts, courses ->
            HomeUiState(
                isLoading = false,
                name = profile?.fullName?.takeIf { it.isNotBlank() }
                    ?: profile?.username.orEmpty(),
                username = profile?.username.orEmpty(),
                email = authRepository.currentEmail(),
                summary = PortfolioSummary.of(assets),
                posts = SocialFeed.sortNewestFirst(posts).take(HOME_POSTS),
                courses = Learning.sortNewestFirst(courses).take(HOME_COURSES)
            )
        }
            .onEach { state -> _uiState.update { state } }
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

    companion object {
        /** Home only shows a preview; the full lists live in the Social/Learn tabs. */
        const val HOME_POSTS = 3
        const val HOME_COURSES = 2

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SocialvestApp
                HomeViewModel(
                    authRepository = app.authRepository,
                    investmentRepository = app.investmentRepository,
                    socialRepository = app.socialRepository,
                    learningRepository = app.learningRepository
                )
            }
        }
    }
}
