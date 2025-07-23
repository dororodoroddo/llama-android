package com.dororogames.tarotllama

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

class AndroidBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val llama = LlamaBridge.instance()

    @JavascriptInterface
    fun showAd() {
        activity.runOnUiThread {
            activity.showBannerAd()
        }
    }

    @JavascriptInterface
    fun hideAd() {
        activity.runOnUiThread {
            activity.hideBannerAd()
        }
    }

    @JavascriptInterface
    fun showInterstitialAd() {
        activity.runOnUiThread {
            activity.showInterstitialAd()
        }
    }
    @JavascriptInterface
    fun askLlama(prompt: String, key: String) {
        CoroutineScope(Dispatchers.Main).launch {
            llama.send(prompt, activity, formatChat = false)
                .onCompletion {
                    evaluate("window.onLlamaAnswerEnd(\"${escapeJsString(key)}\");")
                }
                .collect { text ->
                    evaluate("window.onLlamaAnswer(\"${escapeJsString(text)}\", \"${escapeJsString(key)}\");")
                }
        }
    }
    private fun evaluate(jsCode: String) {
        activity.runOnUiThread {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    private fun escapeJsString(raw: String): String {
        return raw.replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
    }
}
