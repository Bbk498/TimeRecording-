package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.launch

/**
 * 主页ViewModel
 *
 * 管理项目列表的展示与计时操作，包括：
 * 新增项目、开始/暂停/继续计时、结束计时、删除项目、重命名项目、跨天会话检查。
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // 项目数据仓库
    private val repository = ProjectRepository.get(application)

    // 项目列表LiveData，将Flow转换为LiveData供界面观察
    val projects: LiveData<List<Project>> = repository.projectsFlow.asLiveData()

    /**
     * 添加新项目
     *
     * @param name 项目名称
     */
    fun addProject(name: String) {
        viewModelScope.launch {
            repository.addProject(name)
        }
    }

    /**
     * 切换项目的计时状态（开始/暂停/继续）
     *
     * 根据项目当前状态执行不同操作：
     * - idle: 开始计时，记录当前时间为开始时刻
     * - running: 暂停计时，计算已运行时长并累计
     * - paused: 继续计时，以当前时间作为新的开始时刻
     *
     * @param project 目标项目
     */
    fun startStopTimer(project: Project) {
        viewModelScope.launch {
            when (project.state) {
                "idle" -> {
                    val now = System.currentTimeMillis()
                    repository.startTimer(project.id, now)
                }
                "running" -> {
                    // 计算从上次开始到现在经过的时间
                    val elapsed = System.currentTimeMillis() - project.currentStartTime
                    val newElapsed = project.currentElapsedMillis + elapsed
                    repository.pauseTimer(project.id, project.sessionStartTime, newElapsed)
                }
                "paused" -> {
                    val now = System.currentTimeMillis()
                    repository.continueTimer(project.id, project.sessionStartTime, project.currentElapsedMillis)
                }
            }
        }
    }

    /**
     * 结束计时并保存会话记录
     *
     * @param project 目标项目
     * @param onResult 回调函数，参数为是否保存成功
     */
    fun endTimer(project: Project, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var currentElapsed = project.currentElapsedMillis
            var endTime = System.currentTimeMillis()
            if (project.state == "running") {
                // 正在运行中：累加从上次开始到现在的时长
                currentElapsed += System.currentTimeMillis() - project.currentStartTime
            } else if (project.state == "paused") {
                // 已暂停：结束时间取上次暂停时刻
                endTime = project.currentStartTime
            }
            val saved = repository.endTimer(project.id, project.sessionStartTime, currentElapsed, endTime)
            onResult(saved)
        }
    }

    /**
     * 删除项目
     * 若项目正在计时中，先暂停再删除
     *
     * @param project 目标项目
     */
    fun deleteProject(project: Project) {
        viewModelScope.launch {
            if (project.state == "running") {
                // 正在计时中，先计算并暂停
                val elapsed = System.currentTimeMillis() - project.currentStartTime
                val newElapsed = project.currentElapsedMillis + elapsed
                repository.pauseTimer(project.id, project.sessionStartTime, newElapsed)
            }
            repository.deleteProject(project.id)
        }
    }

    /**
     * 重命名项目
     *
     * @param project 目标项目
     * @param newName 新名称
     */
    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            repository.renameProject(project.id, newName)
        }
    }

    /**
     * 检查并拆分跨天计时会话
     * 将跨天运行的计时会话按天拆分为多个会话
     */
    fun checkCrossDaySessions() {
        viewModelScope.launch {
            repository.checkAndSplitCrossDaySessions()
        }
    }
}
