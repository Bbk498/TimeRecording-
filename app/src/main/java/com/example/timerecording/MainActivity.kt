package com.example.timerecording

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import android.widget.ImageView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★★★ 全屏沉浸式：让内容延伸到状态栏和导航栏后面 ★★★
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
        // 状态栏和导航栏透明
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_main)
        // 自动检测更新（静默模式，仅发现新版本时弹窗）
        UpdateChecker.checkAuto(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // ★★★ 让导航栏背景延伸到系统导航栏下方，图标文字自动上移 ★★★
        val navBarHeight = getNavigationBarHeight()
        bottomNav.setPadding(0, 0, 0, navBarHeight)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_statistics -> StatisticsFragment()
                R.id.nav_wallpaper -> WallpaperFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }

        // ★★★ 给底部导航栏留出系统导航栏高度的 padding（避免被系统手势条挡住）★★★
        //val navBarHeight = getNavigationBarHeight()
        //val navParams = bottomNav.layoutParams as ConstraintLayout.LayoutParams
        //navParams.bottomMargin = 8 + navBarHeight
        //bottomNav.layoutParams = navParams
    }

    override fun onResume() {
        super.onResume()
        applyWallpaper()
    }

    /** 应用壁纸到根布局（覆盖全屏） */
    private fun applyWallpaper() {
        val ivBackground = findViewById<ImageView>(R.id.iv_background)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val wallpaperPath = prefs.getString("wallpaper_path", null)
        if (wallpaperPath != null && File(wallpaperPath).exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(wallpaperPath)
            ivBackground.setImageBitmap(bitmap)
        } else {
            ivBackground.setImageDrawable(null)   // 清除壁纸
        }
    }

    /** 获取系统导航栏高度 */
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}