package com.example.nihaogo.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.ProgressRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * AdMob rewarded ads that pay [GameRules.AD_REWARD] coins, up to [GameRules.ADS_PER_DAY] a day.
 * One ad is kept loaded so it shows right away when the player asks for it.
 */
class RewardedAds(
    context: Context,
    private val adUnitId: String,
    private val progress: ProgressRepository,
) {
    private val appContext = context.applicationContext
    private var ad: RewardedAd? = null
    private var loading = false

    enum class Outcome(val messageTh: String) {
        Rewarded("ได้รับ +${GameRules.AD_REWARD} 🪙 แล้ว!"),
        LimitReached("วันนี้ดูโฆษณาครบ ${GameRules.ADS_PER_DAY} ครั้งแล้ว พรุ่งนี้มาใหม่นะ"),
        NotReady("โฆษณายังไม่พร้อม ลองใหม่อีกครั้งในอีกสักครู่"),
        Skipped("ต้องดูโฆษณาจนจบถึงจะได้เหรียญนะ"),
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {}
            withContext(Dispatchers.Main) { load() }
        }
    }

    /** Shows an ad and pays the reward if the player watched it to the end. Call on the main thread. */
    suspend fun watchForCoins(activity: Activity): Outcome {
        if (GameRules.adsLeft(progress.progress.first(), GameRules.today()) == 0) return Outcome.LimitReached
        val current = ad ?: run {
            load()
            return Outcome.NotReady
        }
        ad = null
        if (!show(activity, current)) return Outcome.Skipped

        var paid = false
        progress.update { p -> GameRules.applyAdReward(p, GameRules.today())?.also { paid = true } ?: p }
        return if (paid) Outcome.Rewarded else Outcome.LimitReached
    }

    private fun load() {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(appContext, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) {
                loading = false
                ad = loaded
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
            }
        })
    }

    /** Returns whether the reward was earned, once the ad is closed. Loads the next ad either way. */
    private suspend fun show(activity: Activity, current: RewardedAd): Boolean = suspendCancellableCoroutine { cont ->
        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load()
                if (cont.isActive) cont.resume(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                load()
                if (cont.isActive) cont.resume(false)
            }
        }
        current.show(activity) { earned = true }
    }

    private companion object {
        const val TAG = "RewardedAds"
    }
}
