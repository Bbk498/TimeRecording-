package com.example.timerecording

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 项目操作回调接口。
 * 由宿主（如 [HomeFragment]）实现，用于响应项目列表中的各类按钮点击。
 */
interface OnProjectActionListener {
    fun onStartStopClick(position: Int)  // 开始/暂停/继续
    fun onDeleteClick(position: Int)     //删除
    fun onEndClick(position: Int)        // 结束
    fun onDetailClick(position: Int)     // 查看详情

    fun onRenameClick(position: Int)
}

/**
 * 项目列表适配器。
 *
 * 负责渲染每个项目的名称、实时计时、状态按钮，
 * 并将按钮点击事件通过 [OnProjectActionListener] 回调给宿主。
 *
 * @param projects 项目数据列表
 * @param listener 项目操作回调监听器
 */
class ProjectAdapter(
    private var projects: List<Project>,
    private val listener: OnProjectActionListener
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    /**
     * 项目列表项的 ViewHolder，持有各项子控件的引用。
     */
    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_project_name)           // 项目名
        val tvTime: TextView = itemView.findViewById(R.id.tv_total_time)             // 实时时长
        val btnStartStop: Button = itemView.findViewById(R.id.btn_start_stop)       // 开始/暂停/继续
        val btnEnd: Button = itemView.findViewById(R.id.btn_end)                     // 结束计时
        val btnDetail: ImageButton = itemView.findViewById(R.id.btn_detail)          // 查看详情
        val btnDelete: Button = itemView.findViewById(R.id.btn_delete)               // 删除项目
    }

    /** 创建列表项视图与 ViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    /** 绑定项目数据到列表项视图：名称、时长、按钮状态、点击事件 */
    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]

        // 设置项目名称，点击名称触发重命名
        holder.tvName.text = project.name
        holder.tvName.setOnClickListener {
            listener.onRenameClick(position)
        }

        // 计算当前应显示的时长（当前会话的累计时长）
        val displaySeconds = when (project.state) {
            "running" -> {
                // 运行中：已累计时长 + 从本次开始到现在的流逝时长
                val elapsed = (System.currentTimeMillis() - project.currentStartTime) / 1000
                project.currentElapsedMillis / 1000 + elapsed
            }
            "paused" -> project.currentElapsedMillis / 1000   // 暂停：显示已累计时长
            else -> 0L                                        // 空闲：显示 0
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

        // 绑定各按钮点击事件到监听器
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

    /** 返回项目列表项数量 */
    override fun getItemCount(): Int = projects.size

    /**
     * 将总秒数格式化为可读时长字符串。
     * - 大于等于 1 小时：显示 H:MM:SS
     * - 不足 1 小时：显示 MM:SS
     */
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

    /**
     * 更新项目数据并刷新整个列表。
     */
    fun updateData(newProjects: List<Project>) {
        this.projects = newProjects
        notifyDataSetChanged()
    }
}