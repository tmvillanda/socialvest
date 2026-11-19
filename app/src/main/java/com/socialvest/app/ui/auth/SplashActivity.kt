package com.socialvest.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.socialvest.app.MainActivity
import com.socialvest.app.SocialvestApp

/**
 * Launch screen. Firebase Authentication caches the session on disk, so the
 * destination can be decided synchronously without blocking the UI.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark background: keep status bar icons light.
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val isSignedIn = (application as SocialvestApp).authRepository.currentUser() != null
        val destination = if (isSignedIn) MainActivity::class.java else LoginActivity::class.java

        startActivity(Intent(this, destination))
        finish()
    }
}
