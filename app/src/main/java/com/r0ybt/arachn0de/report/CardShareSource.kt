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
    suspend fun snapshot(app:Arachn0deApplication,storage:Context,id:String,onlyPending:Boolean=false):CardShareData=
        snapshot(app.database,app.nodeRepository,app.attachmentRepository,storage,id,onlyPending)
    suspend fun snapshot(session:com.r0ybt.arachn0de.security.VaultSession,id:String,onlyPending:Boolean=false):CardShareData=
        snapshot(session.database,session.nodes,session.attachments,session.context,id,onlyPending)
    private suspend fun snapshot(database:com.r0ybt.arachn0de.data.local.Arachn0deDatabase,nodes:com.r0ybt.arachn0de.data.repository.NodeRepository,
        attachments:com.r0ybt.arachn0de.data.repository.AttachmentRepository,storage:Context,id:String,onlyPending:Boolean):CardShareData=database.withTransaction {
        val node=requireNotNull(nodes.getNode(id)) {"El elemento ya no existe."}
        require(!onlyPending || node.purpose!=com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION || !node.isCompleted) {
            "Guardar imagen solo incluye tareas pendientes o por verificar."
        }
        val project=node.projectId?.let {database.projectDao().getById(it)}
        val pictures=mutableListOf<CardSharePicture>()
        val people=database.personDao().assignmentsForNodes(listOf(id)).mapNotNull {database.personDao().get(it.personId)}
        val technologies=database.technologyDao().forNodes(listOf(id)).mapNotNull {database.technologyDao().get(it.technologyId)}
        database.projectPhotoDao().forNodes(listOf(id)).forEach {photo->pictures+=CardSharePicture("Fotografía") {AvatarStore(storage,"project-photos").read(photo.file)}}
        val attachmentRepository=attachments
        val attached=attachmentRepository.filesForNode(id).associateBy {it.id}
        val referenced=AttachmentReferences.ids(node.description)
        referenced.forEach {key->
            val image=requireNotNull(attached[key]) {"Falta una imagen asociada a la tarjeta."}
            val label=AttachmentReferences.matches(node.description).firstOrNull {it.groupValues[1]==key}?.let(AttachmentReferences::label)
                ?: image.originalName.substringAfterLast('/')
            if(image.mimeType.startsWith("image/")) pictures+=CardSharePicture(listOf(label,image.originalName.substringAfterLast('/').substringAfterLast('\\'),image.mimeType.removePrefix("image/").uppercase(),android.text.format.Formatter.formatShortFileSize(storage,image.byteSize)).distinct().joinToString(" · ")) {decodeCardReference(attachmentRepository.localFile(image))}
        }
        technologies.forEach {technology->technology.iconFile?.let {name->pictures+=CardSharePicture(technology.name,compact=true) {TechnologyIconStore(storage).read(name)}}}
        val info=buildList {
            node.startAt?.let {add("Inicio: ${DateFormat.getDateInstance().format(Date(it))}")}
            node.obligation?.let {add("Pago: ${com.r0ybt.arachn0de.domain.model.Money.format(java.math.BigInteger.valueOf(it.amountMinor),it.currencyCode,java.util.Locale.getDefault())}")}
            if(technologies.isNotEmpty()) add("Tecnologías: ${technologies.joinToString {it.name}}")
            attached.values.filter {it.mimeType=="audio/aac" && it.id in referenced}.forEach {add("Audio: ${it.originalName}")}
            val description=AttachmentReferences.withoutReferences(node.description).trim()
            if(description.isNotEmpty()) add(description)
        }.joinToString("\n\n")
        val tags=database.tagDao().nodeIds(id).mapNotNull {database.tagDao().get(it)?.name}
        CardShareData(node.title,info,pictures,project=project?.name,
            status=if(node.isCompletable)node.workState?.label ?: node.taskStatus.label else null,
            fields=listOf(
                CardShareField("Creada",DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(node.createdAt))),
                CardShareField("Asignada",people.joinToString {it.name}.ifBlank {"—"}),
                CardShareField("Vencimiento",node.dueAt?.let {DateFormat.getDateInstance().format(Date(it))} ?: "—"),
                CardShareField("Etiqueta",tags.joinToString().ifBlank {"—"})))
    }
    /** A Núcleo has no node row: feed its own visible data to the same card renderer. */
    suspend fun projectSnapshot(session:com.r0ybt.arachn0de.security.VaultSession,id:String):CardShareData=session.database.withTransaction {
        val db=session.database;val storage=session.context
        val project=requireNotNull(db.projectDao().getById(id)) {"El Núcleo ya no existe."}
        val pictures=mutableListOf<CardSharePicture>()
        db.projectPhotoDao().forProject(id)?.let {photo->pictures+=CardSharePicture("Fotografía") {AvatarStore(storage,"project-photos").read(photo.file)}}
        val attached=session.attachments.filesForProject(id).associateBy {it.id}
        val referenced=AttachmentReferences.ids(project.description)
        referenced.forEach {key->
            val image=requireNotNull(attached[key]) {"Falta una imagen asociada al Núcleo."}
            val label=AttachmentReferences.matches(project.description).firstOrNull {it.groupValues[1]==key}?.let(AttachmentReferences::label) ?: image.originalName.substringAfterLast('/')
            if(image.mimeType.startsWith("image/")) pictures+=CardSharePicture(listOf(label,image.originalName.substringAfterLast('/').substringAfterLast('\\'),image.mimeType.removePrefix("image/").uppercase(),android.text.format.Formatter.formatShortFileSize(storage,image.byteSize)).distinct().joinToString(" · ")) {decodeCardReference(session.attachments.localFile(image))}
        }
        val roots=db.conversionDao().roots().filter {it.projectId==id}.mapTo(hashSetOf()) {it.id}
        val people=db.conversionDao().people().filter {it.rootId in roots}.mapNotNull {db.personDao().get(it.personId)}.distinctBy {it.id}
        val technologies=db.technologyDao().projects().filter {it.projectId==id}.mapNotNull {db.technologyDao().get(it.technologyId)}
        technologies.forEach {technology->technology.iconFile?.let {name->pictures+=CardSharePicture(technology.name,compact=true) {TechnologyIconStore(storage).read(name)}}}
        val info=buildList {
            if(people.isNotEmpty())add("Responsables: ${people.joinToString {it.name}}")
            if(technologies.isNotEmpty())add("Tecnologías: ${technologies.joinToString {it.name}}")
            attached.values.filter {it.mimeType=="audio/aac" && it.id in referenced}.forEach {add("Audio: ${it.originalName}")}
            AttachmentReferences.withoutReferences(project.description).trim().takeIf {it.isNotEmpty()}?.let(::add)
        }.joinToString("\n\n")
        CardShareData(project.name,info,pictures,fields=listOf(
            CardShareField("Creada",DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(project.createdAt))),
            CardShareField("Asignada",people.joinToString {it.name}.ifBlank {"—"})))
    }

}
