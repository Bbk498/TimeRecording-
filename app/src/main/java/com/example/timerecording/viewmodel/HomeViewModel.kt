package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository.get(application)

    val projects: LiveData<List<Project>> = repository.projectsFlow.asLiveData()

    fun addProject(name: String) {
        viewModelScope.launch {
            repository.addProject(name)
        }
    }

    fun startStopTimer(project: Project) {
        viewModelScope.launch {
            when (project.state) {
                "idle" -> {
                    val now = System.currentTimeMillis()
                    repository.startTimer(project.id, now)
                }
                "running" -> {
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

    fun endTimer(project: Project, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var currentElapsed = project.currentElapsedMillis
            var endTime = System.currentTimeMillis()
            if (project.state == "running") {
                currentElapsed += System.currentTimeMillis() - project.currentStartTime
            } else if (project.state == "paused") {
                endTime = project.currentStartTime
            }
            val saved = repository.endTimer(project.id, project.sessionStartTime, currentElapsed, endTime)
            onResult(saved)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            if (project.state == "running") {
                val elapsed = System.currentTimeMillis() - project.currentStartTime
                val newElapsed = project.currentElapsedMillis + elapsed
                repository.pauseTimer(project.id, project.sessionStartTime, newElapsed)
            }
            repository.deleteProject(project.id)
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            repository.renameProject(project.id, newName)
        }
    }

    fun checkCrossDaySessions() {
        viewModelScope.launch {
            repository.checkAndSplitCrossDaySessions()
        }
    }
}
