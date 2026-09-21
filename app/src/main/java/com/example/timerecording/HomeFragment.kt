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

class HomeFragment : Fragment(), OnProjectActionListener {

    private lateinit var adapter: ProjectAdapter
    private var projectList: List<Project> = emptyList()

    private val viewModel: HomeViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheckDay = -1
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (projectList.any { it.state == "running" }) {
                adapter.notifyDataSetChanged()
            }

            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (today != lastCheckDay) {
                lastCheckDay = today
                viewModel.checkCrossDaySessions()
            }

            handler.postDelayed(this, 1000)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootView = view.findViewById<LinearLayout>(R.id.home_root)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        val etProjectName = view.findViewById<EditText>(R.id.et_project_name)
        val btnAdd = view.findViewById<Button>(R.id.btn_add)
        val rvProjects = view.findViewById<RecyclerView>(R.id.rv_projects)

        rvProjects.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProjectAdapter(projectList, this)
        rvProjects.adapter = adapter

        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            projectList = projects
            adapter.updateData(projectList)

            if (projects.any { it.state == "running" }) {
                TimerService.start(requireContext())
            }
        }

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

        handler.post(updateRunnable)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkCrossDaySessions()
        if (projectList.any { it.state == "running" }) {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onStartStopClick(position: Int) {
        val project = projectList[position]
        val oldState = project.state
        viewModel.startStopTimer(project)
        if (oldState == "idle" || oldState == "paused") {
            TimerService.start(requireContext())
        }
    }

    override fun onEndClick(position: Int) {
        val project = projectList[position]
        viewModel.endTimer(project) { saved ->
            val msg = if (saved) "本次计时已结束" else "本次计时少于1分钟，不记录"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetailClick(position: Int) {
        val project = projectList[position]
        val intent = Intent(requireContext(), DetailActivity::class.java)
        intent.putExtra("project_id", project.id)
        startActivity(intent)
    }

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

    override fun onRenameClick(position: Int) {
        val project = projectList[position]
        val oldName = project.name
        val editText = EditText(requireContext())
        editText.setText(oldName)
        editText.selectAll()

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
