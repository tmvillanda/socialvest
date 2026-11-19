package com.socialvest.app.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps content clear of the status/navigation bars and of the on-screen keyboard.
 *
 * The app is edge-to-edge (enforced from targetSdk 35/36), so `adjustResize` no
 * longer resizes the window. Padding the root by the IME height instead keeps
 * pinned bottom bars (e.g. the comment input on the post detail screen) above
 * the keyboard while the content area stays scrollable.
 */
fun View.applySystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(
            bars.left,
            bars.top,
            bars.right,
            maxOf(bars.bottom, ime.bottom)
        )
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * Drawer panels sit directly under the [androidx.drawerlayout.widget.DrawerLayout],
 * which ignores padding on itself. Pad the panel so its header clears the status
 * bar and its last item clears the gesture/navigation bar.
 */
fun View.applyDrawerInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
        insets
    }
}
