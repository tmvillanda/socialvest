package com.socialvest.app.ui.learning

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.databinding.ItemCourseBinding
import com.socialvest.app.viewmodel.CourseRow

/** Catalogue cards. Levels/categories arrive as labels from the ViewModel. */
class CourseAdapter(
    private val onCourseClick: (CourseRow) -> Unit
) : ListAdapter<CourseRow, CourseAdapter.CourseViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val binding = ItemCourseBinding.inflate(
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
        private val binding: ItemCourseBinding,
        private val onCourseClick: (CourseRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: CourseRow) {
            val course = row.course
            val context = binding.root.context

            binding.textTitle.text = course.title
            binding.textMeta.text = listOfNotNull(
                row.totalModules
                    .takeIf { it > 0 }
                    ?.let { context.getString(R.string.learn_module_count, it) },
                context.getString(R.string.learn_by_instructor, course.instructorDisplayName),
                course.level?.let { context.getString(it.labelRes) },
                context.getString(course.category.labelRes)
            ).joinToString(" · ")

            binding.textStatus.isVisible = row.enrolled
            binding.textStatus.setText(R.string.learn_status_enrolled)

            binding.textProgress.isVisible = row.enrolled
            binding.textProgress.text = context.getString(
                R.string.learn_progress_modules,
                row.completedModules
            )

            binding.root.setOnClickListener { onCourseClick(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CourseRow>() {
            override fun areItemsTheSame(oldItem: CourseRow, newItem: CourseRow): Boolean =
                oldItem.course.id == newItem.course.id

            override fun areContentsTheSame(oldItem: CourseRow, newItem: CourseRow): Boolean =
                oldItem == newItem
        }
    }
}
