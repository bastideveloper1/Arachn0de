package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*

internal object ConversionFixture {
    val photo = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png"
    fun complete(): BackupData {
        val base = BackupFixture.complete()
        val startDay = java.time.LocalDate.of(2025, 1, 1).toEpochDay()
        return base.copy(nodes = base.nodes.map { when (it.id) {
            "root" -> it.copy(sprintMode = true, priority = "HIGH", creationGroupId = "shared")
            "inner" -> it.copy(sprintMode = true)
            "bill" -> it.copy(workState = "VALIDATED", priority = "MEDIUM")
            else -> it
        } } + listOf(NodeEntity("work", "p", "root", "Working", "Current", false, 3, 1, 2, 10, 30,
            priority = "HIGH", creationGroupId = "shared", workState = "DOING"),
            NodeEntity("outside", "p", null, "Outside", "", false, 9, 1, 2, purpose = "LAYER", creationGroupId = "shared")),
            tags = listOf(TagEntity("tag", "Tag", "tag")), nodeTags = listOf(NodeTagEntity("root", "tag"), NodeTagEntity("work", "tag")),
            technologies = listOf(TechnologyEntity("tech", "Tool", null), TechnologyEntity("root-tech", "Root tool", null)),
            nodeTechnologies = listOf(NodeTechnologyEntity("root", "root-tech"), NodeTechnologyEntity("bill", "tech")),
            projectTechnologies = listOf(ProjectTechnologyEntity("p", "tech")),
            nodeEvents = listOf(NodeEventEntity("root-event", "root", "CREATED", 10), NodeEventEntity("bill-event", "bill", "COMPLETED", 20)),
            creationDefaults = listOf(CreationDefaultsEntity("N:root", "p", "root", priority = "HIGH", tagsOverride = true, peopleOverride = true)),
            defaultsTags = listOf(CreationDefaultsTagEntity("N:root", "tag")), defaultsPeople = listOf(CreationDefaultsPersonEntity("N:root", "r")),
            recurrenceRules = listOf(RecurrenceRuleEntity("rule", "p", "root", "Repeat", "", null, null, startDay,
                "DAILY", 1, null, 0, "ACTIVE", "UTC", 600, null)), recurrenceAssignments = listOf(RecurrencePersonEntity("rule", "r")),
            recurrenceTags = listOf(RecurrenceTagEntity("rule", "tag")),
            projectPhotos = listOf(ProjectPhotoEntity("project-photo", "p", null, photo, 2.4f, -.3f, .7f)),
            projectPhotoImages = mapOf(photo to BackupFixture.png()),
            nodeSortPreferences = listOf(NodeSortPreferenceEntity("p:project-root", "DUE_ASC"), NodeSortPreferenceEntity("p:root", "PRIORITY"),
                NodeSortPreferenceEntity("p:inner", "CREATED_NEWEST")))
    }
}
