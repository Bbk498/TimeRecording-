package com.example.timerecording

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val statusBarHeight = getStatusBarHeight()
        findViewById<View>(R.id.btn_back).setPadding(0, statusBarHeight, 0, 0)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        webView = findViewById(R.id.webview_about)
        webView?.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/about.html")
        }
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
