package com.example.timerecording.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.timerecording.Project
import com.example.timerecording.data.repository.ProjectRepository

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository.get(application)

    val projects: LiveData<List<Project>> = repository.projectsFlow.asLiveData()
}
