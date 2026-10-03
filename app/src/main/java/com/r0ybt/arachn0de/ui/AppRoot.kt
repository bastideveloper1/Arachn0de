package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.state.LoadState
import kotlinx.coroutines.launch

@Composable
internal fun AppRoot(projectRepository: ProjectRepository, nodeRepository: NodeRepository) {
    val personRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showPeople by rememberSaveable { mutableStateOf(false) }
    val projectsListState = rememberLazyListState()
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(null) }
    var projectProgressById by remember(projectRepository, nodeRepository) { mutableStateOf<Map<String, NodeProgress>>(emptyMap()) }
    val load = remember(projectRepository) { LoadState() }
    LaunchedEffect(projectRepository, load.attempt) {
        load.collect(projectRepository.observeProjects()) { items ->
            projects = items
            if (selectedProjectId != null && items.none { it.id == selectedProjectId }) {
                selectedProjectId = null
            }
        }
    }
    LaunchedEffect(projects) {
        if (projects == null) {
            projectProgressById = emptyMap()
            return@LaunchedEffect
        }

        val currentProjectIds = projects.orEmpty().map { it.id }.toSet()
        projectProgressById = projectProgressById.filterKeys { it in currentProjectIds }

        projects.orEmpty().forEach { project ->
            launch {
                nodeRepository.observeProjectState(project.id).collect { snapshot ->
                    val progress = snapshot.projectProgressById[project.id]
                        ?: NodeProgress(project.id, 0, 0, 0, NodeProgressState.NO_WORK)
                    projectProgressById = buildMap {
                        putAll(projectProgressById)
                        put(project.id, progress)
                    }
                }
            }
        }
    }

    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    if (showPeople) {
        screenStates.SaveableStateProvider("people") { PeopleScreen(personRepository) { showPeople = false } }
    } else if (selectedProjectId == null) {
        ProjectDashboardScreen(
            repository = projectRepository,
            listState = projectsListState,
            projects = projects.orEmpty(),
            projectProgressById = projectProgressById,
            onOpenProject = { selectedProjectId = it.id },
            onOpenPeople = { showPeople = true },
        )
    } else if (selectedProject != null) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            ProjectNodeScreen(
                project = selectedProject,
                nodeRepository = nodeRepository,
                personRepository = personRepository,
                onOpenPeople = { showPeople = true },
                onBackToProjects = { screenStates.removeState("project:${selectedProject.id}"); selectedProjectId = null },
            )
        }
    } else {
        BackHandler { selectedProjectId = null }
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
    }
    LoadErrorDialog(load)
}
