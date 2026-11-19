package com.socialvest.app.ui.learning

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.databinding.ItemModuleBinding
import com.socialvest.app.data.model.CourseModule
import com.socialvest.app.domain.learning.Learning

/** Ordered lessons of one course, with a check mark for completed ones. */
class ModuleAdapter(
    private val onModuleClick: (CourseModule) -> Unit
) : ListAdapter<CourseModule, ModuleAdapter.ModuleViewHolder>(DIFF) {

    /** Completed lesson ids. Updated without rebuilding the whole list. */
    var completedIds: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_COMPLETION)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val binding = ItemModuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ModuleViewHolder(binding, onModuleClick, ::isCompleted)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    override fun onBindViewHolder(
        holder: ModuleViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_COMPLETION)) {
            holder.bindCompletion(getItem(position))
        } else {
            holder.bind(getItem(position), position + 1)
        }
    }

    private fun isCompleted(moduleId: String): Boolean = moduleId in completedIds

    class ModuleViewHolder(
        private val binding: ItemModuleBinding,
        private val onModuleClick: (CourseModule) -> Unit,
        private val isCompleted: (String) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(module: CourseModule, number: Int) {
            binding.textNumber.text = number.toString()
            binding.textTitle.text = module.title

            val duration = Learning.durationLabel(module.durationSeconds)
            binding.textDuration.text = duration
            binding.textDuration.isVisible = duration.isNotBlank()

            bindCompletion(module)
            binding.root.setOnClickListener { onModuleClick(module) }
        }

        fun bindCompletion(module: CourseModule) {
            binding.iconCompleted.isVisible = isCompleted(module.id)
        }
    }

    companion object {
        private const val PAYLOAD_COMPLETION = "completion"

        private val DIFF = object : DiffUtil.ItemCallback<CourseModule>() {
            override fun areItemsTheSame(oldItem: CourseModule, newItem: CourseModule): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CourseModule, newItem: CourseModule): Boolean =
                oldItem == newItem
        }
    }
}
