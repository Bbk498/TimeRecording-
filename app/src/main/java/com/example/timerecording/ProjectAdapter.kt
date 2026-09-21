package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

interface OnProjectActionListener {
    fun onStartStopClick(position: Int)  // 开始/暂停/继续
    fun onDeleteClick(position: Int)     //删除
    fun onEndClick(position: Int)        // 结束
    fun onDetailClick(position: Int)     // 查看详情

    fun onRenameClick(position: Int)
}

class ProjectAdapter(
    private var projects: List<Project>,
    private val listener: OnProjectActionListener
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_project_name)
        val tvTime: TextView = itemView.findViewById(R.id.tv_total_time)
        val btnStartStop: Button = itemView.findViewById(R.id.btn_start_stop)
        val btnEnd: Button = itemView.findViewById(R.id.btn_end)
        val btnDetail: ImageButton = itemView.findViewById(R.id.btn_detail)
        val btnDelete: Button = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]

        holder.tvName.text = project.name
        holder.tvName.setOnClickListener {
            listener.onRenameClick(position)
        }

        // 计算当前应显示的时长（当前会话的累计时长）
        val displaySeconds = when (project.state) {
            "running" -> {
                val elapsed = (System.currentTimeMillis() - project.currentStartTime) / 1000
                project.currentElapsedMillis / 1000 + elapsed
            }
            "paused" -> project.currentElapsedMillis / 1000
            else -> 0L
        }
        holder.tvTime.text = formatTime(displaySeconds)

        // 根据状态设置按钮文字和可见性
        // 根据状态设置按钮文字和可见性
        when (project.state) {
            "idle" -> {
                holder.btnStartStop.text = "开始"
                holder.btnEnd.visibility = View.GONE
                holder.btnDelete.visibility = View.VISIBLE   // ← idle 时显示删除
            }
            "running" -> {
                holder.btnStartStop.text = "暂停"
                holder.btnEnd.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE     // ← 计时中隐藏删除
            }
            "paused" -> {
                holder.btnStartStop.text = "继续"
                holder.btnEnd.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.GONE  // ← 暂停时显示删除
            }
        }

        holder.btnStartStop.setOnClickListener {
            listener.onStartStopClick(position)
        }
        holder.btnDelete.setOnClickListener {
            listener.onDeleteClick(position)
        }
        holder.btnEnd.setOnClickListener {
            listener.onEndClick(position)
        }
        holder.btnDetail.setOnClickListener {
            listener.onDetailClick(position)
        }

    }

    override fun getItemCount(): Int = projects.size

    private fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun updateData(newProjects: List<Project>) {
        this.projects = newProjects
        notifyDataSetChanged()
    }
}