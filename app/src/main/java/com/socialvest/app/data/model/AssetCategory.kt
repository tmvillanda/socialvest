package com.socialvest.app.data.model

import androidx.annotation.StringRes
import com.socialvest.app.R

enum class AssetCategory(val id: String, @StringRes val labelRes: Int) {
    STOCK("stock", R.string.category_stock),
    CRYPTO("crypto", R.string.category_crypto),
    MUTUAL_FUND("mutual_fund", R.string.category_mutual_fund),
    GOLD("gold", R.string.category_gold),
    BOND("bond", R.string.category_bond),
    OTHER("other", R.string.category_other);

    companion object {
        fun fromId(id: String?): AssetCategory =
            entries.firstOrNull { it.id == id } ?: OTHER
    }
}
