package com.monetization.adsmain.splash

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.monetization.appopen.AdmobAppOpenAd
import com.monetization.appopen.AdmobAppOpenAdsManager
import com.monetization.core.AdsLoadingStatusListener
import com.monetization.core.FullScreenAdsShowListener
import com.monetization.core.commons.AdsCommons.logAds
import com.monetization.interstitials.AdmobInterstitialAd
import com.monetization.interstitials.AdmobInterstitialAdsManager
import video.downloader.remoteconfig.SdkRemoteConfigConstants.isAdEnabled

class AdmobSplashAdController constructor(
) : DefaultLifecycleObserver {

    private var listener: FullScreenAdsShowListener? = null

    private var splashAdType: SplashAdType = SplashAdType.None
    private var enabled: Boolean = false
    private var mLifecycle: Lifecycle? = null
    private var isScreenInPause: Boolean = false
    private var isHandlerRunning: Boolean = false
    private var splashAdLoaded: Boolean = false
    private var splashAdFailed: Boolean = false
    private val handlerAd = Handler(Looper.getMainLooper())
    private var runnableSplash: Runnable? = null
    private var splashAdTime = 8L
    private var activity: Activity? = null

    private var interstitialAd: AdmobInterstitialAd? = null
    private var appOpenAd: AdmobAppOpenAd? = null


    fun showSplashAd(
        enableKey: String,
        adType: SplashAdType,
        activity: Activity,
        timeInMillis: Long,
        callBack: FullScreenAdsShowListener,
        lifecycle: Lifecycle
    ) {
        val enable = enableKey.isAdEnabled()
        this.listener = callBack
        this.activity = activity
        this.splashAdType = adType
        this.mLifecycle = lifecycle
        this.splashAdTime = timeInMillis
        this.enabled = enable
        this.isScreenInPause = false
        this.isHandlerRunning = false
        this.splashAdLoaded = false
        this.splashAdFailed = false
        logAds("Show Splash Called= enable=$enable, adType=$adType")
        if (enable.not() || adType == SplashAdType.None) {
            handlerAd.postDelayed({
                onAdDismissed("Not Enabled $adType")
            }, 2000)
        } else {
            if (mLifecycle?.currentState != Lifecycle.State.DESTROYED) {
                mLifecycle?.addObserver(this)
            }
            startLoadingAds(activity)
        }
    }

    private fun startLoadingAds(activity: Activity) {
        logAds("startLoadingAds Called For Splash")
        startHandler(splashAdTime)
        val loadingStateListener = object : AdsLoadingStatusListener {
            override fun onAdLoaded(adKey: String) {
                splashAdLoaded = true
                logAds("Splash Ad onAdLoaded ,Handler Running=$isHandlerRunning")
                if (isHandlerRunning) {
                    removeCallBacks()
                    showSplashAd(activity, adKey)
                }
            }

            override fun onAdFailedToLoad(adKey: String, message: String, code: Int) {
                logAds(
                    "Splash Ad onAdFailedToLoad,message=$message ,Handler Running=$isHandlerRunning",
                    true
                )
                splashAdFailed = true
                if (isHandlerRunning) {
                    onAdDismissed(adKey)
                }
            }

        }
        loadInterstitialAd(activity, loadingStateListener)
        loadAppOpenAd(activity, loadingStateListener)
    }


    private var isInterShowing = false
    private fun showSplashAd(activity: Activity, adKey: String) {
        listener?.onAdShown(adKey)
        logAds("showSplashAd Ad Type=$splashAdType")
        val fullScreenAdsShowListener = object : FullScreenAdsShowListener {
            override fun onAdDismiss(adKey: String, adShown: Boolean, rewardEarned: Boolean) {
                listener?.onShowBlackBg(adKey, false)
                if (isInterShowing) {
                    nullAd()
                    onAdDismissed(adKey)
                }
            }

            override fun onAdShown(adKey: String) {
            }

            override fun onAdShownFailed(adKey: String) {
                nullAd()
                onAdDismiss(adKey)
            }
        }
        when (splashAdType) {
            is SplashAdType.None -> {
                fullScreenAdsShowListener.onAdDismiss("None")
            }

            is SplashAdType.AdmobInter -> {
                isInterShowing = true
                interstitialAd?.showAd(activity, fullScreenAdsShowListener)
            }

            is SplashAdType.AdmobAppOpen -> {
                isInterShowing = true
                listener?.onShowBlackBg((splashAdType as SplashAdType.AdmobAppOpen).key, true)
                appOpenAd?.showAd(activity, fullScreenAdsShowListener)
            }
        }
    }

    private fun nullAd() {
        when (splashAdType) {
            SplashAdType.None -> {

            }

            is SplashAdType.AdmobInter -> {
                interstitialAd = null
            }

            is SplashAdType.AdmobAppOpen -> {
                appOpenAd = null
            }
        }
    }


    private fun loadAppOpenAd(activity: Activity, callback: AdsLoadingStatusListener) {
        if (splashAdType !is SplashAdType.AdmobAppOpen) {
            return
        }
        val adKey = (splashAdType as SplashAdType.AdmobAppOpen).key
        val adController =
            AdmobAppOpenAdsManager.getAdController(adKey)
        if (adController == null) {
            callback.onAdFailedToLoad(adKey, "No Controller for app open splash")
            return
        }
        adController.loadAd(
            activity,
            "Splash Load App Open",
            object : AdsLoadingStatusListener {
                override fun onAdLoaded(adKey: String) {
                    appOpenAd = adController.getAvailableAd() as? AdmobAppOpenAd
                    if (appOpenAd != null) {
                        callback.onAdLoaded(adKey)
                    } else {
                        callback.onAdFailedToLoad(adKey, "onAdLoaded appOpenAd =null")
                    }
                }

                override fun onAdFailedToLoad(adKey: String, message: String, code: Int) {
                    appOpenAd = null
                    callback.onAdFailedToLoad(message)
                }
            })

    }

    private fun loadInterstitialAd(activity: Activity, callback: AdsLoadingStatusListener) {
        if (splashAdType !is SplashAdType.AdmobInter) {
            return
        }
        val adKey = (splashAdType as SplashAdType.AdmobInter).key
        val adController =
            AdmobInterstitialAdsManager.getAdController(adKey)
        if (adController == null) {
            callback.onAdFailedToLoad(adKey, "loadInterstitialAd adController == null, key=$adKey")
            return
        }
        adController.loadAd(
            activity,
            "Splash Load Interstitial",
            object : AdsLoadingStatusListener {
                override fun onAdLoaded(adKey: String) {
                    interstitialAd = adController.getAvailableAd() as? AdmobInterstitialAd
                    if (interstitialAd != null) {
                        callback.onAdLoaded(adKey)
                    } else {
                        callback.onAdFailedToLoad(adKey, " onAdLoaded interstitialAd == null")
                    }
                }

                override fun onAdFailedToLoad(adKey: String, message: String, code: Int) {
                    interstitialAd = null
                    callback.onAdFailedToLoad(message)
                }
            })

    }

    private fun onAdDismissed(key: String) {
        listener?.onAdDismiss(key)
        isHandlerRunning = false
        mLifecycle?.removeObserver(this)
        listener = null
        removeCallBacks()
    }


    private fun startHandler(time: Long) {
        if (!isHandlerRunning) {
            isHandlerRunning = true
            runnableSplash = Runnable {
                if (listener != null && isHandlerRunning) {
                    logAds("Splash Time End = $splashAdTime", true)
                    onAdDismissed(splashAdType.toString())
                }
            }
            runnableSplash?.let {
                logAds("Splash Timer started for $splashAdTime", true)
                handlerAd.postDelayed(it, time)
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        logAds("Splash Ad On Pause", true)
        isScreenInPause = true
        removeCallBacks()
    }


    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        logAds("Splash Ad On Resume isScreenInPause=$isScreenInPause", true)
        if (isScreenInPause) {
            isScreenInPause = false
            if (!isHandlerRunning) {
                handlerAd.postDelayed({
                    if (splashAdFailed) {
                        onAdDismissed(splashAdType.toString())
                    } else if (splashAdLoaded && isInterShowing.not()) {
                        activity?.let {
                            showSplashAd(it, "Splash Ad On Resume")
                        }
                    }
                }, 1000)
            }
        }
    }


    private fun removeCallBacks() {
        try {
            runnableSplash?.let {
                isHandlerRunning = false
                handlerAd.removeCallbacks(it)
            }
        } catch (ignored: Exception) {
        }
    }
}