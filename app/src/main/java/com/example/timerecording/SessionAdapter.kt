package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * 计时会话列表适配器。
 *
 * 用于在详情页展示某个项目下的所有计时会话记录，
 * 每条记录显示开始时间、结束时间和持续时长。
 *
 * @param sessions 会话记录列表
 */
class SessionAdapter(private val sessions: List<Session>) :
    RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    // 日期时间格式化器（月-日 时:分）
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    /**
     * 会话列表项的 ViewHolder，持有各项子控件的引用。
     */
    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStart: TextView = itemView.findViewById(R.id.tv_session_start)       // 开始时间
        val tvEnd: TextView = itemView.findViewById(R.id.tv_session_end)           // 结束时间
        val tvDuration: TextView = itemView.findViewById(R.id.tv_session_duration)// 持续时长
    }

    /** 创建列表项视图与 ViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    /** 绑定会话数据到列表项：格式化显示开始、结束时间和时长 */
    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvStart.text = "开始: ${dateFormat.format(Date(session.startTime))}"
        holder.tvEnd.text = "结束: ${dateFormat.format(Date(session.endTime))}"
        holder.tvDuration.text = formatDuration(session.durationMillis / 1000)
    }

    /** 返回会话列表项数量 */
    override fun getItemCount(): Int = sessions.size

    /**
     * 将总秒数格式化为可读时长字符串。
     * - 大于等于 1 小时：显示 H:MM:SS
     * - 不足 1 小时：显示 MM:SS
     */
    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}