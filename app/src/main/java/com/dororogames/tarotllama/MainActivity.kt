package com.dororogames.tarotllama

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    private val llama = LlamaBridge.instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )



        setupAds()
        setupWebView()
        // Llama 모델 초기화
        lifecycleScope.launch {
            llama.init(this@MainActivity)
        }
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        webView.setBackgroundColor(Color.BLACK)
        webView.addJavascriptInterface(AndroidBridge(this, webView), "AndroidBridge")
        webView.loadUrl("file:///android_asset/web/index.html")

        ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->

            val cutout = insets.displayCutout
            val rawTop = cutout?.safeInsetTop ?: 0
            val maxPx = 44
            val top = if (rawTop > maxPx) maxPx else rawTop

            val bottom = cutout?.safeInsetBottom ?: 0
            val left = cutout?.safeInsetLeft ?: 0
            val right = cutout?.safeInsetRight ?: 0

            val js = """
            javascript:(function() {
                document.documentElement.style.setProperty('--safe-top', '${top}px');
                document.documentElement.style.setProperty('--safe-right', '${right}px');
                document.documentElement.style.setProperty('--safe-bottom', '${bottom}px');
                document.documentElement.style.setProperty('--safe-left', '${left}px');
            })();
        """.trimIndent()

            webView.evaluateJavascript(js, null)

            onBackPressedDispatcher.addCallback(this) {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }

            insets
        }
    }


    private fun setupAds() {
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        loadInterstitialAd()
    }

    private fun loadInterstitialAd() {
        val adUnitId = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712" // 테스트 ID
        } else {
            "ca-app-pub-2635143640472461/2036568950" // 실제 ID
        }

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        loadInterstitialAd() // 광고 닫혔을 때 재로드
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        interstitialAd = null // 보여주기 실패 → 제거
                    }

                    override fun onAdShowedFullScreenContent() {
                        interstitialAd = null // 한번 보여준 후 재사용 금지
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null // 로드 실패 → 제거
            }
        })
    }
    fun showBannerAd() {
        adView.visibility = View.VISIBLE
    }

    fun hideBannerAd() {
        adView.visibility = View.GONE
    }

    fun showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd?.show(this)
        } else {
            loadInterstitialAd()
        }
    }
}