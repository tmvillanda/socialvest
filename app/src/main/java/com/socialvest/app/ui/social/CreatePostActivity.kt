package com.socialvest.app.ui.social

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.databinding.ActivityCreatePostBinding
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.CreatePostUiState
import com.socialvest.app.viewmodel.CreatePostViewModel
import kotlinx.coroutines.launch

/**
 * Creates a new text post, or edits an existing one when [EXTRA_POST_ID] is
 * present. Image attachments are intentionally not offered yet.
 */
class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val postId: String? by lazy { intent.getStringExtra(EXTRA_POST_ID) }
    private val viewModel: CreatePostViewModel by viewModels {
        CreatePostViewModel.factory(postId)
    }
    private val categories = AssetCategory.entries
    private var hasFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val labels = categories.map { getString(it.labelRes) }
        binding.dropdownCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        binding.dropdownCategory.setOnItemClickListener { _, _, position, _ ->
            categories.getOrNull(position)?.let(viewModel::onCategorySelected)
        }

        binding.editContent.doAfterTextChanged { editable ->
            viewModel.onContentChanged(editable?.toString().orEmpty())
        }

        binding.buttonSubmit.setOnClickListener {
            binding.editContent.clearFocus()
            viewModel.submit()
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

    private fun render(state: CreatePostUiState) {
        binding.toolbar.setTitle(
            if (state.isEdit) R.string.social_post_edit_title else R.string.social_post_title
        )
        binding.buttonSubmit.setText(
            if (state.isEdit) R.string.social_post_save else R.string.social_post_submit
        )

        // Only touch the field when the value really differs (prefill), so the
        // cursor never jumps while the user is typing.
        if (binding.editContent.text?.toString() != state.content) {
            binding.editContent.setText(state.content)
            binding.editContent.setSelection(state.content.length)
        }
        if (binding.dropdownCategory.text.toString() != getString(state.category.labelRes)) {
            binding.dropdownCategory.setText(getString(state.category.labelRes), false)
        }

        binding.inputContent.error = state.contentErrorRes?.let(::getString)
        binding.buttonSubmit.isEnabled = !state.isSubmitting && !state.isLoading
        binding.progressSubmit.isVisible = state.isSubmitting || state.isLoading
        binding.editContent.isEnabled = !state.isSubmitting && !state.isLoading
        binding.dropdownCategory.isEnabled = !state.isSubmitting && !state.isLoading

        state.errorRes?.let { messageRes ->
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }

        if (state.isPosted && !hasFinished) {
            hasFinished = true
            val message = if (state.isEdit) {
                R.string.social_post_updated
            } else {
                R.string.social_post_published
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val EXTRA_POST_ID = "extra_post_id"

        fun intent(context: Context): Intent =
            Intent(context, CreatePostActivity::class.java)

        /** Opens the same form preloaded with [postId]'s content (owner only). */
        fun editIntent(context: Context, postId: String): Intent =
            intent(context).putExtra(EXTRA_POST_ID, postId)
    }
}
