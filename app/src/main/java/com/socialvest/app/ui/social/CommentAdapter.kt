package com.socialvest.app.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.model.Comment
import com.socialvest.app.databinding.ItemCommentBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials

/** Comment rows. Delete is offered only to the comment author or the post owner. */
class CommentAdapter(
    private val canDelete: (Comment) -> Boolean,
    private val onDelete: (Comment) -> Unit,
    private val onAuthorClick: (Comment) -> Unit
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding, canDelete, onDelete, onAuthorClick)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val canDelete: (Comment) -> Boolean,
        private val onDelete: (Comment) -> Unit,
        private val onAuthorClick: (Comment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context

            binding.textAuthor.text = comment.authorDisplayName
            binding.avatarInitials.text = Initials.of(comment.authorName, comment.authorUsername)
            binding.textMeta.text = comment.createdAtMillis?.let(Display::dateTime)
                ?: context.getString(R.string.social_time_unknown)
            binding.textContent.text = comment.content

            binding.buttonDeleteComment.isVisible = canDelete(comment)
            binding.buttonDeleteComment.setOnClickListener { onDelete(comment) }
            binding.avatarContainer.setOnClickListener { onAuthorClick(comment) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean =
                oldItem == newItem
        }
    }
}
