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

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 状态栏高度 padding
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
                subtitle = "TimeRecording v1.3",
                actionId = "about"
            ),
            SettingItem(
                iconRes = android.R.drawable.stat_sys_download,
                title = "检查更新",
                subtitle = "检查是否有新版本可用",
                actionId = "update"
            )
        )

        val rvSettings = view.findViewById<RecyclerView>(R.id.rv_settings)
        rvSettings.layoutManager = LinearLayoutManager(requireContext())
        rvSettings.adapter = SettingsAdapter(settings) { item ->
            when (item.actionId) {
                "wallpaper" -> {
                    startActivity(Intent(requireContext(), WallpaperSettingsActivity::class.java))
                }
                "transfer" -> {
                    startActivity(Intent(requireContext(), DataTransferActivity::class.java))
                }
                "about" -> {
                    startActivity(Intent(requireContext(), AboutActivity::class.java))
                }
                "update" -> {
                    UpdateChecker.checkManual(requireContext())
                }
            }
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
