package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.state.LoadState

@Composable
internal fun AppRoot(projectRepository: ProjectRepository, nodeRepository: NodeRepository) {
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(null) }
    val load = remember(projectRepository) { LoadState() }
    LaunchedEffect(projectRepository, load.attempt) {
        load.collect(projectRepository.observeProjects()) { items ->
            projects = items
            if (selectedProjectId != null && items.none { it.id == selectedProjectId }) {
                selectedProjectId = null
            }
        }
    }
    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    if (selectedProjectId == null) {
        ProjectDashboardScreen(
            repository = projectRepository,
            projects = projects.orEmpty(),
            onOpenProject = { selectedProjectId = it.id },
        )
    } else if (selectedProject != null) {
        ProjectNodeScreen(
            project = selectedProject,
            nodeRepository = nodeRepository,
            onBackToProjects = { selectedProjectId = null },
        )
    } else {
        BackHandler { selectedProjectId = null }
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
    }
    LoadErrorDialog(load)
}
