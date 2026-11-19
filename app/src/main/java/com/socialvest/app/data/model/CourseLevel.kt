package com.socialvest.app.data.model

import androidx.annotation.StringRes
import com.socialvest.app.R

/** Course difficulty. Unknown values from the Console are simply not shown. */
enum class CourseLevel(val id: String, @StringRes val labelRes: Int) {
    BEGINNER("beginner", R.string.level_beginner),
    INTERMEDIATE("intermediate", R.string.level_intermediate),
    ADVANCED("advanced", R.string.level_advanced);

    companion object {
        fun fromId(id: String?): CourseLevel? =
            entries.firstOrNull { it.id == id }
    }
}
