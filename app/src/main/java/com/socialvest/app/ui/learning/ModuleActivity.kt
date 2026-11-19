package com.socialvest.app.ui.learning

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityModuleBinding
import com.socialvest.app.domain.learning.YoutubeVideo
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.ModulePlayerUiState
import com.socialvest.app.viewmodel.ModulePlayerViewModel
import kotlinx.coroutines.launch

/**
 * One lesson: YouTube embed, description, completion toggle and an explicit
 * "open in YouTube" fallback. The app never downloads or re-uploads videos; the
 * progress record stores the completed flag only (no playback position).
 */
class ModuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModuleBinding
    private val courseId: String by lazy { intent.getStringExtra(EXTRA_COURSE_ID).orEmpty() }
    private val moduleId: String by lazy { intent.getStringExtra(EXTRA_MODULE_ID).orEmpty() }
    private val viewModel: ModulePlayerViewModel by viewModels {
        ModulePlayerViewModel.factory(courseId, moduleId)
    }

    private var loadedVideoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (courseId.isBlank() || moduleId.isBlank()) {
            finish()
            return
        }

        binding = ActivityModuleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        configureWebView()

        binding.buttonMarkDone.setOnClickListener {
            val completed = viewModel.uiState.value.completed
            viewModel.setCompleted(!completed)
        }
        binding.buttonOpenYoutube.setOnClickListener { openOnYoutube() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        observeState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val web = binding.webVideo
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true

        // Keep the embed inside the app; anything the player tries to open
        // elsewhere (channel, YouTube logo) is handed to the YouTube app.
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith(EMBED_HOST)) {
                    false
                } else {
                    openExternal(url)
                    true
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ModulePlayerUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        val module = state.module

        binding.progressModule.isVisible = state.isLoading
        binding.groupModule.isVisible = module != null
        binding.textMissing.isVisible =
            !state.isLoading && module == null && state.errorRes == null
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

        if (module == null) {
            showVideo(null)
            return
        }

        binding.textCourseTitle.text = state.courseTitle.ifBlank {
            getString(R.string.module_no_course_title)
        }
        binding.textModuleTitle.text = module.title

        binding.textDuration.text = state.durationLabel
        binding.textDuration.isVisible = state.durationLabel.isNotBlank()

        val description = module.description
        binding.textDescriptionSection.isVisible = description.isNotBlank()
        binding.textDescription.isVisible = description.isNotBlank()
        binding.textDescription.text = description

        binding.buttonMarkDone.setText(
            if (state.completed) R.string.module_mark_undone else R.string.module_mark_done
        )
        binding.buttonMarkDone.isEnabled = !state.isSaving
        binding.progressSave.isVisible = state.isSaving
        binding.buttonOpenYoutube.isEnabled = state.videoId != null

        showVideo(state.videoId)
    }

    /** Loads the embed only when the module changes (no reload every emission). */
    private fun showVideo(videoId: String?) {
        val web = binding.webVideo

        if (videoId == null) {
            loadedVideoId = null
            web.stopLoading()
            web.isVisible = false
            binding.textVideoMissing.isVisible = true
            return
        }

        binding.textVideoMissing.isVisible = false
        web.isVisible = true
        if (loadedVideoId == videoId) return

        loadedVideoId = videoId
        // 16:9-friendly fixed height already set in the layout; the embed fits.
        web.layoutParams = web.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        // YouTube rejects embed requests without a Referer (player error 153),
        // and a plain WebView loadUrl sends none.
        web.loadUrl(
            YoutubeVideo.embedUrl(videoId),
            mapOf(
                REFERER_HEADER to REFERER_URL,
                ORIGIN_HEADER to YoutubeVideo.EMBED_ORIGIN
            )
        )
    }

    private fun openOnYoutube() {
        val videoId = viewModel.uiState.value.videoId ?: return
        openExternal(YoutubeVideo.watchUrl(videoId))
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.module_youtube_unavailable, Toast.LENGTH_SHORT).show()
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

    override fun onPause() {
        if (!::binding.isInitialized) {
            super.onPause()
            return
        }
        binding.webVideo.onPause()
        binding.webVideo.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        binding.webVideo.resumeTimers()
        binding.webVideo.onResume()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.webVideo.stopLoading()
            binding.webVideo.loadUrl("about:blank")
            binding.webVideo.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_MODULE_ID = "extra_module_id"
        private const val EMBED_HOST = "https://www.youtube.com/embed/"
        private const val REFERER_HEADER = "Referer"
        private const val ORIGIN_HEADER = "Origin"
        private const val REFERER_URL = "${YoutubeVideo.EMBED_ORIGIN}/"

        fun intent(context: Context, courseId: String, moduleId: String): Intent =
            Intent(context, ModuleActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_MODULE_ID, moduleId)
    }
}
