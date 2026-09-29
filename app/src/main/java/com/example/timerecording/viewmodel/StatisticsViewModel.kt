package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository

/**
 * 统计页面ViewModel
 *
 * 提供项目列表数据供统计图表展示使用。
 */
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    // 项目数据仓库
    private val repository = ProjectRepository.get(application)

    // 项目列表LiveData，将Flow转换为LiveData供界面观察
    val projects: LiveData<List<Project>> = repository.projectsFlow.asLiveData()
}
