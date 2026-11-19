package com.socialvest.app.utils

import java.util.Locale

/** Initials used by the avatar chips (no photo upload exists yet). */
object Initials {

    /** At most two letters, derived from a display name or username. */
    fun of(vararg candidates: String): String {
        val source = candidates.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (source.isEmpty()) return "?"

        val words = source.split(Regex("\\s+")).filter { it.isNotBlank() }
        val initials = when {
            words.isEmpty() -> ""
            words.size == 1 -> words.first().take(2)
            else -> words.take(2).map { it.first() }.joinToString("")
        }
        return initials.uppercase(Locale.getDefault())
    }
}
