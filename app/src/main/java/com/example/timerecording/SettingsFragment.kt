package com.example.timerecording

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 设置页 Fragment。
 *
 * 职责：
 * - 以列表展示各设置项（壁纸、数据导入导出、关于、检查更新）；
 * - 点击设置项时根据 actionId 跳转到对应 Activity 或触发更新检查。
 */
class SettingsFragment : Fragment() {

    /** 加载设置页布局 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    /**
     * 视图创建完成后构建设置项列表并绑定点击跳转逻辑。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 状态栏高度 padding：根布局顶部留出状态栏高度，避免内容被遮挡
        val rootView = view.findViewById<LinearLayout>(R.id.settings_root)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        // 构建设置项列表
        val settings = listOf(
            SettingItem(
                iconRes = android.R.drawable.ic_menu_gallery,
                title = "更换壁纸",
                subtitle = "从相册选择图片作为主页背景",
                actionId = "wallpaper"
            ),
            SettingItem(
                iconRes = android.R.drawable.ic_menu_save,
                title = "数据导入与导出",
                subtitle = "备份或恢复你的项目与计时记录",
                actionId = "transfer"
            ),
            SettingItem(
                iconRes = android.R.drawable.ic_menu_info_details,
                title = "关于应用",
                subtitle = "TimeRecording",
                actionId = "about"
            ),
            SettingItem(
                iconRes = android.R.drawable.stat_sys_download,
                title = "检查更新",
                subtitle = "检查是否有新版本可用",
                actionId = "update"
            )
        )

        // 配置 RecyclerView 与适配器，点击项根据 actionId 跳转
        val rvSettings = view.findViewById<RecyclerView>(R.id.rv_settings)
        rvSettings.layoutManager = LinearLayoutManager(requireContext())
        rvSettings.adapter = SettingsAdapter(settings) { item ->
            when (item.actionId) {
                // 跳转壁纸设置页
                "wallpaper" -> {
                    startActivity(Intent(requireContext(), WallpaperSettingsActivity::class.java))
                }
                // 跳转数据导入导出页
                "transfer" -> {
                    startActivity(Intent(requireContext(), DataTransferActivity::class.java))
                }
                // 跳转关于页
                "about" -> {
                    startActivity(Intent(requireContext(), AboutActivity::class.java))
                }
                // 手动触发更新检查
                "update" -> {
                    UpdateChecker.checkManual(requireContext())
                }
            }
        }
    }

    /** 获取系统状态栏高度（像素），无法获取时回退为 24dp */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
