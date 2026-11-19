package com.socialvest.app.ui.learning

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityCourseDetailBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Initials
import com.socialvest.app.utils.RemoteImage
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.CourseDetailUiState
import com.socialvest.app.viewmodel.CourseDetailViewModel
import kotlinx.coroutines.launch

/**
 * Course detail: title, description, thumbnail, level/category, instructor,
 * module list, enrollment button and per-course progress.
 */
class CourseDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCourseDetailBinding
    private val courseId: String by lazy { intent.getStringExtra(EXTRA_COURSE_ID).orEmpty() }
    private val viewModel: CourseDetailViewModel by viewModels {
        CourseDetailViewModel.factory(courseId)
    }

    private var moduleAdapter: ModuleAdapter? = null
    private var thumbnailCourseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (courseId.isBlank()) {
            finish()
            return
        }

        binding = ActivityCourseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = ModuleAdapter { module ->
            startActivity(ModuleActivity.intent(this, courseId, module.id))
        }
        moduleAdapter = adapter
        binding.listModules.layoutManager = LinearLayoutManager(this)
        binding.listModules.adapter = adapter

        binding.buttonEnroll.setOnClickListener { viewModel.enroll() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: CourseDetailUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        val course = state.course

        binding.progressDetail.isVisible = state.isLoading
        binding.groupCourse.isVisible = course != null
        binding.textMissing.isVisible =
            !state.isLoading && course == null && state.errorRes == null
        binding.textError.isVisible = state.errorRes != null
        binding.buttonRetry.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.setText(it) }

        state.messageRes?.let { messageRes ->
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
        state.errorRes?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }

        if (course == null) {
            thumbnailCourseId = null
            return
        }

        binding.textTitle.text = course.title
        binding.textMeta.text = listOfNotNull(
            getString(R.string.learn_by_instructor, course.instructorDisplayName),
            course.level?.let { getString(it.labelRes) },
            getString(course.category.labelRes)
        ).joinToString(" · ")
        binding.textDescription.text = course.description
        binding.textDescription.isVisible = course.description.isNotBlank()

        val sourceUrl = course.sourceUrl.trim()
        val hasSource = sourceUrl.isNotEmpty()
        binding.textSource.isVisible = hasSource
        binding.buttonPlaylist.isVisible = hasSource
        if (hasSource) {
            binding.textSource.text = getString(
                R.string.course_detail_source_note,
                course.instructorDisplayName
            )
            binding.buttonPlaylist.setOnClickListener { openSource(sourceUrl) }
        }

        binding.textThumbnailFallback.text = Initials.of(course.title)
        loadThumbnail(course.id, course.thumbnailUrl)

        binding.textProgress.text = getString(
            R.string.course_detail_progress_format,
            state.completedCount,
            state.totalModules
        )
        binding.textPercent.text = getString(
            R.string.course_detail_percent_format,
            state.percent
        )
        binding.progressCourse.setProgressCompat(state.percent, true)
        binding.cardProgress.isVisible = state.totalModules > 0

        binding.buttonEnroll.isEnabled = !state.enrolled && !state.isEnrolling
        binding.buttonEnroll.setText(
            if (state.enrolled) R.string.course_detail_enrolled else R.string.course_detail_start
        )
        binding.progressEnroll.isVisible = state.isEnrolling

        binding.textNoModules.isVisible = state.modules.isEmpty()
        binding.listModules.isVisible = state.modules.isNotEmpty()

        moduleAdapter?.completedIds = state.progress
            .filter { it.completed }
            .map { it.moduleId }
            .toSet()
        moduleAdapter?.submitList(state.modules)
    }

    /**
     * Loads the optional thumbnail once per course. A missing or broken URL
     * simply keeps the initials placeholder, so the screen never shows a
     * blank/broken image.
     */
    private fun loadThumbnail(id: String, url: String) {
        if (url.isBlank() || thumbnailCourseId == id) return
        thumbnailCourseId = id

        lifecycleScope.launch {
            val bitmap = RemoteImage.load(url)
            if (bitmap == null || thumbnailCourseId != id) return@launch
            binding.imageThumbnail.setImageBitmap(bitmap)
            binding.imageThumbnail.isVisible = true
            binding.textThumbnailFallback.isVisible = false
        }
    }

    /**
     * Opens the original playlist in YouTube/browser. The app never re-hosts a
     * creator's video, so attribution always links back to the source.
     */
    private fun openSource(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.learn_source_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_COURSE_ID = "extra_course_id"

        fun intent(context: Context, courseId: String): Intent =
            Intent(context, CourseDetailActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
    }
}
