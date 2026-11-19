package com.socialvest.app.ui.learning

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.socialvest.app.R
import com.socialvest.app.databinding.FragmentLearnBinding
import com.socialvest.app.domain.learning.StarterCatalog
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.viewmodel.LearnUiState
import com.socialvest.app.viewmodel.LearnViewModel
import kotlinx.coroutines.launch

/**
 * Phase 5 Learning Center: the catalogue of published courses plus the caller's
 * enrollment/lesson progress. Courses are read-only and seeded from the
 * Firebase Console, so an empty list is a valid, honest state.
 */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LearnViewModel by viewModels { LearnViewModel.Factory }
    private var courseAdapter: CourseAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CourseAdapter { row ->
            startActivity(CourseDetailActivity.intent(requireContext(), row.course.id))
        }
        courseAdapter = adapter
        binding.listCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.listCourses.adapter = adapter

        binding.buttonRetry.setOnClickListener { viewModel.retry() }
        binding.textSourceCaption.text = getString(
            R.string.learn_source_caption,
            StarterCatalog.modules(StarterCatalog.COURSE_ID)?.size ?: 0,
            StarterCatalog.CHANNEL_NAME
        )
        binding.buttonPlaylist.setOnClickListener { openPlaylist() }

        observeState()
    }

    private fun openPlaylist() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(StarterCatalog.PLAYLIST_URL)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                R.string.learn_source_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: LearnUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        val hasCourses = state.rows.isNotEmpty()

        binding.progressLearn.isVisible = state.isLoading
        binding.listCourses.isVisible = hasCourses
        binding.textCourseCount.isVisible = hasCourses
        binding.groupEmpty.isVisible =
            !state.isLoading && !hasCourses && state.errorRes == null
        binding.textError.isVisible = state.errorRes != null
        binding.buttonRetry.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.setText(it) }

        if (hasCourses) {
            binding.textCourseCount.text =
                getString(R.string.learn_course_count, state.rows.size)
        }

        courseAdapter?.submitList(state.rows)
    }

    private fun goToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        requireActivity().finish()
    }

    override fun onDestroyView() {
        binding.listCourses.adapter = null
        courseAdapter = null
        super.onDestroyView()
        _binding = null
    }
}
