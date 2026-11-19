package com.socialvest.app.utils

import androidx.annotation.StringRes
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.socialvest.app.R

/**
 * Converts Firebase errors into short, user-facing Indonesian messages.
 * Never exposes raw server error text to the UI.
 */
object ErrorMapper {

    @StringRes
    fun messageRes(throwable: Throwable): Int = when (throwable) {
        is FirebaseAuthException -> when (throwable.errorCode) {
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_EMAIL",
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_DISABLED" -> R.string.error_invalid_credential

            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.error_email_in_use
            "ERROR_WEAK_PASSWORD" -> R.string.error_password_short
            "ERROR_TOO_MANY_REQUESTS" -> R.string.error_too_many_requests
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.error_network
            "ERROR_REQUIRES_RECENT_LOGIN" -> R.string.error_requires_recent_login
            "ERROR_OPERATION_NOT_ALLOWED" -> R.string.error_operation_not_allowed
            else -> R.string.error_unknown
        }

        is FirebaseFirestoreException -> when (throwable.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission_denied
            FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.error_network
            else -> R.string.error_unknown
        }

        else -> R.string.error_unknown
    }
}
