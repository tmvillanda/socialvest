package com.socialvest.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.R
import com.socialvest.app.data.model.User
import com.socialvest.app.databinding.FragmentProfileBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.ui.settings.SettingsActivity
import com.socialvest.app.utils.Initials
import com.socialvest.app.viewmodel.ProfileUiState
import com.socialvest.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory }

    private val editProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The profile screen observes Firestore, so no manual refresh is needed.
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonEditProfile.setOnClickListener {
            editProfileLauncher.launch(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.buttonLogout.setOnClickListener { confirmLogout() }
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        binding.progressProfile.isVisible = state.isLoading
        binding.groupProfile.isVisible = !state.isLoading && state.user != null
        binding.textEmail.isVisible = !state.isLoading && state.user != null
        binding.textBio.isVisible = !state.isLoading && state.user != null

        state.user?.let(::bindUser)

        binding.textMissingProfile.isVisible = state.profileMissing
        binding.buttonEditProfile.text = getString(
            if (state.profileMissing) R.string.complete_profile else R.string.edit_profile_title
        )

        binding.textError.isVisible = state.errorRes != null
        state.errorRes?.let { binding.textError.text = getString(it) }
    }

    private fun bindUser(user: User) {
        binding.textName.text = user.fullName.ifBlank { user.username }
        binding.textUsername.text = getString(R.string.profile_username_format, user.username)
        binding.textEmail.text = viewModel.uiState.value.email
        binding.textBio.text = user.bio.ifBlank { getString(R.string.profile_no_bio) }
        binding.textInitials.text = Initials.of(user.fullName, user.username)
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout_button) { _, _ -> viewModel.logout() }
            .show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
