package com.example.timerecording

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * 关于页 Activity。
 *
 * 职责：
 * - 通过 WebView 加载 assets/about.html 展示应用介绍信息；
 * - 处理返回键：WebView 可后退时优先后退网页历史，否则退出 Activity；
 * - 销毁时释放 WebView 资源避免内存泄漏。
 */
class AboutActivity : AppCompatActivity() {

    private var webView: WebView? = null          // 展示关于内容的 WebView

    /**
     * Activity 创建入口：配置返回按钮、初始化 WebView 并加载关于页面。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // 返回按钮顶部留出状态栏高度的 padding
        val statusBarHeight = getStatusBarHeight()
        findViewById<View>(R.id.btn_back).setPadding(0, statusBarHeight, 0, 0)

        // 返回按钮点击：关闭当前 Activity
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // 初始化 WebView 配置并加载本地关于页面
        webView = findViewById(R.id.webview_about)
        webView?.apply {
            settings.javaScriptEnabled = true        // 启用 JS
            settings.builtInZoomControls = true      // 启用缩放控件
            settings.displayZoomControls = false     // 不显示缩放按钮
            settings.loadWithOverviewMode = true      // 按屏幕宽度自适应
            settings.useWideViewPort = true           // 使用宽视口
            webViewClient = WebViewClient()          // 在本 WebView 内打开链接
            // 加载 assets 目录下的 about.html
            loadUrl("file:///android_asset/about.html")
        }
    }

    /**
     * 返回键处理：WebView 有历史记录时回退网页，否则退出 Activity。
     */
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 销毁时释放 WebView，防止其持有 Activity 导致内存泄漏。
     */
    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    /** 获取系统状态栏高度（像素），无法获取时回退为 24dp */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
