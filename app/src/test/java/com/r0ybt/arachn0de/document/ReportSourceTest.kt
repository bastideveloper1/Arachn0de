package com.r0ybt.arachn0de.document

import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportSourceTest {
    private val app get()=ApplicationProvider.getApplicationContext<Arachn0deApplication>()
    @Test fun coreAndLayerContainOnlyTheirDescendantsWithHierarchyMetadataImagesAndNoAudio()=runBlocking<Unit> {
        val db=app.database;val imageId=UUID.randomUUID().toString();val audioId=UUID.randomUUID().toString()
        db.projectDao().insert(ProjectEntity("p","Núcleo","Introducción",0,1,1));db.projectDao().insert(ProjectEntity("other","Ajeno","",1,1,1))
        val rows=listOf(NodeEntity("layer","p",null,"Capa","Descripción",false,0,1,1,purpose="LAYER"),NodeEntity("task","p","layer","Tarea","**Antes** ${AttachmentReferences.token(imageId)} después ${AttachmentReferences.audioToken(audioId)} final",true,0,1,1,startAt=100,dueAt=200,priority="HIGH"),NodeEntity("note","p","layer","Nota","Texto de nota",false,1,1,1,purpose="NOTE"),NodeEntity("sibling","p",null,"Hermana","",false,1,1,1,purpose="LAYER"),NodeEntity("external","other",null,"Externa","",false,0,1,1))
        rows.forEach {db.nodeDao().insert(it)}
        val bytes=com.r0ybt.arachn0de.backup.BackupFixture.png();val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val image=AttachmentFileEntity(imageId,"$imageId.png","imagen.png","image/png",bytes.size.toLong(),1,1,sha,1)
        val audio=AttachmentFileEntity(audioId,"$audioId.aac","grabación.aac","audio/aac",20,0,0,"0".repeat(64),1,durationMs=100)
        db.attachmentDao().insert(image);db.attachmentDao().insert(audio);db.attachmentDao().attachNode(listOf(NodeAttachmentEntity("task",imageId),NodeAttachmentEntity("task",audioId)))
        val file=app.attachmentRepository.localFile(image);file.parentFile!!.mkdirs();SecureFiles.write(file,bytes)
        db.technologyDao().insert(TechnologyEntity("tech","Kotlin",null))
        db.technologyDao().assignProjects(listOf(ProjectTechnologyEntity("p","tech")))
        val before=db.nodeDao().getProjectNodes("p")
        val core=captureReport(app,"p",null)
        assertEquals(setOf("Núcleo","Capa","Tarea","Nota","Hermana"),core.sections.map {it.title}.toSet());assertFalse(core.sections.any {it.title=="Externa"})
        assertEquals(listOf("Tecnologías: Kotlin"),core.sections.first().metadata)
        val layer=captureReport(app,"p","layer");assertEquals(setOf("Capa","Tarea","Nota"),layer.sections.map {it.title}.toSet());assertEquals(listOf(0,1,1),layer.sections.map {it.depth});assertEquals(listOf("","1","2"),layer.sections.map {it.number})
        val task=layer.sections.first {it.title=="Tarea"};assertTrue(task.metadata.any {it.contains("Completada")});assertTrue(task.metadata.any {it.contains("Alta")});assertTrue(task.metadata.any {it.contains("Vencimiento")})
        assertEquals(listOf(imageId),task.parts.filterIsInstance<ReportPart.Image>().map {it.id});assertEquals(setOf(imageId),layer.images.keys)
        assertFalse(task.parts.toString().contains(audioId));assertFalse(task.parts.toString().contains("grabación"));assertFalse(app.attachmentRepository.localFile(audio).exists())
        assertEquals(before,db.nodeDao().getProjectNodes("p"));assertArrayEquals(bytes,SecureFiles.input(file).use {it.readBytes()})
        core.release();layer.release();assertTrue(layer.images.getValue(imageId).bytes.all {it==0.toByte()})
    }
    @Test fun missingImagesAbortRatherThanSilentlyOmitContentAndAudioTokensAreExcluded() {
        val id=UUID.randomUUID().toString()
        assertTrue(runCatching {reportParts(AttachmentReferences.token(id),emptySet(),emptySet())}.isFailure)
        assertEquals(listOf(ReportPart.Text("Antes "),ReportPart.Text(" después")),reportParts("Antes ${AttachmentReferences.audioToken(id)} después",emptySet(),setOf(id)))
    }
}
