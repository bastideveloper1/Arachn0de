package com.r0ybt.arachn0de.backup

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import com.r0ybt.arachn0de.data.local.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit field mapping is versioned independently of Room and of the outer container. */
internal object BackupJson {
    fun encode(data: BackupData): ByteArray {
        data.validate()
        // Bound user text before constructing JSON, which otherwise could exhaust the heap.
        val textBytes = data.calendarEvents.sumOf {(it.id+it.title+it.date+it.description).toByteArray().size.toLong()} + data.calendarEventPeople.sumOf {(it.eventId+it.personId).toByteArray().size.toLong()} + data.personMemories.sumOf {listOf(it.id,it.personId,it.date.orEmpty(),it.description).sumOf {text->text.toByteArray().size.toLong()}} + data.personMemoryImages.sumOf {listOf(it.id,it.memoryId,it.attachmentId.orEmpty(),it.legacyPhotoId.orEmpty(),it.date.orEmpty(),it.description).sumOf {text->text.toByteArray().size.toLong()}} + data.metroLearningEvents.sumOf {it.payload.toByteArray().size.toLong()+it.id.toByteArray().size+it.sessionId.toByteArray().size} + data.ganttAnnotations.sumOf {it.text.toByteArray().size.toLong()+it.id.toByteArray().size+it.contextId.toByteArray().size} + data.sprintStories.sumOf {it.sprintId.toByteArray().size.toLong()+it.storyId.toByteArray().size} + data.objectiveWork.sumOf {it.objectiveId.toByteArray().size.toLong()+it.nodeId.toByteArray().size} + data.localIdentities.sumOf {it.slot.toByteArray().size.toLong()+it.personId.toByteArray().size} + data.managementLayers.sumOf {listOf(it.nodeId,it.contextId,it.kind).sumOf {s->s.toByteArray().size.toLong()}} + data.personPhotos.sumOf {it.id.toByteArray().size.toLong()+it.personId.toByteArray().size+it.file.toByteArray().size} + data.personBirthdays.sumOf {it.personId.toByteArray().size.toLong()+it.date.toByteArray().size} + data.personTechnologies.sumOf {it.personId.toByteArray().size.toLong()+it.technologyId.toByteArray().size+it.proficiency.toByteArray().size} + data.personProfiles.sumOf {p->(listOf(p.personId)+p.values()).sumOf {it.toByteArray().size.toLong()}} + data.personRelations.sumOf {r->listOf(r.id,r.sourceId,r.targetId,r.category,r.label,r.inverseLabel,r.meaning,r.mode,r.relationKey).sumOf {it.toByteArray().size.toLong()}} + data.privatePreferences.sumOf { it.name.toByteArray().size.toLong() + it.payload.toByteArray().size } + data.projectPhotos.sumOf { it.id.toByteArray().size.toLong() + it.file.toByteArray().size + (it.projectId?.toByteArray()?.size ?: 0) + (it.nodeId?.toByteArray()?.size ?: 0) } + data.technologies.sumOf { it.name.toByteArray().size.toLong() + it.id.toByteArray().size + (it.iconFile?.toByteArray()?.size ?: 0) } + data.attachmentFiles.sumOf { it.originalName.toByteArray().size.toLong() + it.storageName.toByteArray().size + it.id.toByteArray().size + it.mimeType.toByteArray().size + it.sha256.toByteArray().size } + data.tags.sumOf { it.name.toByteArray().size.toLong() + it.normalizedName.toByteArray().size } + data.projects.sumOf { it.name.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.nodes.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.persons.sumOf { it.name.toByteArray().size.toLong() } + data.recurrenceRules.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.creationDefaults.sumOf { it.id.toByteArray().size.toLong() + (it.projectId?.toByteArray()?.size ?: 0) + (it.nodeId?.toByteArray()?.size ?: 0) } +
            data.defaultsTags.sumOf { it.defaultsId.toByteArray().size.toLong() + it.tagId.toByteArray().size } +
            data.defaultsPeople.sumOf { it.defaultsId.toByteArray().size.toLong() + it.personId.toByteArray().size } +
            data.managementRoles.sumOf {it.nodeId.toByteArray().size.toLong()+it.contextId.toByteArray().size+it.role.toByteArray().size} +
            data.objectiveEpics.sumOf {it.objectiveId.toByteArray().size.toLong()+it.epicId.toByteArray().size} +
            data.epicStories.sumOf {it.epicId.toByteArray().size.toLong()+it.storyId.toByteArray().size} +
            data.storyTasks.sumOf {it.storyId.toByteArray().size.toLong()+it.taskId.toByteArray().size} +
            data.iterationPlans.sumOf {it.id.toByteArray().size.toLong()+it.contextId.toByteArray().size+it.sprintId.toByteArray().size+it.goal.toByteArray().size+it.notes.toByteArray().size+it.state.toByteArray().size} +
            data.iterationCycles.sumOf {it.id.toByteArray().size.toLong()+it.planId.toByteArray().size} +
            data.iterationCommitments.sumOf {it.cycleId.toByteArray().size.toLong()+it.taskId.toByteArray().size} +
            data.iterationClosures.sumOf {it.cycleId.toByteArray().size.toLong()+it.taskId.toByteArray().size} +
            data.managementParticipants.sumOf {it.contextId.toByteArray().size.toLong()+it.personId.toByteArray().size} +
            data.participantRoles.sumOf {it.contextId.toByteArray().size.toLong()+it.personId.toByteArray().size+it.role.toByteArray().size} +
            data.objectiveResponsibilities.sumOf {it.contextId.toByteArray().size.toLong()+it.personId.toByteArray().size+it.objectiveId.toByteArray().size+it.role.toByteArray().size} +
            data.nodeResponsibilities.sumOf {it.contextId.toByteArray().size.toLong()+it.personId.toByteArray().size+it.nodeId.toByteArray().size+it.role.toByteArray().size} +
            data.backlogEntries.sumOf {it.nodeId.toByteArray().size.toLong()+it.contextId.toByteArray().size} +
            data.projectCharters.sumOf {listOf(it.contextId,it.purpose,it.scope,it.exclusions,it.deliverables,it.successConditions).sumOf {text->text.toByteArray().size.toLong()}} +
            data.managementObjectives.sumOf {listOf(it.id,it.contextId,it.title,it.description).sumOf {text->text.toByteArray().size.toLong()}} +
            data.acceptanceCriteria.sumOf {listOf(it.id,it.objectiveId,it.description).sumOf {text->text.toByteArray().size.toLong()}} +
            data.managementContexts.sumOf {it.id.toByteArray().size.toLong()+it.methodology.toByteArray().size} +
            data.sprintLinks.sumOf {it.taskId.toByteArray().size.toLong()+it.sprintId.toByteArray().size} +
            (data.gameSession?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) +
            (data.metroPreferences?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) + data.metroJourneys.sumOf { it.payload.toByteArray(Charsets.UTF_8).size.toLong() } + data.savedTemplates.sumOf { it.payload.toByteArray(Charsets.UTF_8).size.toLong() + it.name.toByteArray(Charsets.UTF_8).size + it.id.toByteArray(Charsets.UTF_8).size }
        require(textBytes <= BackupLimits.PAYLOAD_BYTES / 2) { "El contenido supera el límite de metadatos del backup." }
        fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
        val version=if(data.persons.isNotEmpty()) 39 else if(data.calendarEvents.any {it.color!="LIGHT_GREY"}) 38 else if(data.calendarEvents.isNotEmpty()) 37 else if(data.recurrenceRules.any {it.isDeleted}) 36 else if(data.personMemories.any {it.isPinned}) 35 else if(data.attachmentFiles.any {it.mimeType=="audio/aac"}) 34 else if(data.projects.any {it.isPinned} || data.nodes.any {it.isPinned}) 33 else if(data.personMemories.isNotEmpty() || data.personMemoryImages.isNotEmpty()) 32 else if(data.metroLearningEvents.isNotEmpty()) 31 else if(data.ganttAnnotations.isNotEmpty()) 30 else if(data.managementLayers.isNotEmpty() || data.sprintStories.isNotEmpty() || data.objectiveWork.isNotEmpty() || data.localIdentities.isNotEmpty()) 29 else if(data.personPhotos.isNotEmpty() || data.personBirthdays.isNotEmpty()) 28 else if(data.personTechnologies.isNotEmpty()) 27 else if(data.personProfiles.isNotEmpty() || data.personRelations.isNotEmpty()) 26 else if(data.iterationPlans.isNotEmpty() || data.iterationCycles.isNotEmpty() || data.iterationCommitments.isNotEmpty() || data.iterationClosures.isNotEmpty()) 25 else if(data.managementParticipants.isNotEmpty() || data.participantRoles.isNotEmpty() || data.objectiveResponsibilities.isNotEmpty() || data.nodeResponsibilities.isNotEmpty()) 24 else if(data.managementRoles.isNotEmpty() || data.objectiveEpics.isNotEmpty() || data.epicStories.isNotEmpty() || data.storyTasks.isNotEmpty() || data.backlogEntries.isNotEmpty()) 23 else if(data.projectCharters.isNotEmpty() || data.managementObjectives.isNotEmpty() || data.acceptanceCriteria.isNotEmpty()) 22 else if(data.managementContexts.isNotEmpty()) 21 else if(data.sprintLinks.isNotEmpty()) 20 else if(data.nodeSortPreferences.any {it.layersFirst || it.mode=="INHERIT"} || data.projectPhotos.isNotEmpty()) 19 else if(data.privatePreferences.isEmpty() && data.storeId==null && data.storeKind=="primary") 17 else 18
        val json = obj(
            "conversionRoots" to JSONArray(data.conversionRoots.map { obj("id" to it.id, "projectId" to it.projectId, "nodeId" to it.nodeId, "projectIdentity" to it.projectIdentity, "nodeIdentity" to it.nodeIdentity, "projectPosition" to it.projectPosition, "nodePosition" to it.nodePosition, "startAt" to it.startAt, "dueAt" to it.dueAt, "priority" to it.priority, "creationGroupId" to it.creationGroupId, "sprintMode" to it.sprintMode) }),
            "conversionPeople" to JSONArray(data.conversionPeople.map { obj("rootId" to it.rootId, "personId" to it.personId) }),
            "conversionTags" to JSONArray(data.conversionTags.map { obj("rootId" to it.rootId, "tagId" to it.tagId) }),
            "conversionEvents" to JSONArray(data.conversionEvents.map { obj("id" to it.id, "rootId" to it.rootId, "type" to it.type, "occurredAt" to it.occurredAt) }),
            "conversionWorkStates" to JSONArray(data.conversionWorkStates.map { obj("nodeId" to it.nodeId, "rootId" to it.rootId, "workState" to it.workState) }),
            "nodeSortPreferences" to JSONArray(data.nodeSortPreferences.map { obj("context" to it.context, "mode" to it.mode).apply {if(version>=19) put("layersFirst",it.layersFirst)} }),
            "imageFiles" to JSONArray(data.imageFiles.map { obj("directory" to it.directory, "name" to it.name, "byteSize" to it.byteSize, "sha256" to it.sha256) }),
            "metroPreferences" to data.metroPreferences,
            "metroJourneys" to JSONArray(data.metroJourneys.map { obj("id" to it.id, "nodeId" to it.nodeId, "personId" to it.personId, "enabled" to it.enabled, "payload" to it.payload, "revision" to it.revision) }),
            "dataVersion" to version, "savedTemplates" to JSONArray(data.savedTemplates.map { obj("id" to it.id, "name" to it.name, "payload" to it.payload) }), "gameSession" to data.gameSession, "appVersion" to data.appVersion, "createdAt" to data.createdAt,
            "projects" to JSONArray(data.projects.map { obj("id" to it.id, "name" to it.name, "description" to it.description, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,*(if(version>=33) arrayOf("isPinned" to it.isPinned) else emptyArray())) }),
            "nodes" to JSONArray(data.nodes.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "isCompleted" to it.isCompleted, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt, "startAt" to it.startAt, "dueAt" to it.dueAt, "purpose" to it.purpose, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "priority" to it.priority, "creationGroupId" to it.creationGroupId, "sprintMode" to it.sprintMode, "workState" to it.workState,*(if(version>=33) arrayOf("isPinned" to it.isPinned) else emptyArray())) }),
            "persons" to JSONArray(data.persons.map { obj("id" to it.id, "name" to it.name, "avatarFile" to it.avatarFile, "avatarZoom" to it.avatarZoom, "avatarX" to it.avatarX, "avatarY" to it.avatarY) }),
            "assignments" to JSONArray(data.assignments.map { obj("nodeId" to it.nodeId, "personId" to it.personId) }),
            "recurrenceRules" to JSONArray(data.recurrenceRules.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "startDay" to it.startDay, "frequency" to it.frequency, "interval" to it.interval, "endDay" to it.endDay, "nextIndex" to it.nextIndex, "status" to it.status, "zoneId" to it.zoneId, "dueMinute" to it.dueMinute, "startOffsetMillis" to it.startOffsetMillis, "priority" to it.priority, *(if(version>=36) arrayOf("isDeleted" to it.isDeleted) else emptyArray())) }),
            "recurrenceOccurrences" to JSONArray(data.recurrenceOccurrences.map { obj("ruleId" to it.ruleId, "day" to it.day, "nodeId" to it.nodeId) }),
            "recurrenceAssignments" to JSONArray(data.recurrenceAssignments.map { obj("ruleId" to it.ruleId, "personId" to it.personId) }),
            "tags" to JSONArray(data.tags.map { obj("id" to it.id, "name" to it.name, "normalizedName" to it.normalizedName) }),
            "nodeTags" to JSONArray(data.nodeTags.map { obj("nodeId" to it.nodeId, "tagId" to it.tagId) }),
            "recurrenceTags" to JSONArray(data.recurrenceTags.map { obj("ruleId" to it.ruleId, "tagId" to it.tagId) }),
            "nodeEvents" to JSONArray(data.nodeEvents.map { obj("id" to it.id, "nodeId" to it.nodeId, "type" to it.type, "occurredAt" to it.occurredAt) }),
            "creationDefaults" to JSONArray(data.creationDefaults.map { obj("id" to it.id,"projectId" to it.projectId,"nodeId" to it.nodeId,
                "purpose" to it.purpose,"obligation" to it.obligation,"currency" to it.currency,"priority" to it.priority,
                "tagsOverride" to it.tagsOverride,"peopleOverride" to it.peopleOverride,"startRule" to it.startRule,"startNumber" to it.startNumber,
                "startMinute" to it.startMinute,"dueRule" to it.dueRule,"dueNumber" to it.dueNumber,"dueMinute" to it.dueMinute) }),
            "defaultsTags" to JSONArray(data.defaultsTags.map { obj("defaultsId" to it.defaultsId,"tagId" to it.tagId) }),
            "defaultsPeople" to JSONArray(data.defaultsPeople.map { obj("defaultsId" to it.defaultsId,"personId" to it.personId) }),
            "attachmentFiles" to JSONArray(data.attachmentFiles.map { obj("id" to it.id, "storageName" to it.storageName, "originalName" to it.originalName, "mimeType" to it.mimeType, "byteSize" to it.byteSize, "width" to it.width, "height" to it.height, "sha256" to it.sha256, "createdAt" to it.createdAt, "lifecycleState" to it.lifecycleState,*(if(version>=34) arrayOf("durationMs" to it.durationMs,"playbackMs" to it.playbackMs,"bookmarkMs" to it.bookmarkMs) else emptyArray())) }),
            "nodeAttachments" to JSONArray(data.nodeAttachments.map { obj("nodeId" to it.nodeId, "attachmentId" to it.attachmentId) }),
            "projectAttachments" to JSONArray(data.projectAttachments.map { obj("projectId" to it.projectId, "attachmentId" to it.attachmentId) }),
            "technologies" to JSONArray(data.technologies.map { obj("id" to it.id, "name" to it.name, "iconFile" to it.iconFile) }),
            "nodeTechnologies" to JSONArray(data.nodeTechnologies.map { obj("nodeId" to it.nodeId, "technologyId" to it.technologyId, "position" to it.position) }),
            "projectTechnologies" to JSONArray(data.projectTechnologies.map { obj("projectId" to it.projectId, "technologyId" to it.technologyId, "position" to it.position) }),
            "technologyIcons" to JSONArray(data.technologyIcons.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
            "projectPhotos" to JSONArray(data.projectPhotos.map { obj("id" to it.id, "projectId" to it.projectId, "nodeId" to it.nodeId, "file" to it.file, "photoZoom" to it.photoZoom, "photoX" to it.photoX, "photoY" to it.photoY) }),
            "projectPhotoImages" to JSONArray(data.projectPhotoImages.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
            "avatars" to JSONArray(data.avatars.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
        )
        if(version>=29) {
            json.put("sprintStories",JSONArray(data.sprintStories.map {obj("sprintId" to it.sprintId,"storyId" to it.storyId)}))
            json.put("objectiveWork",JSONArray(data.objectiveWork.map {obj("objectiveId" to it.objectiveId,"nodeId" to it.nodeId)}))
            json.put("localIdentities",JSONArray(data.localIdentities.map {obj("slot" to it.slot,"personId" to it.personId)}))
        }
        if(version>=31) json.put("metroLearningEvents",JSONArray(data.metroLearningEvents.map {obj("id" to it.id,"sessionId" to it.sessionId,"sequence" to it.sequence,"payload" to it.payload,"invalidReason" to it.invalidReason)}))
        if(version>=30) json.put("ganttAnnotations",JSONArray(data.ganttAnnotations.map {obj("id" to it.id,"contextId" to it.contextId,"text" to it.text,"epochDay" to it.epochDay,"lane" to it.lane)}))
        if(version>=29) json.put("managementLayers",JSONArray(data.managementLayers.map {obj("nodeId" to it.nodeId,"contextId" to it.contextId,"kind" to it.kind)}))
        if(version>=28) {
            json.put("personPhotos",JSONArray(data.personPhotos.map {obj("id" to it.id,"personId" to it.personId,"file" to it.file,"position" to it.position,"photoZoom" to it.zoom,"photoX" to it.x,"photoY" to it.y)}))
            json.put("personBirthdays",JSONArray(data.personBirthdays.map {obj("personId" to it.personId,"date" to it.date)}))
        }
        if(version>=27) json.put("personTechnologies",JSONArray(data.personTechnologies.map {obj("personId" to it.personId,"technologyId" to it.technologyId,"proficiency" to it.proficiency)}))
        if(version>=26) {
            json.put("personProfiles",JSONArray(data.personProfiles.map {obj("personId" to it.personId,"alias" to it.alias,"description" to it.description,"notes" to it.notes,"phones" to it.phones,"emails" to it.emails,"addresses" to it.addresses,"links" to it.links,"qualities" to it.qualities,"skills" to it.skills,"interests" to it.interests,"availability" to it.availability).apply {if(version>=39) {put("givenName",it.givenName);put("familyName",it.familyName);put("documentId",it.documentId)}}}))
            json.put("personRelations",JSONArray(data.personRelations.map {obj("id" to it.id,"sourceId" to it.sourceId,"targetId" to it.targetId,"category" to it.category,"label" to it.label,"inverseLabel" to it.inverseLabel,"meaning" to it.meaning,"mode" to it.mode,"relationKey" to it.relationKey)}))
        }
        if(version>=25) {
            json.put("iterationPlans",JSONArray(data.iterationPlans.map {obj("id" to it.id,"contextId" to it.contextId,"sprintId" to it.sprintId,"goal" to it.goal,"startAt" to it.startAt,"endAt" to it.endAt,"notes" to it.notes,"state" to it.state)}))
            json.put("iterationCycles",JSONArray(data.iterationCycles.map {obj("id" to it.id,"planId" to it.planId,"number" to it.number,"startedAt" to it.startedAt,"closedAt" to it.closedAt)}))
            json.put("iterationCommitments",JSONArray(data.iterationCommitments.map {obj("cycleId" to it.cycleId,"taskId" to it.taskId,"initial" to it.initial,"addedAt" to it.addedAt)}))
            json.put("iterationClosures",JSONArray(data.iterationClosures.map {obj("cycleId" to it.cycleId,"taskId" to it.taskId,"inScope" to it.inScope,"phaseAtClose" to it.phaseAtClose,"completedAtClose" to it.completedAtClose)}))
        }
        if(version>=24) {
            json.put("managementParticipants",JSONArray(data.managementParticipants.map {obj("contextId" to it.contextId,"personId" to it.personId)}))
            json.put("participantRoles",JSONArray(data.participantRoles.map {obj("contextId" to it.contextId,"personId" to it.personId,"role" to it.role)}))
            json.put("objectiveResponsibilities",JSONArray(data.objectiveResponsibilities.map {obj("contextId" to it.contextId,"personId" to it.personId,"objectiveId" to it.objectiveId,"role" to it.role)}))
            json.put("nodeResponsibilities",JSONArray(data.nodeResponsibilities.map {obj("contextId" to it.contextId,"personId" to it.personId,"nodeId" to it.nodeId,"role" to it.role)}))
        }
        if(version>=23) {
            json.put("managementRoles",JSONArray(data.managementRoles.map {obj("nodeId" to it.nodeId,"contextId" to it.contextId,"role" to it.role)}))
            json.put("objectiveEpics",JSONArray(data.objectiveEpics.map {obj("objectiveId" to it.objectiveId,"epicId" to it.epicId)}))
            json.put("epicStories",JSONArray(data.epicStories.map {obj("epicId" to it.epicId,"storyId" to it.storyId)}))
            json.put("storyTasks",JSONArray(data.storyTasks.map {obj("storyId" to it.storyId,"taskId" to it.taskId)}))
            json.put("backlogEntries",JSONArray(data.backlogEntries.map {obj("nodeId" to it.nodeId,"contextId" to it.contextId,"position" to it.position)}))
        }
        if(version>=22) {
            json.put("projectCharters",JSONArray(data.projectCharters.map {obj("contextId" to it.contextId,"purpose" to it.purpose,"scope" to it.scope,"exclusions" to it.exclusions,"deliverables" to it.deliverables,"successConditions" to it.successConditions)}))
            json.put("managementObjectives",JSONArray(data.managementObjectives.map {obj("id" to it.id,"contextId" to it.contextId,"title" to it.title,"description" to it.description,"position" to it.position)}))
            json.put("acceptanceCriteria",JSONArray(data.acceptanceCriteria.map {obj("id" to it.id,"objectiveId" to it.objectiveId,"description" to it.description,"position" to it.position,"validated" to it.validated,"validatedAt" to it.validatedAt)}))
        }
        if(version>=21) json.put("managementContexts",JSONArray(data.managementContexts.map {obj("id" to it.id,"enabled" to it.enabled,"methodology" to it.methodology)}))
        if(version>=20) json.put("sprintLinks",JSONArray(data.sprintLinks.map {obj("taskId" to it.taskId,"sprintId" to it.sprintId)}))
        if(version>=18) {
            json.put("privatePreferences", JSONArray(data.privatePreferences.map { obj("name" to it.name,"payload" to it.payload) }))
            json.put("storeId",data.storeId ?: JSONObject.NULL);json.put("storeKind",data.storeKind)
        }
        if(version>=32) {
            json.put("personMemories",JSONArray(data.personMemories.map {obj("id" to it.id,"personId" to it.personId,"date" to it.date,"description" to it.description,"position" to it.position,*(if(version>=35) arrayOf("isPinned" to it.isPinned) else emptyArray()))}))
            json.put("personMemoryImages",JSONArray(data.personMemoryImages.map {obj("id" to it.id,"memoryId" to it.memoryId,"attachmentId" to it.attachmentId,"legacyPhotoId" to it.legacyPhotoId,"position" to it.position,"date" to it.date,"description" to it.description)}))
        }
        if(version>=37) {
            json.put("calendarEvents",JSONArray(data.calendarEvents.map {obj("id" to it.id,"title" to it.title,"date" to it.date,"minute" to it.minute,"description" to it.description,*(if(version>=38) arrayOf("color" to it.color) else emptyArray()))}))
            json.put("calendarEventPeople",JSONArray(data.calendarEventPeople.map {obj("eventId" to it.eventId,"personId" to it.personId)}))
        }
        return json.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupLimits.PAYLOAD_BYTES) { "El backup supera el límite de 16 MiB de metadatos." }
        }
    }

    fun decode(bytes: ByteArray): BackupData {
        require(bytes.size <= BackupLimits.PAYLOAD_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = JsonReader(StringReader(text)).use { reader ->
            val value = readValue(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Contenido adicional." }
            value as? JSONObject ?: error("Backup no es un objeto.")
        }
        val version = root.integer("dataVersion")
        require(version in 1L..39L) { "Versión de datos no compatible." }
        val baseFields = arrayOf("dataVersion", "appVersion", "createdAt", "projects", "nodes", "persons", "assignments", "avatars")
        root.fields(*(baseFields + (if(version>=37L) arrayOf("calendarEvents","calendarEventPeople") else emptyArray()) + (if(version>=32L) arrayOf("personMemories","personMemoryImages") else emptyArray()) + (if(version>=31L) arrayOf("metroLearningEvents") else emptyArray()) + (if(version>=30L) arrayOf("ganttAnnotations") else emptyArray()) + (if(version>=29L) arrayOf("managementLayers","sprintStories","objectiveWork","localIdentities") else emptyArray()) + (if(version>=28L) arrayOf("personPhotos","personBirthdays") else emptyArray()) + (if(version>=27L) arrayOf("personTechnologies") else emptyArray()) + (if(version>=26L) arrayOf("personProfiles","personRelations") else emptyArray()) + (if (version >= 2L) arrayOf("recurrenceRules", "recurrenceOccurrences", "recurrenceAssignments") else emptyArray()) + (if (version >= 3L) arrayOf("tags", "nodeTags", "recurrenceTags") else emptyArray()) + (if (version >= 4L) arrayOf("nodeEvents") else emptyArray()) + (if (version >= 7L) arrayOf("creationDefaults","defaultsTags","defaultsPeople") else emptyArray()) + (if (version >= 10L) arrayOf("attachmentFiles", "nodeAttachments", "projectAttachments") else emptyArray()) + (if (version >= 11L) arrayOf("technologies", "nodeTechnologies", "projectTechnologies", "technologyIcons") else emptyArray()) + (if (version >= 13L) arrayOf("projectPhotos", "projectPhotoImages") else emptyArray()) + (if (version >= 14L) arrayOf("conversionRoots", "conversionPeople", "conversionTags", "conversionEvents", "conversionWorkStates", "nodeSortPreferences", "imageFiles") else emptyArray()) + (if (version >= 15L) arrayOf("gameSession") else emptyArray()) + (if (version >= 16L) arrayOf("metroPreferences", "metroJourneys") else emptyArray()) + (if (version >= 17L) arrayOf("savedTemplates") else emptyArray()) + (if (version >= 18L) arrayOf("privatePreferences","storeId","storeKind") else emptyArray()) + (if(version>=20L) arrayOf("sprintLinks") else emptyArray()) + (if(version>=21L) arrayOf("managementContexts") else emptyArray()) + (if(version>=22L) arrayOf("projectCharters","managementObjectives","acceptanceCriteria") else emptyArray()) + (if(version>=25L) arrayOf("iterationPlans","iterationCycles","iterationCommitments","iterationClosures") else emptyArray()) + (if(version>=24L) arrayOf("managementParticipants","participantRoles","objectiveResponsibilities","nodeResponsibilities") else emptyArray()) + (if(version>=23L) arrayOf("managementRoles","objectiveEpics","epicStories","storyTasks","backlogEntries") else emptyArray())))
        val projects = root.records("projects").map { row ->
            row.fields("id", "name", "description", "position", "createdAt", "updatedAt",*(if(version>=33) arrayOf("isPinned") else emptyArray()))
            ProjectEntity(row.string("id"), row.string("name"), row.string("description"), row.position(), row.integer("createdAt"), row.integer("updatedAt"),isPinned=if(version>=33) row.boolean("isPinned") else false)
        }
        val rawNodes = root.records("nodes").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "isCompleted", "position", "createdAt", "updatedAt", "startAt", "dueAt", "purpose", "amountMinor", "currencyCode", *(if (version >= 5) arrayOf("priority") else emptyArray()), *(if (version >= 6) arrayOf("creationGroupId") else emptyArray()), *(if (version >= 9) arrayOf("sprintMode","workState") else emptyArray()),*(if(version>=33) arrayOf("isPinned") else emptyArray()))
            NodeEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.get("isCompleted") as? Boolean ?: error("Completado inválido."), row.position(), row.integer("createdAt"), row.integer("updatedAt"), row.nullableLong("startAt"), row.nullableLong("dueAt"), row.string("purpose"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"), if (version >= 5) row.string("priority") else "NONE", if (version >= 6) row.nullableString("creationGroupId") else null, if (version >= 9) row.boolean("sprintMode") else false, if (version >= 9) row.nullableString("workState") else null,isPinned=if(version>=33) row.boolean("isPinned") else false)
        }
        val legacyParents=rawNodes.mapNotNullTo(hashSetOf()) { it.parentId }
        if(version<8) require(rawNodes.all { it.purpose in listOf("ACTION","NOTE") }) { "Propósito desconocido en backup antiguo." }
        val nodes=if(version<8) rawNodes.map { if(it.id in legacyParents) {
            require(it.purpose=="ACTION" && !it.isCompleted && it.amountMinor==null) { "Capa antigua inválida." }
            it.copy(purpose="LAYER")
        } else it } else rawNodes
        var persons = root.records("persons").map { row ->
            row.fields(*(arrayOf("id", "name", "avatarFile") + if (version >= 12) arrayOf("avatarZoom", "avatarX", "avatarY") else emptyArray()))
            PersonEntity(row.string("id"), row.string("name"), row.nullableString("avatarFile"),
                if (version >= 12) row.finiteFloat("avatarZoom") else 1f,
                if (version >= 12) row.finiteFloat("avatarX") else 0f,
                if (version >= 12) row.finiteFloat("avatarY") else 0f)
        }
        val assignments = root.records("assignments").map { row ->
            row.fields("nodeId", "personId")
            NodePersonEntity(row.string("nodeId"), row.string("personId"))
        }
        val rules = if (version == 1L) emptyList() else root.records("recurrenceRules").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "amountMinor", "currencyCode", "startDay", "frequency", "interval", "endDay", "nextIndex", "status", "zoneId", "dueMinute", "startOffsetMillis", *(if (version >= 5) arrayOf("priority") else emptyArray()), *(if(version>=36) arrayOf("isDeleted") else emptyArray()))
            RecurrenceRuleEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"), row.integer("startDay"), row.string("frequency"), row.intValue("interval"), row.nullableLong("endDay"), row.integer("nextIndex"), row.string("status"), row.string("zoneId"), row.intValue("dueMinute"), row.nullableLong("startOffsetMillis"), if (version >= 5) row.string("priority") else "NONE", if(version>=36) row.boolean("isDeleted") else false)
        }
        val occurrences = if (version == 1L) emptyList() else root.records("recurrenceOccurrences").map { row ->
            row.fields("ruleId", "day", "nodeId")
            RecurrenceOccurrenceEntity(row.string("ruleId"), row.integer("day"), row.string("nodeId"))
        }
        val recurrenceAssignments = if (version == 1L) emptyList() else root.records("recurrenceAssignments").map { row ->
            row.fields("ruleId", "personId")
            RecurrencePersonEntity(row.string("ruleId"), row.string("personId"))
        }
        val tags = if (version < 3L) emptyList() else root.records("tags").map {
            it.fields("id", "name", "normalizedName")
            TagEntity(it.string("id"), it.string("name"), it.string("normalizedName"))
        }
        val nodeTags = if (version < 3L) emptyList() else root.records("nodeTags").map {
            it.fields("nodeId", "tagId"); NodeTagEntity(it.string("nodeId"), it.string("tagId"))
        }
        val recurrenceTags = if (version < 3L) emptyList() else root.records("recurrenceTags").map {
            it.fields("ruleId", "tagId"); RecurrenceTagEntity(it.string("ruleId"), it.string("tagId"))
        }
        val nodeEvents = if (version < 4L) emptyList() else root.records("nodeEvents").map {
            it.fields("id", "nodeId", "type", "occurredAt")
            NodeEventEntity(it.string("id"), it.string("nodeId"), it.string("type"), it.integer("occurredAt"))
        }
        val defaults=if(version<7) emptyList() else root.records("creationDefaults").map { row ->
            row.fields("id","projectId","nodeId","purpose","obligation","currency","priority","tagsOverride","peopleOverride","startRule","startNumber","startMinute","dueRule","dueNumber","dueMinute")
            CreationDefaultsEntity(row.string("id"),row.nullableString("projectId"),row.nullableString("nodeId"),row.nullableString("purpose"),
                if(row.get("obligation")==JSONObject.NULL) null else row.boolean("obligation"),row.nullableString("currency"),row.nullableString("priority"),
                row.boolean("tagsOverride"),row.boolean("peopleOverride"),row.nullableString("startRule"),row.nullableInt("startNumber"),row.nullableInt("startMinute"),
                row.nullableString("dueRule"),row.nullableInt("dueNumber"),row.nullableInt("dueMinute"))
        }
        val defaultsTags=if(version<7) emptyList() else root.records("defaultsTags").map { it.fields("defaultsId","tagId");CreationDefaultsTagEntity(it.string("defaultsId"),it.string("tagId")) }
        val defaultsPeople=if(version<7) emptyList() else root.records("defaultsPeople").map { it.fields("defaultsId","personId");CreationDefaultsPersonEntity(it.string("defaultsId"),it.string("personId")) }
        val attachmentFiles = if (version < 10) emptyList() else root.records("attachmentFiles").map {
            it.fields("id", "storageName", "originalName", "mimeType", "byteSize", "width", "height", "sha256", "createdAt", "lifecycleState",*(if(version>=34) arrayOf("durationMs","playbackMs","bookmarkMs") else emptyArray()))
            AttachmentFileEntity(it.string("id"), it.string("storageName"), it.string("originalName"), it.string("mimeType"), it.integer("byteSize"), it.intValue("width"), it.intValue("height"), it.string("sha256"), it.integer("createdAt"), it.string("lifecycleState"),if(version>=34) it.integer("durationMs") else 0,if(version>=34) it.integer("playbackMs") else 0,if(version>=34) it.nullableLong("bookmarkMs") else null)
        }
        val nodeAttachments = if (version < 10) emptyList() else root.records("nodeAttachments").map { it.fields("nodeId", "attachmentId"); NodeAttachmentEntity(it.string("nodeId"), it.string("attachmentId")) }
        val projectAttachments = if (version < 10) emptyList() else root.records("projectAttachments").map { it.fields("projectId", "attachmentId"); ProjectAttachmentEntity(it.string("projectId"), it.string("attachmentId")) }
        val technologies = if (version < 11) emptyList() else root.records("technologies").map {
            it.fields("id", "name", "iconFile"); TechnologyEntity(it.string("id"), it.string("name"), it.nullableString("iconFile"))
        }
        val nodeTechnologiesRaw = if (version < 11) emptyList() else root.records("nodeTechnologies").map {
            it.fields(*(arrayOf("nodeId", "technologyId") + if(version >= 17) arrayOf("position") else emptyArray())); NodeTechnologyEntity(it.string("nodeId"), it.string("technologyId"), if(version>=17) it.integer("position").also { n->require(n in 0..Int.MAX_VALUE.toLong()) }.toInt() else 0)
        }
        val projectTechnologiesRaw = if (version < 11) emptyList() else root.records("projectTechnologies").map {
            it.fields(*(arrayOf("projectId", "technologyId") + if(version >= 17) arrayOf("position") else emptyArray())); ProjectTechnologyEntity(it.string("projectId"), it.string("technologyId"), if(version>=17) it.integer("position").also { n->require(n in 0..Int.MAX_VALUE.toLong()) }.toInt() else 0)
        }
        val names=technologies.associate { it.id to it.name }
        val nodeTechnologies=if(version>=17) nodeTechnologiesRaw else nodeTechnologiesRaw.groupBy { it.nodeId }.values.flatMap { rows->rows.sortedWith(compareBy({ names[it.technologyId]?.map { c->if(c in 'A'..'Z') c.lowercaseChar() else c }?.joinToString("") },{ it.technologyId })).mapIndexed { index,row->row.copy(position=index) } }
        val projectTechnologies=if(version>=17) projectTechnologiesRaw else projectTechnologiesRaw.groupBy { it.projectId }.values.flatMap { rows->rows.sortedWith(compareBy({ names[it.technologyId]?.map { c->if(c in 'A'..'Z') c.lowercaseChar() else c }?.joinToString("") },{ it.technologyId })).mapIndexed { index,row->row.copy(position=index) } }
        var imageBytes = 0L
        fun pngs(key: String): Map<String, ByteArray> {
            val images = linkedMapOf<String, ByteArray>()
            root.records(key).forEach { row ->
                row.fields("name", "png")
                val name = row.string("name")
                require(name !in images && BackupLimits.avatarName.matches(name)) { "Nombre de imagen inválido o repetido." }
                val png = row.string("png")
                require(png.length <= ((if(key=="avatars") BackupLimits.PERSON_AVATAR_BYTES else if(key=="projectPhotoImages" && version>=19) BackupLimits.PROJECT_PHOTO_BYTES else BackupLimits.AVATAR_BYTES) + 2) / 3 * 4) { "Imagen demasiado grande." }
                val decoded = Base64.decode(png, Base64.NO_WRAP)
                require(Base64.encodeToString(decoded, Base64.NO_WRAP) == png) { "Codificación de imagen inválida." }
                imageBytes += decoded.size
                require(imageBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares e iconos demasiado grandes." }
                images[name] = decoded
            }
            return images
        }
        val projectPhotos = if (version < 13) emptyList() else root.records("projectPhotos").map { row ->
            row.fields("id", "projectId", "nodeId", "file", "photoZoom", "photoX", "photoY")
            ProjectPhotoEntity(row.string("id"), row.nullableString("projectId"), row.nullableString("nodeId"), row.string("file"),
                row.finiteFloat("photoZoom"), row.finiteFloat("photoX"), row.finiteFloat("photoY"))
        }
        val avatars = pngs("avatars")
        val technologyIcons = if (version < 11) emptyMap() else pngs("technologyIcons")
        val projectPhotoImages = if (version < 13) emptyMap() else pngs("projectPhotoImages")
        val conversionRoots = if (version < 14) emptyList() else root.records("conversionRoots").map { row ->
            row.fields("id", "projectId", "nodeId", "projectIdentity", "nodeIdentity", "projectPosition", "nodePosition", "startAt", "dueAt", "priority", "creationGroupId", "sprintMode")
            ConversionRootEntity(row.string("id"), row.nullableString("projectId"), row.nullableString("nodeId"), row.string("projectIdentity"), row.string("nodeIdentity"), row.integer("projectPosition").also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt(), row.integer("nodePosition").also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt(), row.nullableLong("startAt"), row.nullableLong("dueAt"), row.string("priority"), row.nullableString("creationGroupId"), row.boolean("sprintMode"))
        }
        val conversionPeople = if (version < 14) emptyList() else root.records("conversionPeople").map { row ->
            row.fields("rootId", "personId")
            ConversionPersonEntity(row.string("rootId"), row.string("personId"))
        }
        val conversionTags = if (version < 14) emptyList() else root.records("conversionTags").map { row ->
            row.fields("rootId", "tagId")
            ConversionTagEntity(row.string("rootId"), row.string("tagId"))
        }
        val conversionEvents = if (version < 14) emptyList() else root.records("conversionEvents").map { row ->
            row.fields("id", "rootId", "type", "occurredAt")
            ConversionEventEntity(row.string("id"), row.string("rootId"), row.string("type"), row.integer("occurredAt"))
        }
        val conversionWorkStates = if (version < 14) emptyList() else root.records("conversionWorkStates").map { row ->
            row.fields("nodeId", "rootId", "workState")
            ConversionWorkStateEntity(row.string("nodeId"), row.string("rootId"), row.string("workState"))
        }
        val nodeSortPreferences = if (version < 14) emptyList() else root.records("nodeSortPreferences").map { row ->
            row.fields(*(arrayOf("context","mode")+if(version>=19) arrayOf("layersFirst") else emptyArray()))
            NodeSortPreferenceEntity(row.string("context"), row.string("mode").also {require(version>=19 || it!="INHERIT")}, if(version>=19) row.boolean("layersFirst") else false)
        }
        val imageFiles = if (version < 14) emptyList() else root.records("imageFiles").map { row ->
            row.fields("directory", "name", "byteSize", "sha256")
            BackupImageFile(row.string("directory"), row.string("name"), row.integer("byteSize"), row.string("sha256"))
        }
        val managementRoles=if(version<23) emptyList() else root.records("managementRoles").map {row->
            row.fields("nodeId","contextId","role");ManagementRoleEntity(row.string("nodeId"),row.string("contextId"),row.string("role"))
        }
        val objectiveEpics=if(version<23) emptyList() else root.records("objectiveEpics").map {row->
            row.fields("objectiveId","epicId");ObjectiveEpicEntity(row.string("objectiveId"),row.string("epicId"))
        }
        val epicStories=if(version<23) emptyList() else root.records("epicStories").map {row->
            row.fields("epicId","storyId");EpicStoryEntity(row.string("epicId"),row.string("storyId"))
        }
        val storyTasks=if(version<23) emptyList() else root.records("storyTasks").map {row->
            row.fields("storyId","taskId");StoryTaskEntity(row.string("storyId"),row.string("taskId"))
        }
        val backlogEntries=if(version<23) emptyList() else root.records("backlogEntries").map {row->
            row.fields("nodeId","contextId","position");BacklogEntryEntity(row.string("nodeId"),row.string("contextId"),row.intValue("position"))
        }
        val managementParticipants=if(version<24) emptyList() else root.records("managementParticipants").map {row->row.fields("contextId","personId");ManagementParticipantEntity(row.string("contextId"),row.string("personId"))}
        val participantRoles=if(version<24) emptyList() else root.records("participantRoles").map {row->row.fields("contextId","personId","role");ParticipantRoleEntity(row.string("contextId"),row.string("personId"),row.string("role"))}
        val objectiveResponsibilities=if(version<24) emptyList() else root.records("objectiveResponsibilities").map {row->row.fields("contextId","personId","objectiveId","role");ObjectiveResponsibilityEntity(row.string("contextId"),row.string("personId"),row.string("objectiveId"),row.string("role"))}
        val nodeResponsibilities=if(version<24) emptyList() else root.records("nodeResponsibilities").map {row->row.fields("contextId","personId","nodeId","role");NodeResponsibilityEntity(row.string("contextId"),row.string("personId"),row.string("nodeId"),row.string("role"))}
        val iterationPlans=if(version<25) emptyList() else root.records("iterationPlans").map {row->row.fields("id","contextId","sprintId","goal","startAt","endAt","notes","state");IterationPlanEntity(row.string("id"),row.string("contextId"),row.string("sprintId"),row.string("goal"),row.nullableLong("startAt"),row.nullableLong("endAt"),row.string("notes"),row.string("state"))}
        val iterationCycles=if(version<25) emptyList() else root.records("iterationCycles").map {row->row.fields("id","planId","number","startedAt","closedAt");IterationCycleEntity(row.string("id"),row.string("planId"),row.intValue("number"),row.integer("startedAt"),row.nullableLong("closedAt"))}
        val iterationCommitments=if(version<25) emptyList() else root.records("iterationCommitments").map {row->row.fields("cycleId","taskId","initial","addedAt");IterationCommitmentEntity(row.string("cycleId"),row.string("taskId"),row.boolean("initial"),row.integer("addedAt"))}
        val iterationClosures=if(version<25) emptyList() else root.records("iterationClosures").map {row->row.fields("cycleId","taskId","inScope","phaseAtClose","completedAtClose");IterationClosureEntity(row.string("cycleId"),row.string("taskId"),row.boolean("inScope"),row.nullableString("phaseAtClose"),row.boolean("completedAtClose"))}
        val personPhotos=if(version<28) emptyList() else root.records("personPhotos").map {r->r.fields("id","personId","file","position","photoZoom","photoX","photoY");PersonPhotoEntity(r.string("id"),r.string("personId"),r.string("file"),r.intValue("position"),r.finiteFloat("photoZoom"),r.finiteFloat("photoX"),r.finiteFloat("photoY"))}
        val personBirthdays=if(version<28) emptyList() else root.records("personBirthdays").map {r->r.fields("personId","date");PersonBirthdayEntity(r.string("personId"),r.string("date"))}
        val personTechnologies=if(version<27) emptyList() else root.records("personTechnologies").map {r->r.fields("personId","technologyId","proficiency");PersonTechnologyEntity(r.string("personId"),r.string("technologyId"),r.string("proficiency"))}
        val rawProfiles=if(version<26) emptyList() else root.records("personProfiles").map {r->
            r.fields("personId","alias","description","notes","phones","emails","addresses","links","qualities","skills","interests","availability",*(if(version>=39) arrayOf("givenName","familyName","documentId") else emptyArray()))
            PersonProfileEntity(r.string("personId"),r.string("alias"),r.string("description"),r.string("notes"),r.string("phones"),r.string("emails"),r.string("addresses"),r.string("links"),r.string("qualities"),r.string("skills"),r.string("interests"),r.string("availability"),
                if(version>=39) r.string("givenName") else "",if(version>=39) r.string("familyName") else "",if(version>=39) r.string("documentId") else "")
        }
        // Preserve all historical records before translating the public identity to nickname.
        val personProfiles=if(version>=39) rawProfiles else {
            require(persons.all {it.name.isNotBlank()}) {"Nombre histórico vacío."}
            com.r0ybt.arachn0de.domain.model.validatePersonDetails(persons.mapTo(hashSetOf()) {it.id},rawProfiles,emptyList())
            val byPerson=rawProfiles.associateBy {it.personId}
            persons.map {person->(byPerson[person.id] ?: PersonProfileEntity(person.id)).copy(givenName=person.name)}
        }
        if(version<39) {
            val byPerson=rawProfiles.associateBy {it.personId}
            persons=persons.map {person->person.copy(name=byPerson[person.id]?.alias?.trim()?.takeIf {it.isNotEmpty()} ?: person.name)}
        }
        val personRelations=if(version<26) emptyList() else root.records("personRelations").map {r->r.fields("id","sourceId","targetId","category","label","inverseLabel","meaning","mode","relationKey");PersonRelationEntity(r.string("id"),r.string("sourceId"),r.string("targetId"),r.string("category"),r.string("label"),r.string("inverseLabel"),r.string("meaning"),r.string("mode"),r.string("relationKey"))}
        val memories=if(version<32) personPhotos.map {PersonMemoryEntity(it.id,it.personId,null,"",it.position)} else root.records("personMemories").map {r->r.fields("id","personId","date","description","position",*(if(version>=35) arrayOf("isPinned") else emptyArray()));PersonMemoryEntity(r.string("id"),r.string("personId"),r.nullableString("date"),r.string("description"),r.intValue("position"),if(version>=35) r.boolean("isPinned") else false)}
        val memoryImages=if(version<32) personPhotos.map {PersonMemoryImageEntity(it.id,it.id,null,it.id,0)} else root.records("personMemoryImages").map {r->r.fields("id","memoryId","attachmentId","legacyPhotoId","position","date","description");PersonMemoryImageEntity(r.string("id"),r.string("memoryId"),r.nullableString("attachmentId"),r.nullableString("legacyPhotoId"),r.intValue("position"),r.nullableString("date"),r.string("description"))}
        return BackupData(root.string("appVersion"), root.integer("createdAt"), projects, nodes, persons, assignments, avatars, rules, occurrences, recurrenceAssignments, tags, nodeTags, recurrenceTags, nodeEvents, defaults, defaultsTags, defaultsPeople, attachmentFiles, nodeAttachments, projectAttachments, technologies = technologies, nodeTechnologies = nodeTechnologies, projectTechnologies = projectTechnologies, technologyIcons = technologyIcons, projectPhotos = projectPhotos, projectPhotoImages = projectPhotoImages, conversionRoots = conversionRoots, conversionPeople = conversionPeople, conversionTags = conversionTags, conversionEvents = conversionEvents, conversionWorkStates = conversionWorkStates, nodeSortPreferences = nodeSortPreferences, imageFiles = imageFiles, gameSession = if (version >= 15) root.nullableString("gameSession") else null, calendarEvents=if(version<37) emptyList() else root.records("calendarEvents").map {r->r.fields("id","title","date","minute","description",*(if(version>=38) arrayOf("color") else emptyArray()));val minute=r.nullableLong("minute");require(minute==null || minute in 0L..1439L);CalendarEventEntity(r.string("id"),r.string("title"),r.string("date"),minute?.toInt(),r.string("description"),if(version>=38) r.string("color") else "LIGHT_GREY")},calendarEventPeople=if(version<37) emptyList() else root.records("calendarEventPeople").map {r->r.fields("eventId","personId");CalendarEventPersonEntity(r.string("eventId"),r.string("personId"))},personMemories=memories,personMemoryImages=memoryImages,personPhotos=personPhotos,personBirthdays=personBirthdays,personTechnologies=personTechnologies, personProfiles=personProfiles,personRelations=personRelations, iterationPlans=iterationPlans,iterationCycles=iterationCycles,iterationCommitments=iterationCommitments,iterationClosures=iterationClosures,managementParticipants=managementParticipants,participantRoles=participantRoles,objectiveResponsibilities=objectiveResponsibilities,nodeResponsibilities=nodeResponsibilities,managementRoles=managementRoles,objectiveEpics=objectiveEpics,epicStories=epicStories,storyTasks=storyTasks,backlogEntries=backlogEntries, projectCharters=if(version<22) emptyList() else root.records("projectCharters").map {row->row.fields("contextId","purpose","scope","exclusions","deliverables","successConditions");ProjectCharterEntity(row.string("contextId"),row.string("purpose"),row.string("scope"),row.string("exclusions"),row.string("deliverables"),row.string("successConditions"))}, managementObjectives=if(version<22) emptyList() else root.records("managementObjectives").map {row->row.fields("id","contextId","title","description","position");ManagementObjectiveEntity(row.string("id"),row.string("contextId"),row.string("title"),row.string("description"),row.intValue("position"))}, acceptanceCriteria=if(version<22) emptyList() else root.records("acceptanceCriteria").map {row->row.fields("id","objectiveId","description","position","validated","validatedAt");AcceptanceCriterionEntity(row.string("id"),row.string("objectiveId"),row.string("description"),row.intValue("position"),row.boolean("validated"),row.nullableLong("validatedAt"))}, sprintStories=if(version<29) emptyList() else root.records("sprintStories").map {r->r.fields("sprintId","storyId");SprintStoryEntity(r.string("sprintId"),r.string("storyId"))},objectiveWork=if(version<29) emptyList() else root.records("objectiveWork").map {r->r.fields("objectiveId","nodeId");ObjectiveWorkEntity(r.string("objectiveId"),r.string("nodeId"))},localIdentities=if(version<29) emptyList() else root.records("localIdentities").map {r->r.fields("slot","personId");LocalIdentityEntity(r.string("slot"),r.string("personId"))},ganttAnnotations=if(version<30) emptyList() else root.records("ganttAnnotations").map {r->r.fields("id","contextId","text","epochDay","lane");GanttAnnotationEntity(r.string("id"),r.string("contextId"),r.string("text"),r.integer("epochDay"),r.finiteDouble("lane"))},managementLayers=if(version<29) emptyList() else root.records("managementLayers").map {row->row.fields("nodeId","contextId","kind");ManagementLayerEntity(row.string("nodeId"),row.string("contextId"),row.string("kind"))}, managementContexts=if(version<21) emptyList() else root.records("managementContexts").map {row->row.fields("id","enabled","methodology");ManagementContextEntity(row.string("id"),row.boolean("enabled"),row.string("methodology"))}, sprintLinks=if(version<20) emptyList() else root.records("sprintLinks").map {row->row.fields("taskId","sprintId");SprintLinkEntity(row.string("taskId"),row.string("sprintId"))}, storeId=if(version<18) null else root.nullableString("storeId"),storeKind=if(version<18) "primary" else root.string("storeKind"), privatePreferences = if(version<18) emptyList() else root.records("privatePreferences").map { row -> row.fields("name","payload");PrivatePreferenceEntity(row.string("name"),row.string("payload")) }, savedTemplates = if(version >= 17) root.records("savedTemplates").map { it.fields("id","name","payload"); SavedTemplateEntity(it.string("id"),it.string("name"),it.string("payload")) } else emptyList(), metroLearningEvents = if(version<31) emptyList() else root.records("metroLearningEvents").map {r->r.fields("id","sessionId","sequence","payload","invalidReason");MetroLearningEventEntity(r.string("id"),r.string("sessionId"),r.integer("sequence"),r.string("payload"),r.nullableString("invalidReason"))}, metroPreferences = if (version >= 16) root.nullableString("metroPreferences") else null, metroJourneys = if (version >= 16) root.records("metroJourneys").map { row -> row.fields("id", "nodeId", "personId", "enabled", "payload", "revision"); MetroJourneyEntity(row.string("id"), row.nullableString("nodeId"), row.nullableString("personId"), row.boolean("enabled"), row.string("payload"), row.integer("revision")) } else emptyList()).also { it.validate() }
    }

    private fun readValue(reader: JsonReader, depth: Int, framingNumber: Boolean = false): Any {
        require(depth <= 8) { "Estructura demasiado profunda." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    require(length() < if (depth == 0) 74 else 32) { "Demasiados campos." }
                    val key = reader.nextName()
                    require(!has(key)) { "Campo duplicado." }
                    put(key, readValue(reader, depth + 1, key in setOf("avatarZoom", "avatarX", "avatarY", "photoZoom", "photoX", "photoY", "lane")))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) {
                    require(length() < BackupLimits.RECORDS) { "Demasiados registros." }
                    put(readValue(reader, depth + 1))
                }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val token = reader.nextString()
                if (framingNumber) token.toDoubleOrNull()?.also { require(it.isFinite()) } ?: error("Encuadre numérico inválido.")
                else token.toLongOrNull() ?: error("Número entero inválido o fuera de rango.")
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("Estructura JSON inválida.")
        }
    }

    private fun JSONObject.fields(vararg names: String) { require(keys().asSequence().toSet() == names.toSet()) { "Campos ausentes o desconocidos." } }
    private fun JSONObject.string(key: String) = get(key) as? String ?: error("Texto inválido: $key")
    private fun JSONObject.integer(key: String) = get(key) as? Long ?: error("Entero inválido: $key")
    private fun JSONObject.nullableLong(key: String) = if (get(key) == JSONObject.NULL) null else integer(key)
    private fun JSONObject.nullableString(key: String) = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.intValue(key: String): Int = integer(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.finiteDouble(key:String):Double = (get(key) as? Number ?: error("Número inválido: $key")).toDouble().also {require(it.isFinite())}
    private fun JSONObject.finiteFloat(key: String): Float {
        val value = get(key) as? Number ?: error("Número inválido: $key")
        return value.toFloat().also { require(it.isFinite()) }
    }
    private fun JSONObject.boolean(key:String):Boolean = get(key) as? Boolean ?: error("Boolean inválido: $key")
    private fun JSONObject.nullableInt(key:String):Int? = if(get(key)==JSONObject.NULL) null else intValue(key)
    private fun JSONObject.position(): Int = intValue("position")
    private fun JSONObject.records(key: String): List<JSONObject> {
        val array = get(key) as? JSONArray ?: error("Lista inválida: $key")
        return (0 until array.length()).map { array.get(it) as? JSONObject ?: error("Registro inválido: $key") }
    }
}
