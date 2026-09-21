package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingViewHolder>() {

    class SettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_setting_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_setting_title)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tv_setting_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        val item = items[position]
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvTitle.text = item.title
        holder.tvSubtitle.text = item.subtitle

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}