package com.socialvest.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.socialvest.app.databinding.ActivityMainBinding
import com.socialvest.app.databinding.NavHeaderDrawerBinding
import com.socialvest.app.ui.auth.LoginActivity
import com.socialvest.app.ui.settings.AccountSecurityActivity
import com.socialvest.app.ui.settings.LegalActivity
import com.socialvest.app.ui.settings.SettingsActivity
import com.socialvest.app.utils.AppTheme
import com.socialvest.app.utils.Initials
import com.socialvest.app.utils.ThemePreference
import com.socialvest.app.utils.applyDrawerInsets
import com.socialvest.app.utils.applySystemBarInsets
import com.socialvest.app.viewmodel.ProfileUiState
import com.socialvest.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Single activity hosting the five main destinations (Home, Social, Search,
 * Learn, Portfolio) plus the Profile screen reached from the left drawer.
 *
 * The bottom navigation deliberately does not contain Profile: account actions
 * live in the drawer, and the Profile destination hides the bottom bar so it
 * feels like a detail screen rather than a sixth tab.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var headerBinding: NavHeaderDrawerBinding
    private lateinit var navController: NavController
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory }
    private var leavingToLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // A DrawerLayout lays its children out across the whole window, so
        // padding the drawer itself does nothing. Pad the content container and
        // the drawer panel instead.
        binding.contentRoot.applySystemBarInsets()
        binding.navigationView.applyDrawerInsets()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        setUpDrawer()
        observeProfile()
    }

    private fun setUpDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.toolbar.setNavigationOnClickListener {
            if (navController.currentDestination?.id == R.id.profileFragment) {
                navController.navigateUp()
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        binding.navigationView.setNavigationItemSelectedListener(::onDrawerItemSelected)

        headerBinding = NavHeaderDrawerBinding.bind(binding.navigationView.getHeaderView(0))
        headerBinding.root.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            openProfile()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isProfile = destination.id == R.id.profileFragment
            binding.bottomNav.isVisible = !isProfile
            binding.toolbar.title = destination.label ?: getString(R.string.app_name)

            if (isProfile) {
                binding.toolbar.setNavigationIcon(R.drawable.ic_back)
                binding.toolbar.setNavigationContentDescription(R.string.back)
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                binding.navigationView.setCheckedItem(R.id.drawer_profile)
            } else {
                binding.toolbar.setNavigationIcon(drawerToggle.drawerArrowDrawable)
                binding.toolbar.setNavigationContentDescription(R.string.drawer_open)
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }
    }

    private fun onDrawerItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawers()

        when (item.itemId) {
            R.id.drawer_profile -> openProfile()
            R.id.drawer_account_security ->
                startActivity(Intent(this, AccountSecurityActivity::class.java))

            R.id.drawer_settings, R.id.drawer_privacy ->
                startActivity(Intent(this, SettingsActivity::class.java))

            R.id.drawer_theme -> showThemeDialog()
            R.id.drawer_notifications -> showInfoDialog(
                titleRes = R.string.drawer_notifications,
                messageRes = R.string.drawer_notifications_unavailable
            )

            R.id.drawer_language -> showInfoDialog(
                titleRes = R.string.drawer_language,
                messageRes = R.string.drawer_language_message
            )

            R.id.drawer_terms ->
                startActivity(LegalActivity.intent(this, LegalActivity.Document.TERMS))

            R.id.drawer_privacy_policy ->
                startActivity(LegalActivity.intent(this, LegalActivity.Document.PRIVACY))

            R.id.drawer_logout -> confirmLogout()
        }
        return true
    }

    private fun openProfile() {
        if (navController.currentDestination?.id == R.id.profileFragment) return
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        navController.navigate(R.id.profileFragment)
    }

    private fun showThemeDialog() {
        val themes = AppTheme.entries
        val labels = themes.map { getString(it.labelRes) }.toTypedArray()
        val selected = themes.indexOf(ThemePreference.current(this)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_theme_title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val theme = themes.getOrNull(which) ?: return@setSingleChoiceItems
                ThemePreference.save(this, theme)
                ThemePreference.apply(theme)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showInfoDialog(titleRes: Int, messageRes: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout_button) { _, _ -> viewModel.logout() }
            .show()
    }

    private fun observeProfile() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderDrawerHeader)
            }
        }
    }

    private fun renderDrawerHeader(state: ProfileUiState) {
        if (state.isSignedOut) {
            goToLogin()
            return
        }

        headerBinding.textDrawerInitials.text =
            Initials.of(state.user?.fullName.orEmpty(), state.user?.username.orEmpty())
        headerBinding.textDrawerName.text = state.user?.fullName?.takeIf { it.isNotBlank() }
            ?: state.user?.username
            ?: getString(R.string.value_undefined)
        headerBinding.textDrawerUsername.text = if (state.user?.username.isNullOrBlank()) {
            ""
        } else {
            getString(R.string.profile_username_format, state.user.username)
        }
        headerBinding.textDrawerEmail.text = state.email
    }

    private fun goToLogin() {
        if (leavingToLogin) return
        leavingToLogin = true
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
