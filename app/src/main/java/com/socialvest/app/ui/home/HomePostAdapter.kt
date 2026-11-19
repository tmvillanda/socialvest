package com.socialvest.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.model.Post
import com.socialvest.app.databinding.ItemHomePostBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials

/**
 * Compact post preview for Home. Counters and labels are formatted here because
 * the card is read-only: tapping it opens the full post detail, where liking and
 * commenting live.
 */
class HomePostAdapter(
    private val onPostClick: (Post) -> Unit
) : ListAdapter<Post, HomePostAdapter.PostViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemHomePostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding, onPostClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
        private val binding: ItemHomePostBinding,
        private val onPostClick: (Post) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            val context = binding.root.context

            binding.avatarInitials.text = Initials.of(post.authorName, post.authorUsername)
            binding.textAuthor.text = post.authorDisplayName

            val username = post.authorUsername.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.social_author_format, it) }
            val time = post.createdAtMillis?.let(Display::dateTime)
                ?: context.getString(R.string.social_time_unknown)
            binding.textMeta.text = listOfNotNull(username, time).joinToString(" · ")

            binding.textContent.text = post.content
            binding.textCounters.text = context.getString(
                R.string.home_post_counters,
                post.likeCount,
                post.commentCount
            )

            binding.root.setOnClickListener { onPostClick(post) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Post>() {
            override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean =
                oldItem == newItem
        }
    }
}
