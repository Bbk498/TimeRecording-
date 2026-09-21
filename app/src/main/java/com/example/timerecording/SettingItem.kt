package com.example.timerecording

data class SettingItem(
    val iconRes: Int,        // 图标资源 ID
    val title: String,       // 功能名称
    val subtitle: String,    // 功能描述
    val actionId: String     // 功能标识（用于点击后跳转判断）
)