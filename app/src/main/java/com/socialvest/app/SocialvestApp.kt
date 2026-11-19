package com.socialvest.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.socialvest.app.data.repository.AuthRepository
import com.socialvest.app.data.repository.InvestmentRepository
import com.socialvest.app.data.repository.LearningRepository
import com.socialvest.app.data.repository.SocialRepository
import com.socialvest.app.data.remote.PriceService
import com.socialvest.app.data.remote.MarketQuoteService
import com.socialvest.app.utils.ThemePreference

/**
 * Simple manual dependency container. Keeps Firebase construction out of
 * Activities/Fragments and lets ViewModels depend on a repository only.
 */
class SocialvestApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemePreference.applySaved(this)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    val investmentRepository: InvestmentRepository by lazy {
        InvestmentRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    /** Shared so its 60-second quote cache survives tab switches. */
    val priceService: PriceService by lazy { PriceService() }

    /** On-demand quotes for the Search catalogue. */
    val marketQuoteService: MarketQuoteService by lazy { MarketQuoteService() }

    val socialRepository: SocialRepository by lazy {
        SocialRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    val learningRepository: LearningRepository by lazy {
        LearningRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }
}
