package com.r0ybt.arachn0de.export

import android.app.Application
import com.r0ybt.arachn0de.backup.BackupData
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ElementExportTest {
    private val project=ProjectEntity("p","Núcleo á & []","",0,1,2)
    private val task=NodeEntity("task","p","layer","Revisar *API*","- lista\n\n```kotlin\nval x = 1\n```",false,0,1,2,startAt=1000,dueAt=2000,priority="HIGH",awaitingVerification=true)
    private val layer=task.copy(id="layer",parentId=null,title="Capa",description="",purpose="LAYER",awaitingVerification=false)
    private fun data(nodes:List<NodeEntity> = listOf(layer,task))=BackupData("test",1,listOf(project),nodes,emptyList(),emptyList(),emptyMap(),
        tags=listOf(TagEntity("tag","Etiqueta","etiqueta")),nodeTags=listOf(NodeTagEntity("task","tag")))
    @Test fun markdownKeepsHierarchyCodeListsDatesTagsAndRealStates() {
        val export=ElementExport(data(),listOf("p" to 0,"layer" to 1,"task" to 2),"p",null,true)
        val text=export.markdown()
        assertTrue(text.contains("# Núcleo"));assertTrue(text.contains("## Capa"));assertTrue(text.contains("### Revisar \\*API\\*"))
        assertTrue(text.contains("- lista\n\n```kotlin\nval x = 1\n```"))
        assertTrue(text.contains("Estado: Por verificar"));assertTrue(text.contains("Etiquetas: Etiqueta"))
        assertTrue(text.contains("Inicio: 1970"));assertTrue(text.contains("Vencimiento: 1970"))
        val completed=ElementExport(data(listOf(task.copy(isCompleted=true,awaitingVerification=false))),listOf("task" to 0),"p","task",false)
        assertTrue(completed.markdown().contains("Estado: Completada"))
        val empty=ElementExport(data(emptyList()),listOf("p" to 0),"p",null,false).markdown()
        assertFalse(empty.contains("Estado:"));assertFalse(empty.contains("Etiquetas:"))
    }
    @Test fun partialJsonUsesBackupFieldsRetainsExternalParentsAndContainsNoPrivatePreferences() {
        val source=data(listOf(task)).copy(projects=emptyList())
        val export=ElementExport(source,listOf("task" to 0),"p","task",false)
        val json=JSONObject(String(export.json()))
        assertEquals("arachn0de-element-export",json.getString("kind"));assertTrue(json.getBoolean("partial"))
        assertFalse(json.getJSONObject("root").getBoolean("descendants"))
        val records=json.getJSONObject("records")
        assertTrue(records.getInt("dataVersion")>=43)
        val node=records.getJSONArray("nodes").getJSONObject(0)
        assertEquals("task",node.getString("id"));assertEquals("layer",node.getString("parentId"))
        assertEquals("p",node.getString("projectId"));assertTrue(node.getBoolean("awaitingVerification"))
        assertEquals(0,records.getJSONArray("privatePreferences").length())
        assertFalse(json.toString().contains("/data/"))
        assertTrue(runCatching {com.r0ybt.arachn0de.backup.BackupJson.decode(export.json())}.isFailure)
    }
    @Test
    @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class)
    fun committedCaptureHonorsBranchAndSingleScopesWithoutWriting()=runBlocking {
        val app=androidx.test.core.app.ApplicationProvider.getApplicationContext<com.r0ybt.arachn0de.Arachn0deApplication>()
        val db=app.database
        db.projectDao().insert(project)
        db.projectDao().insert(project.copy(id="other-project",name="Never export"))
        db.nodeDao().insert(layer);db.nodeDao().insert(task)
        db.nodeDao().insert(task.copy(id="sibling",parentId=null,title="Sibling secret",awaitingVerification=false))
        db.nodeDao().insert(task.copy(id="foreign",projectId="other-project",parentId=null,title="Foreign secret",awaitingVerification=false))
        val before=db.backupDao().nodes()
        try {
        val single=captureElement(app,"p","task",false)
        assertEquals(listOf("task"),single.data.nodes.map {it.id})
        assertEquals(0,single.data.projects.size)
        val branch=captureElement(app,"p","layer",true)
        assertEquals(listOf("layer","task"),branch.data.nodes.map {it.id})
        val core=captureElement(app,"p",null,false)
        assertTrue(core.data.nodes.isEmpty());assertEquals(listOf("p"),core.data.projects.map {it.id})
        val all=captureElement(app,"p",null,true)
        assertEquals(setOf("layer","task","sibling"),all.data.nodes.map {it.id}.toSet())
        assertFalse(String(branch.json()).contains("Sibling secret"));assertFalse(String(all.json()).contains("Foreign secret"))
        assertEquals(before,db.backupDao().nodes())
        } finally {
            // No audio is running in this capture fixture. Close directly before the
            // legacy application teardown, whose paused main looper cannot join audio jobs.
            val session=app.security.current.value
            app.security.current.value=null
            com.r0ybt.arachn0de.security.SecureFiles.activeProfile=null
            session?.close()
        }
    }
    @Test fun zippedAttachmentsKeepExactBytesRelativeReferencesAndRejectCorruption()=runBlocking {
        val id="12345678-1234-1234-1234-123456789abc"
        val raw="recording bytes".toByteArray()
        val file=File.createTempFile("element-export-",".aac")
        try {
            file.writeBytes(raw)
            val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {"%02x".format(it)}
            val row=AttachmentFileEntity(id,"private.aac","Audio con espacios.aac","audio/aac",raw.size.toLong(),0,0,hash,1)
            val node=task.copy(description="Audio: "+AttachmentReferences.audioToken(id))
            val source=data(listOf(node)).copy(projects=emptyList(),attachmentFiles=listOf(row),nodeAttachments=listOf(NodeAttachmentEntity(node.id,id)),attachmentContents=mapOf(row.storageName to file))
            val export=ElementExport(source,listOf("task" to 0),"p","task",false)
            val output=ByteArrayOutputStream();export.write(output,false,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
            val entries=linkedMapOf<String,ByteArray>()
            ZipInputStream(output.toByteArray().inputStream()).use {zip->while(true) {val entry=zip.nextEntry ?: break;entries[entry.name]=zip.readBytes()}}
            assertArrayEquals(raw,entries["files/attachments/$id.aac"])
            assertTrue(String(entries.getValue("contenido.md")).contains("](files/attachments/$id.aac)"))
            assertFalse(String(export.json()).contains(file.absolutePath))
            val jsonOutput=ByteArrayOutputStream();export.write(jsonOutput,true,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
            val jsonEntries=linkedMapOf<String,ByteArray>()
            ZipInputStream(jsonOutput.toByteArray().inputStream()).use {zip->while(true) {val entry=zip.nextEntry ?: break;jsonEntries[entry.name]=zip.readBytes()}}
            val document=JSONObject(String(jsonEntries.getValue("element.json")))
            assertEquals("files/attachments/$id.aac",document.getJSONObject("attachmentPaths").getString(id))
            assertArrayEquals(raw,jsonEntries["files/attachments/$id.aac"])
            file.writeBytes(ByteArray(raw.size))
            try {export.write(ByteArrayOutputStream(),true,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory);fail("Expected integrity failure")} catch(_:java.io.IOException) {}
        } finally {file.delete()}
    }
}
