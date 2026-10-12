package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.data.repository.IterationState

/** Optional associations, calculated from existing IDs; never an error or persisted state. */
internal data class StoryAssociationReview(val nodeId: String, val objectiveMissing: Boolean, val sprintMissing: Boolean)

internal fun storyAssociationReview(context: String, state: IterationState): List<StoryAssociationReview> {
    val records = state.work.records
    val nodes = state.work.nodes.associateBy { it.id }
    val objectiveIds = state.work.objectives.mapTo(hashSetOf()) { it.id }
    val epicsWithObjective = records.objectiveEpics.filter { it.objectiveId in objectiveIds }.mapTo(hashSetOf()) { it.epicId }
    val storiesWithObjective = records.epicStories.filter { it.epicId in epicsWithObjective }.mapTo(hashSetOf()) { it.storyId }
    val sprintIds = state.work.nodes.filter { it.sprintMode && state.work.owners[it.id] == context }.mapTo(hashSetOf()) { it.id }
    val sprintTasks = state.work.nodes.filter { it.parentId in sprintIds }.mapTo(hashSetOf()) { it.id }
    sprintTasks.addAll(state.links.filter { it.sprintId in sprintIds }.map { it.taskId })
    val storiesWithSprint = records.storyTasks.filter { it.taskId in sprintTasks }.mapTo(hashSetOf()) { it.storyId }
    return records.roles.asSequence().filter { it.contextId == context && it.role == "STORY" && it.nodeId in nodes }
        .distinctBy { it.nodeId }.map { StoryAssociationReview(it.nodeId, it.nodeId !in storiesWithObjective, it.nodeId !in storiesWithSprint) }
        .filter { it.objectiveMissing || it.sprintMissing }.toList()
}
