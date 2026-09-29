package com.example.timerecording

/**
 * 设置项数据模型。
 *
 * 描述设置页中每一项的图标、名称、描述和功能标识，
 * 供 [SettingsAdapter] 渲染并在点击时根据 actionId 跳转。
 *
 * @property iconRes  图标资源 ID
 * @property title     功能名称
 * @property subtitle  功能描述
 * @property actionId  功能标识（用于点击后跳转判断）
 */
data class SettingItem(
    val iconRes: Int,        // 图标资源 ID
    val title: String,       // 功能名称
    val subtitle: String,    // 功能描述
    val actionId: String     // 功能标识（用于点击后跳转判断）
)