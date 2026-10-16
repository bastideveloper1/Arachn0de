package com.r0ybt.arachn0de.report

import android.content.Context
import androidx.room.withTransaction
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.data.local.TechnologyIconStore
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import java.text.DateFormat
import java.util.Date

/** Called under the file mutex. Metadata snapshot and image reads cannot race restore/cleanup. */
internal object CardShareSource {
    suspend fun snapshot(app:Arachn0deApplication,storage:Context,id:String):CardShareData=app.database.withTransaction {
        val node=requireNotNull(app.nodeRepository.getNode(id)) {"El elemento ya no existe."}
        val project=app.database.projectDao().getById(node.projectId)
        val pictures=mutableListOf<CardSharePicture>()
        val people=app.database.personDao().assignmentsForNodes(listOf(id)).mapNotNull {app.database.personDao().get(it.personId)}
        val technologies=app.database.technologyDao().forNodes(listOf(id)).mapNotNull {app.database.technologyDao().get(it.technologyId)}
        app.database.projectPhotoDao().forNodes(listOf(id)).forEach {photo->pictures+=CardSharePicture("Fotografía") {AvatarStore(storage,"project-photos").read(photo.file)}}
        val attachmentRepository=app.attachmentRepository
        val attached=attachmentRepository.filesForNode(id).associateBy {it.id}
        val referenced=AttachmentReferences.ids(node.description)
        referenced.forEach {key->
            val image=requireNotNull(attached[key]) {"Falta una imagen asociada a la tarjeta."}
            val label=AttachmentReferences.matches(node.description).firstOrNull {it.groupValues[1]==key}?.let(AttachmentReferences::label)
                ?: image.originalName.substringAfterLast('/')
            if(image.mimeType.startsWith("image/")) pictures+=CardSharePicture(label) {com.r0ybt.arachn0de.ui.decodeAttachment(attachmentRepository.localFile(image))}
        }
        people.forEach {person->person.avatarFile?.let {name->pictures+=CardSharePicture(person.name,compact=true) {AvatarStore(storage).read(name)}}}
        technologies.forEach {technology->technology.iconFile?.let {name->pictures+=CardSharePicture(technology.name,compact=true) {TechnologyIconStore(storage).read(name)}}}
        val info=buildList {
            project?.let {add(it.name)}
            add(node.workState?.label ?: if(node.isCompleted) "Completada" else "Pendiente")
            node.startAt?.let {add("Inicio: ${DateFormat.getDateInstance().format(Date(it))}")}
            node.dueAt?.let {add("Vencimiento: ${DateFormat.getDateInstance().format(Date(it))}")}
            node.obligation?.let {add("Obligación: ${com.r0ybt.arachn0de.domain.model.Money.format(java.math.BigInteger.valueOf(it.amountMinor),it.currencyCode,java.util.Locale.getDefault())}")}
            if(people.isNotEmpty()) add("Responsables: ${people.joinToString {it.name}}")
            if(technologies.isNotEmpty()) add("Tecnologías: ${technologies.joinToString {it.name}}")
            attached.values.filter {it.mimeType=="audio/aac" && it.id in referenced}.forEach {add("Audio: ${it.originalName}")}
            val description=AttachmentReferences.withoutReferences(node.description).trim()
            if(description.isNotEmpty()) add(description)
        }.joinToString("\n\n")
        CardShareData(node.title,info,pictures)
    }
}
