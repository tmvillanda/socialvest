package com.socialvest.app.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.databinding.ItemPostBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials
import com.socialvest.app.viewmodel.SocialPostRow

/** Feed cards. All numbers and labels arrive pre-computed from the ViewModel. */
class PostAdapter(
    private val onPostClick: (SocialPostRow) -> Unit,
    private val onLikeClick: (SocialPostRow) -> Unit,
    private val onAuthorClick: (SocialPostRow) -> Unit
) : ListAdapter<SocialPostRow, PostAdapter.PostViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding, onPostClick, onLikeClick, onAuthorClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
        private val binding: ItemPostBinding,
        private val onPostClick: (SocialPostRow) -> Unit,
        private val onLikeClick: (SocialPostRow) -> Unit,
        private val onAuthorClick: (SocialPostRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SocialPostRow) {
            val post = row.post
            val context = binding.root.context

            binding.textAuthor.text = post.authorDisplayName
            binding.avatarInitials.text = Initials.of(post.authorName, post.authorUsername)

            val username = post.authorUsername.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.social_author_format, it) }
            val time = post.createdAtMillis?.let(Display::dateTime)
                ?: context.getString(R.string.social_time_unknown)
            binding.textMeta.text = listOfNotNull(username, time).joinToString(" · ")

            binding.textCategory.text = context.getString(post.category.labelRes)
            binding.textContent.text = post.content

            binding.buttonLike.text = post.likeCount.toString()
            binding.buttonLike.setIconResource(
                if (row.liked) R.drawable.ic_like_filled else R.drawable.ic_like
            )
            binding.buttonLike.contentDescription = context.getString(
                if (row.liked) R.string.social_unlike else R.string.social_like
            )
            binding.buttonLike.isEnabled = !row.isLiking

            binding.buttonComment.text = post.commentCount.toString()
            binding.buttonComment.contentDescription =
                context.getString(R.string.social_comment)

            binding.avatarContainer.contentDescription = context.getString(
                R.string.social_open_profile,
                post.authorDisplayName
            )

            binding.root.setOnClickListener { onPostClick(row) }
            binding.buttonLike.setOnClickListener { onLikeClick(row) }
            binding.buttonComment.setOnClickListener { onPostClick(row) }
            binding.avatarContainer.setOnClickListener { onAuthorClick(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SocialPostRow>() {
            override fun areItemsTheSame(oldItem: SocialPostRow, newItem: SocialPostRow): Boolean =
                oldItem.post.id == newItem.post.id

            override fun areContentsTheSame(oldItem: SocialPostRow, newItem: SocialPostRow): Boolean =
                oldItem == newItem
        }
    }
}
