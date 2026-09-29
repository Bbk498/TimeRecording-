package com.example.timerecording

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timerecording.service.TimerService
import com.example.timerecording.viewmodel.HomeViewModel

/**
 * 首页 Fragment。
 *
 * 职责：
 * - 展示项目列表（RecyclerView），支持添加、删除、改名、开始/暂停/继续、结束计时、查看详情；
 * - 通过 [HomeViewModel] 观察项目数据并自动刷新 UI；
 * - 每秒轮询更新运行中项目的计时显示，并检测跨天会话；
 * - 依赖 [TimerService] 在有项目计时中时保持后台运行。
 */
class HomeFragment : Fragment(), OnProjectActionListener {

    private lateinit var adapter: ProjectAdapter                     // 项目列表适配器
    private var projectList: List<Project> = emptyList()             // 当前项目数据

    private val viewModel: HomeViewModel by viewModels()             // 首页视图模型

    private val handler = Handler(Looper.getMainLooper())            // 主线程 Handler，用于定时刷新
    private var lastCheckDay = -1                                     // 上次跨天检查的"年中第几天"，用于判断日期变更
    // 每秒执行的刷新任务：刷新计时显示并按天检查跨天会话
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 有正在计时的项目时刷新列表以更新显示时长
            if (projectList.any { it.state == "running" }) {
                adapter.notifyDataSetChanged()
            }

            // 检测系统日期是否已变化（跨天），变化则触发跨天会话检查
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (today != lastCheckDay) {
                lastCheckDay = today
                viewModel.checkCrossDaySessions()
            }

            // 每秒循环一次
            handler.postDelayed(this, 1000)
        }
    }

    /** 获取系统状态栏高度（像素），无法获取时回退为 24dp */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    /** 加载首页布局 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    /**
     * 视图创建完成后初始化列表、观察数据、绑定事件。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 为根布局顶部设置状态栏高度的 padding，避免内容被状态栏遮挡
        val rootView = view.findViewById<LinearLayout>(R.id.home_root)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        // 绑定输入框、添加按钮和项目列表控件
        val etProjectName = view.findViewById<EditText>(R.id.et_project_name)
        val btnAdd = view.findViewById<Button>(R.id.btn_add)
        val rvProjects = view.findViewById<RecyclerView>(R.id.rv_projects)

        // 初始化 RecyclerView 与适配器
        rvProjects.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProjectAdapter(projectList, this)
        rvProjects.adapter = adapter

        // 观察项目数据变化：更新列表，有计时中项目则启动后台计时服务
        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            projectList = projects
            adapter.updateData(projectList)

            if (projects.any { it.state == "running" }) {
                TimerService.start(requireContext())
            }
        }

        // 添加项目按钮：输入非空时新增项目并清空输入框
        btnAdd.setOnClickListener {
            val name = etProjectName.text.toString().trim()
            if (name.isNotEmpty()) {
                viewModel.addProject(name)
                etProjectName.text.clear()
                Toast.makeText(requireContext(), "已添加：$name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "项目名不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        // 启动每秒刷新任务
        handler.post(updateRunnable)
    }

    /**
     * 页面回到前台时检查跨天会话，并重启定时刷新任务。
     */
    override fun onResume() {
        super.onResume()
        // 回到前台时重新检查跨天会话
        viewModel.checkCrossDaySessions()
        // 若有计时中项目，重启每秒刷新任务
        if (projectList.any { it.state == "running" }) {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        adapter.notifyDataSetChanged()
    }

    /** 销毁视图时移除定时刷新回调，避免内存泄漏 */
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * 开始/暂停/继续 计时按钮回调。
     * 空闲或暂停态启动计时时，同时启动后台计时服务。
     */
    override fun onStartStopClick(position: Int) {
        val project = projectList[position]
        val oldState = project.state
        viewModel.startStopTimer(project)
        // 从空闲或暂停态开始计时时启动后台服务
        if (oldState == "idle" || oldState == "paused") {
            TimerService.start(requireContext())
        }
    }

    /**
     * 结束计时按钮回调：结束当前会话并保存，少于1分钟不记录。
     */
    override fun onEndClick(position: Int) {
        val project = projectList[position]
        viewModel.endTimer(project) { saved ->
            val msg = if (saved) "本次计时已结束" else "本次计时少于1分钟，不记录"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 查看详情按钮回调：跳转到 DetailActivity 并传入项目 ID。
     */
    override fun onDetailClick(position: Int) {
        val project = projectList[position]
        val intent = Intent(requireContext(), DetailActivity::class.java)
        intent.putExtra("project_id", project.id)
        startActivity(intent)
    }

    /**
     * 删除项目按钮回调：弹出二次确认对话框后删除。
     */
    override fun onDeleteClick(position: Int) {
        val project = projectList[position]
        val name = project.name
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("真的想好删掉项目「$name」了吗？\n该操作不可撤销！")
            .setPositiveButton("确认") { _, _ ->
                viewModel.deleteProject(project)
                Toast.makeText(requireContext(), "已删除：$name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("我再想想", null)
            .show()
    }

    /**
     * 重命名项目按钮回调：点击项目名触发，两步确认后修改名称。
     */
    override fun onRenameClick(position: Int) {
        val project = projectList[position]
        val oldName = project.name
        val editText = EditText(requireContext())
        editText.setText(oldName)
        editText.selectAll()

        // 第一步：输入新名称
        AlertDialog.Builder(requireContext())
            .setTitle("修改项目名称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == oldName) {
                    Toast.makeText(requireContext(), "名称未变化", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // 第二步：确认修改前再次确认
                AlertDialog.Builder(requireContext())
                    .setTitle("确认修改")
                    .setMessage("将项目「$oldName」改名为「$newName」，确定吗？")
                    .setPositiveButton("我想好了") { _, _ ->
                        viewModel.renameProject(project, newName)
                        Toast.makeText(requireContext(), "已改名为：$newName", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("我再想想", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
