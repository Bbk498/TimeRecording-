package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.launch

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository.get(application)

    val project = MutableLiveData<Project?>()

    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            project.value = repository.getProject(projectId)
        }
    }
}
