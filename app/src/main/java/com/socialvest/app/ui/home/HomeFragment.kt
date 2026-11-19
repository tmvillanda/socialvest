package com.socialvest.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.socialvest.app.R
import com.socialvest.app.databinding.FragmentHomeBinding
import com.socialvest.app.domain.PortfolioSummary
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.ui.learning.CourseDetailActivity
import com.socialvest.app.ui.social.PostDetailActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials
import com.socialvest.app.viewmodel.HomeUiState
import com.socialvest.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Phase 6 Home dashboard: greeting, the caller's own portfolio summary, the
 * newest community posts and featured courses, plus shortcuts into the other
 * tabs. Prices are the last stored ones (manual or refreshed), never a
 * real-time feed.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    private var postAdapter: HomePostAdapter? = null
    private var courseAdapter: HomeCourseAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val posts = HomePostAdapter { post ->
            startActivity(PostDetailActivity.intent(requireContext(), post.id))
        }
        postAdapter = posts
        binding.listPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.listPosts.adapter = posts

        val courses = HomeCourseAdapter { course ->
            startActivity(CourseDetailActivity.intent(requireContext(), course.id))
        }
        courseAdapter = courses
        binding.listCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.listCourses.adapter = courses

        binding.buttonOpenInvest.setOnClickListener { openTab(R.id.portfolioFragment) }
        binding.buttonShortcutInvest.setOnClickListener { openTab(R.id.portfolioFragment) }
        binding.buttonShortcutSocial.setOnClickListener { openTab(R.id.socialFragment) }
        binding.buttonShortcutLearn.setOnClickListener { openTab(R.id.learnFragment) }
        binding.buttonAllPosts.setOnClickListener { openTab(R.id.socialFragment) }
        binding.buttonAllCourses.setOnClickListener { openTab(R.id.learnFragment) }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        observeState()
    }

    override fun onDestroyView() {
        binding.listPosts.adapter = null
        binding.listCourses.adapter = null
        postAdapter = null
        courseAdapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: HomeUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressHome.isVisible = state.isLoading
        binding.textError.isVisible = state.errorRes != null
        binding.buttonRetry.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.setText(it) }

        binding.groupContent.isVisible = !state.isLoading && state.errorRes == null

        val displayName = state.name.ifBlank { state.username }
        binding.textGreeting.text = if (displayName.isBlank()) {
            getString(R.string.home_greeting_anonymous)
        } else {
            getString(R.string.home_greeting, displayName)
        }

        val account = listOfNotNull(
            state.username.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.home_username_format, it) },
            state.email.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        binding.textAccount.isVisible = account.isNotBlank()
        binding.textAccount.text = account
        binding.avatarInitials.text = Initials.of(state.name, state.username, state.email)

        renderPortfolio(state.summary)

        binding.textPostsEmpty.isVisible = state.posts.isEmpty()
        binding.listPosts.isVisible = state.posts.isNotEmpty()
        postAdapter?.submitList(state.posts)

        binding.textCoursesEmpty.isVisible = state.courses.isEmpty()
        binding.listCourses.isVisible = state.courses.isNotEmpty()
        courseAdapter?.submitList(state.courses)
    }

    private fun renderPortfolio(summary: PortfolioSummary) {
        val hasAssets = summary.hasAssets

        binding.groupMetrics.isVisible = hasAssets
        binding.textNoAssets.isVisible = !hasAssets
        binding.buttonOpenInvest.isVisible = !hasAssets

        binding.textTotalValue.text = if (hasAssets) {
            Display.rupiah(summary.totalValue)
        } else {
            getString(R.string.value_undefined)
        }

        if (!hasAssets) return

        binding.textTotalCost.text = Display.rupiah(summary.totalCost)
        binding.textUnrealized.text = Display.signedRupiah(summary.totalUnrealized)
        binding.textRealized.text = Display.signedRupiah(summary.totalRealized)
        binding.textReturn.text = getString(
            R.string.invest_return_format,
            Display.percent(summary.returnPercent)
                ?: getString(R.string.value_undefined)
        )
    }

    /** Switches the shared bottom navigation instead of starting a second host. */
    private fun openTab(itemId: Int) {
        requireActivity()
            .findViewById<BottomNavigationView>(R.id.bottom_nav)
            ?.selectedItemId = itemId
    }

    private fun goToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        requireActivity().finish()
    }
}
