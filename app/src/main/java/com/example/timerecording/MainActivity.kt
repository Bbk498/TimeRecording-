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

/**
 * 应用主界面 Activity。
 *
 * 职责：
 * - 承载底部导航栏（首页 / 统计 / 设置）与对应的 Fragment 切换；
 * - 配置全屏沉浸式 UI（状态栏、导航栏透明）；
 * - 在 onResume 时应用用户设置的壁纸；
 * - 启动时静默触发自动更新检查。
 */
class MainActivity : AppCompatActivity() {

    /**
     * Activity 创建入口：初始化全屏沉浸式 UI、加载布局、注册导航栏切换逻辑。
     */
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

        // 加载主界面布局
        setContentView(R.layout.activity_main)
        // 自动检测更新（静默模式，仅发现新版本时弹窗）
        UpdateChecker.checkAuto(this)

        // 获取底部导航栏实例
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // ★★★ 让导航栏背景延伸到系统导航栏下方，图标文字自动上移 ★★★
        // 根据系统导航栏高度设置底部 padding，避免内容被手势条遮挡
        val navBarHeight = getNavigationBarHeight()
        bottomNav.setPadding(0, 0, 0, navBarHeight)

        // 首次进入时默认加载首页 Fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        // 底部导航栏选项切换监听：根据选中项加载对应 Fragment
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()               // 首页
                R.id.nav_statistics -> StatisticsFragment()   // 统计页
                R.id.nav_wallpaper -> SettingsFragment()      // 设置页
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

    /**
     * 页面回到前台时重新应用壁纸，保证用户在设置页修改壁纸后即时生效。
     */
    override fun onResume() {
        super.onResume()
        applyWallpaper()
    }

    /** 应用壁纸到根布局（覆盖全屏） */
    private fun applyWallpaper() {
        val ivBackground = findViewById<ImageView>(R.id.iv_background)
        // 从 SharedPreferences 读取用户设置的壁纸路径
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val wallpaperPath = prefs.getString("wallpaper_path", null)
        if (wallpaperPath != null && File(wallpaperPath).exists()) {
            // 壁纸文件存在，解码并设置为背景
            val bitmap = android.graphics.BitmapFactory.decodeFile(wallpaperPath)
            ivBackground.setImageBitmap(bitmap)
        } else {
            // 无壁纸设置，清除背景
            ivBackground.setImageDrawable(null)   // 清除壁纸
        }
    }

    /** 获取系统导航栏高度（像素），用于沉浸式布局计算 */
    private fun getNavigationBarHeight(): Int {
        // 通过系统资源标识符读取导航栏高度，无法获取时返回 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * 使用 Fragment 事务将目标 Fragment 加载到 fragment_container 容器中。
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}