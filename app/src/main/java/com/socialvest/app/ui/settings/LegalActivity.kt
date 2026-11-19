package com.socialvest.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.socialvest.app.R
import com.socialvest.app.databinding.ActivityLegalBinding
import com.socialvest.app.utils.applySystemBarInsets

/** Static terms / privacy documents. No data is fetched or written here. */
class LegalActivity : AppCompatActivity() {

    enum class Document(
        val id: String,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int
    ) {
        TERMS("terms", R.string.legal_terms_title, R.string.legal_terms_body),
        PRIVACY("privacy", R.string.legal_privacy_title, R.string.legal_privacy_body);

        companion object {
            fun fromId(value: String?): Document =
                entries.firstOrNull { it.id == value } ?: TERMS
        }
    }

    private lateinit var binding: ActivityLegalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val document = Document.fromId(intent.getStringExtra(EXTRA_DOCUMENT))
        binding.toolbar.title = getString(document.titleRes)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textLegalBody.setText(document.bodyRes)
    }

    companion object {
        private const val EXTRA_DOCUMENT = "extra_document"

        fun intent(context: Context, document: Document): Intent =
            Intent(context, LegalActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, document.id)
    }
}
