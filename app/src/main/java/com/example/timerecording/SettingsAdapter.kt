package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 设置项列表适配器。
 *
 * 用于在设置页展示各项设置（图标 + 标题 + 副标题），
 * 点击项时通过回调通知宿主执行对应跳转。
 *
 * @param items      设置项数据列表
 * @param onItemClick 点击项时的回调
 */
class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingViewHolder>() {

    /**
     * 设置列表项的 ViewHolder，持有图标、标题、副标题控件的引用。
     */
    class SettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_setting_icon)       // 图标
        val tvTitle: TextView = itemView.findViewById(R.id.tv_setting_title)       // 标题
        val tvSubtitle: TextView = itemView.findViewById(R.id.tv_setting_subtitle) // 副标题
    }

    /** 创建列表项视图与 ViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingViewHolder(view)
    }

    /** 绑定设置项数据到列表项：图标、标题、副标题、点击事件 */
    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        val item = items[position]
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvTitle.text = item.title
        holder.tvSubtitle.text = item.subtitle

        // 点击整项触发回调
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    /** 返回设置项数量 */
    override fun getItemCount(): Int = items.size
}