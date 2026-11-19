package com.socialvest.app.ui.social

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityPublicProfileBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Initials
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.PublicProfileUiState
import com.socialvest.app.viewmodel.PublicProfileViewModel
import kotlinx.coroutines.launch

/**
 * Read-only public profile of another user. Only the fields stored in
 * `users/{uid}` are shown — never portfolio values or the email address.
 */
class PublicProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublicProfileBinding
    private val uid: String by lazy { intent.getStringExtra(EXTRA_UID).orEmpty() }
    private val viewModel: PublicProfileViewModel by viewModels {
        PublicProfileViewModel.factory(uid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (uid.isBlank()) {
            finish()
            return
        }

        binding = ActivityPublicProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: PublicProfileUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressProfile.isVisible = state.isLoading

        val user = state.user
        binding.groupProfile.isVisible = user != null
        binding.textBio.isVisible = user != null
        binding.textJoined.isVisible = user != null
        binding.textMissing.isVisible = !state.isLoading && user == null && state.errorRes == null
        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.setText(it) }

        if (user == null) return

        binding.textName.text = user.fullName.ifBlank { user.username }
        binding.textUsername.text = getString(R.string.social_author_format, user.username)
        binding.textBio.text = user.bio.ifBlank {
            getString(R.string.social_public_profile_no_bio)
        }
        binding.textInitials.text = Initials.of(user.fullName, user.username)
        binding.textJoined.text = user.createdAt?.toDate()?.time
            ?.let { getString(R.string.social_public_profile_joined, Display.date(it)) }
            .orEmpty()
        binding.textJoined.isVisible = binding.textJoined.text.isNotBlank()
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
        private const val EXTRA_UID = "extra_uid"

        fun intent(context: Context, uid: String): Intent =
            Intent(context, PublicProfileActivity::class.java)
                .putExtra(EXTRA_UID, uid)
    }
}
