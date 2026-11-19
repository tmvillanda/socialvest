package com.socialvest.app.utils

import android.util.Patterns
import androidx.annotation.StringRes
import com.socialvest.app.R

/**
 * Form validation shared by Login, Register and Edit Profile.
 * Returns a string resource id for the first problem, or `null` when valid.
 */
object Validators {

    private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,20}$")

    const val MIN_PASSWORD_LENGTH = 6
    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 60
    const val MAX_BIO_LENGTH = 160

    @StringRes
    fun emailError(value: String): Int? {
        val email = value.trim()
        return when {
            email.isEmpty() -> R.string.error_email_required
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.error_email_invalid
            else -> null
        }
    }

    @StringRes
    fun passwordError(value: String): Int? = when {
        value.isEmpty() -> R.string.error_password_required
        value.length < MIN_PASSWORD_LENGTH -> R.string.error_password_short
        else -> null
    }

    @StringRes
    fun confirmPasswordError(password: String, confirmation: String): Int? = when {
        confirmation.isEmpty() -> R.string.error_password_required
        confirmation != password -> R.string.error_password_mismatch
        else -> null
    }

    @StringRes
    fun fullNameError(value: String): Int? {
        val name = value.trim()
        return when {
            name.length < MIN_NAME_LENGTH -> R.string.error_name_short
            name.length > MAX_NAME_LENGTH -> R.string.error_name_long
            else -> null
        }
    }

    @StringRes
    fun usernameError(value: String): Int? {
        val username = value.trim().lowercase()
        return when {
            username.isEmpty() -> R.string.error_username_required
            !USERNAME_PATTERN.matches(username) -> R.string.error_username_invalid
            else -> null
        }
    }

    @StringRes
    fun bioError(value: String): Int? =
        if (value.trim().length > MAX_BIO_LENGTH) R.string.error_bio_too_long else null

    private val TICKER_PATTERN = Regex("^[A-Za-z0-9.\\-]{1,10}$")

    @StringRes
    fun assetNameError(value: String): Int? {
        val name = value.trim()
        return when {
            name.length < MIN_NAME_LENGTH -> R.string.error_asset_name_short
            name.length > MAX_NAME_LENGTH -> R.string.error_asset_name_long
            else -> null
        }
    }

    @StringRes
    fun tickerError(value: String): Int? {
        val ticker = value.trim()
        if (ticker.isEmpty()) return null
        return if (TICKER_PATTERN.matches(ticker)) null else R.string.error_ticker_invalid
    }
}
