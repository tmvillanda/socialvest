package com.socialvest.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.model.Course
import com.socialvest.app.databinding.ItemHomeCourseBinding
import com.socialvest.app.utils.Initials

/** Featured-course row on Home. Tapping opens the normal course detail screen. */
class HomeCourseAdapter(
    private val onCourseClick: (Course) -> Unit
) : ListAdapter<Course, HomeCourseAdapter.CourseViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val binding = ItemHomeCourseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CourseViewHolder(binding, onCourseClick)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CourseViewHolder(
        private val binding: ItemHomeCourseBinding,
        private val onCourseClick: (Course) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(course: Course) {
            val context = binding.root.context

            binding.textThumbnailFallback.text = Initials.of(course.title)
            binding.textTitle.text = course.title
            binding.textMeta.text = listOfNotNull(
                context.getString(R.string.learn_by_instructor, course.instructorDisplayName),
                course.level?.let { context.getString(it.labelRes) },
                context.getString(course.category.labelRes)
            ).joinToString(" · ")

            binding.root.setOnClickListener { onCourseClick(course) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Course>() {
            override fun areItemsTheSame(oldItem: Course, newItem: Course): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Course, newItem: Course): Boolean =
                oldItem == newItem
        }
    }
}
