package com.socialvest.app.ui.social

import android.content.Intent
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
import com.socialvest.app.databinding.FragmentSocialBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.viewmodel.SocialUiState
import com.socialvest.app.viewmodel.SocialViewModel
import kotlinx.coroutines.launch

/**
 * Phase 4 Social tab: a community feed of published posts with likes and
 * comments. Posts are user opinions and are labelled as such; no portfolio
 * value is ever shown here.
 */
class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SocialViewModel by viewModels { SocialViewModel.Factory }
    private var postAdapter: PostAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PostAdapter(
            onPostClick = { row ->
                startActivity(PostDetailActivity.intent(requireContext(), row.post.id))
            },
            onLikeClick = { row -> viewModel.toggleLike(row.post.id) },
            onAuthorClick = { row ->
                startActivity(
                    PublicProfileActivity.intent(requireContext(), row.post.userId)
                )
            }
        )
        postAdapter = adapter
        binding.listPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.listPosts.adapter = adapter

        binding.fabCreatePost.setOnClickListener {
            startActivity(CreatePostActivity.intent(requireContext()))
        }
        binding.buttonLoadMore.setOnClickListener { viewModel.loadMore() }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    /**
     * Likes made on the detail screen cannot be observed from here, so the
     * heart state is re-read whenever the feed becomes visible again.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshLikedState()
    }

    private fun render(state: SocialUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        val hasPosts = state.rows.isNotEmpty()
        binding.progressSocial.isVisible = state.isLoading
        binding.listPosts.isVisible = hasPosts
        binding.groupEmpty.isVisible = !state.isLoading && !hasPosts && state.errorRes == null
        binding.buttonLoadMore.isVisible = state.hasMore

        postAdapter?.submitList(state.rows)

        state.errorRes?.let { messageRes ->
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
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
        binding.listPosts.adapter = null
        postAdapter = null
        super.onDestroyView()
        _binding = null
    }
}
