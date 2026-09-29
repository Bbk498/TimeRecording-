package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.launch

/**
 * 详情页面ViewModel
 *
 * 负责根据项目ID加载单个项目的完整数据，供详情界面展示。
 */
class DetailViewModel(application: Application) : AndroidViewModel(application) {

    // 项目数据仓库
    private val repository = ProjectRepository.get(application)

    // 当前加载的项目数据（可观察，界面据此更新显示）
    val project = MutableLiveData<Project?>()

    /**
     * 异步加载指定项目
     *
     * @param projectId 项目ID
     */
    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            project.value = repository.getProject(projectId)
        }
    }
}
