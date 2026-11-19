package com.socialvest.app.ui.social

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.data.model.Comment
import com.socialvest.app.data.model.Post
import com.socialvest.app.databinding.ActivityPostDetailBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.PostDetailUiState
import com.socialvest.app.viewmodel.PostDetailViewModel
import kotlinx.coroutines.launch

/**
 * One post with its comments. Likes and the comment counter are changed in the
 * same transaction as the like/comment document, so the numbers never drift.
 */
class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private val postId: String by lazy { intent.getStringExtra(EXTRA_POST_ID).orEmpty() }
    private val viewModel: PostDetailViewModel by viewModels {
        PostDetailViewModel.factory(postId)
    }

    private var commentAdapter: CommentAdapter? = null
    private var postOwnerId: String = ""
    private var hasFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (postId.isBlank()) {
            finish()
            return
        }

        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = CommentAdapter(
            canDelete = ::canDeleteComment,
            onDelete = ::confirmDeleteComment,
            onAuthorClick = { comment ->
                startActivity(PublicProfileActivity.intent(this, comment.userId))
            }
        )
        commentAdapter = adapter
        binding.listComments.layoutManager = LinearLayoutManager(this)
        binding.listComments.adapter = adapter

        binding.buttonLike.setOnClickListener { viewModel.toggleLike() }
        binding.avatarContainer.setOnClickListener { openPostAuthor() }
        binding.buttonEditPost.setOnClickListener { openEditor() }
        binding.buttonDeletePost.setOnClickListener { confirmDeletePost() }
        binding.buttonSendComment.setOnClickListener { sendComment() }
        binding.editComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment()
                true
            } else {
                false
            }
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: PostDetailUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressDetail.isVisible = state.isLoading && state.post == null
        binding.groupPost.isVisible = state.post != null

        binding.inputComment.error = state.commentErrorRes?.let(::getString)
        binding.buttonSendComment.isEnabled = !state.isSubmittingComment && state.post != null
        binding.editComment.isEnabled = !state.isSubmittingComment
        binding.buttonLike.isEnabled = !state.isTogglingLike && state.post != null

        commentAdapter?.submitList(state.comments)
        binding.textNoComments.isVisible = state.comments.isEmpty() && state.post != null

        state.post?.let(::bindPost)

        state.errorRes?.let { messageRes ->
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }

        if (state.commentSaved) {
            binding.editComment.setText("")
            binding.editComment.clearFocus()
            viewModel.consumeCommentSaved()
        }

        if (state.commentDeleted) {
            Toast.makeText(this, R.string.social_comment_deleted, Toast.LENGTH_SHORT).show()
            viewModel.consumeCommentDeleted()
        }

        if (state.isDeleted && !hasFinished) {
            hasFinished = true
            Toast.makeText(this, R.string.social_post_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bindPost(post: Post) {
        postOwnerId = post.userId

        binding.textAuthor.text = post.authorDisplayName
        binding.avatarInitials.text = Initials.of(post.authorName, post.authorUsername)

        val username = post.authorUsername.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.social_author_format, it) }
        val time = post.createdAtMillis?.let(Display::dateTime)
            ?: getString(R.string.social_time_unknown)
        binding.textMeta.text = listOfNotNull(username, time).joinToString(" · ")

        binding.textCategory.text = getString(post.category.labelRes)
        binding.textContent.text = post.content

        binding.buttonLike.text = post.likeCount.toString()
        binding.buttonLike.setIconResource(
            if (viewModel.uiState.value.liked) R.drawable.ic_like_filled else R.drawable.ic_like
        )
        binding.buttonLike.contentDescription = getString(
            if (viewModel.uiState.value.liked) R.string.social_unlike else R.string.social_like
        )

        binding.textCommentCount.text =
            getString(R.string.social_comment_count, post.commentCount)

        binding.buttonDeletePost.isVisible = viewModel.uiState.value.isOwner
        binding.buttonDeletePost.isEnabled = !viewModel.uiState.value.isDeletingPost
        binding.buttonEditPost.isVisible = viewModel.uiState.value.isOwner
        binding.buttonEditPost.isEnabled = !viewModel.uiState.value.isDeletingPost
    }

    private fun canDeleteComment(comment: Comment): Boolean {
        val currentUserId = viewModel.uiState.value.currentUserId
        if (currentUserId.isBlank()) return false
        return comment.userId == currentUserId || postOwnerId == currentUserId
    }

    private fun sendComment() {
        viewModel.addComment(binding.editComment.text?.toString().orEmpty())
    }

    private fun openEditor() {
        val post = viewModel.uiState.value.post ?: return
        startActivity(CreatePostActivity.editIntent(this, post.id))
    }

    private fun openPostAuthor() {
        val userId = viewModel.uiState.value.post?.userId ?: return
        if (userId.isBlank()) return
        startActivity(PublicProfileActivity.intent(this, userId))
    }

    private fun confirmDeletePost() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.social_delete_post_title)
            .setMessage(R.string.social_delete_post_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePost() }
            .show()
    }

    private fun confirmDeleteComment(comment: Comment) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.social_delete_comment_title)
            .setMessage(R.string.social_delete_comment_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteComment(comment.id)
            }
            .show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.listComments.adapter = null
        }
        commentAdapter = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_POST_ID = "extra_post_id"

        fun intent(context: Context, postId: String): Intent =
            Intent(context, PostDetailActivity::class.java)
                .putExtra(EXTRA_POST_ID, postId)
    }
}
