package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(private val sessions: List<Session>) :
    RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStart: TextView = itemView.findViewById(R.id.tv_session_start)
        val tvEnd: TextView = itemView.findViewById(R.id.tv_session_end)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_session_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvStart.text = "开始: ${dateFormat.format(Date(session.startTime))}"
        holder.tvEnd.text = "结束: ${dateFormat.format(Date(session.endTime))}"
        holder.tvDuration.text = formatDuration(session.durationMillis / 1000)
    }

    override fun getItemCount(): Int = sessions.size

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