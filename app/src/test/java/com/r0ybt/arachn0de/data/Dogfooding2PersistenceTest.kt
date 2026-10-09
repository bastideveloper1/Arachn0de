package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class Dogfooding2PersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    @Before fun setup() {context.deleteDatabase("arachn0de.db");File(context.cacheDir,"project-thumbnails").deleteRecursively();db=Arachn0deDatabase.create(context)}
    @After fun close() {db.close()}
    @Test fun photoSourceKeepsResolutionAlphaFramingAndBackupBytesWhileThumbnailIsSeparate()=runBlocking {
        val bitmap=Bitmap.createBitmap(4000,2000,Bitmap.Config.ARGB_8888).apply {eraseColor(0x80abcdef.toInt())}
        val source=File(context.cacheDir,"high-quality.png")
        source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val store=ProjectPhotoStore(context,BackupFixture::syncDirectory)
        val photos=ProjectPhotoRepository(db,store)
        val projects=ProjectRepository(db.projectDao(),db)
        val project=projects.createProject("Quality")
        val name=photos.import(Uri.fromFile(source),"quality-editor")
        val bytes=store.durable.read(name)
        val decoded=AvatarStore(context,"project-photos").read(name)!!
        assertEquals(2048,decoded.width);assertEquals(1024,decoded.height);assertTrue(decoded.hasAlpha());decoded.recycle()
        val framing=AvatarFraming(4f,.3f,-.2f);photos.save(project.id,name,framing,"quality-editor")
        val thumb=AvatarStore(context,"project-photos").readThumbnail(name,240)!!
        assertEquals(240,thumb.width);assertEquals(120,thumb.height)
        assertArrayEquals(bytes,store.durable.read(name))
        assertTrue(File(context.cacheDir,"project-thumbnails").listFiles()!!.sumOf {it.length()}<=ProjectThumbnailCache.MAX_DISK_BYTES)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val output=backup.create();val inspected=backup.inspect(output.inputStream())
        assertEquals(19,org.json.JSONObject(String(BackupJson.encode(inspected))).getInt("dataVersion"))
        backup.restore(inspected);backup.discard(inspected)
        val restored=projects.getProject(project.id)!!.photo!!
        assertEquals(framing,restored.framing);assertArrayEquals(bytes,store.durable.read(restored.file))
        db.close();db=Arachn0deDatabase.create(context)
        assertEquals(framing,ProjectRepository(db.projectDao(),db).getProject(project.id)!!.photo!!.framing)
    }
    @Test fun layersFirstBackupsRoundTripAndHistoricalDataDefaultsToFalse()=runBlocking {
        val p=ProjectRepository(db.projectDao(),db).createProject("Backup order")
        val repository=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        db.nodeSortPreferenceDao().save(NodeSortPreferenceEntity("${p.id}:project-root","PRIORITY",true))
        val exported=repository.create();val inspected=repository.inspect(exported.inputStream())
        val json=org.json.JSONObject(String(BackupJson.encode(inspected)))
        assertEquals(19,json.getInt("dataVersion"))
        repository.restore(inspected);repository.discard(inspected)
        assertTrue(db.nodeSortPreferenceDao().all().single().layersFirst)
        val row=json.getJSONArray("nodeSortPreferences").getJSONObject(0)
        row.put("layersFirst","invalid")
        assertThrows(IllegalStateException::class.java) {BackupJson.decode(json.toString().toByteArray())}
        assertTrue(db.nodeSortPreferenceDao().all().single().layersFirst)
        row.remove("layersFirst");json.put("dataVersion",18)
        assertFalse(BackupJson.decode(json.toString().toByteArray()).nodeSortPreferences.single().layersFirst)
    }
    @Test fun exifOrientationIsAppliedOnceWithoutChangingTheAspectOrBytesOnFraming()=runBlocking {
        val bitmap=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val source=File(context.cacheDir,"oriented.jpg");source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        android.media.ExifInterface(source.path).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
        val store=ProjectPhotoStore(context,BackupFixture::syncDirectory)
        val name=store.import(Uri.fromFile(source)) {}
        val decoded=AvatarStore(context,"project-photos").read(name)!!
        assertEquals(900,decoded.width);assertEquals(1800,decoded.height);decoded.recycle()
    }
    @Test fun layerReorderAndConversionKeepTaskPositionsRelationsAndIndependentAutomaticCriteria()=runBlocking {
        val projects=ProjectRepository(db.projectDao(),db);val nodes=NodeRepository(db)
        val p=projects.createProject("Mixed")
        val task=nodes.createNode(p.id,null,"Task",dueAt=100)
        val first=nodes.createNode(p.id,null,"First",purpose=NodePurpose.LAYER)
        val other=nodes.createNode(p.id,null,"Other",dueAt=200)
        val second=nodes.createNode(p.id,null,"Second",purpose=NodePurpose.LAYER)
        val before=nodes.getProjectNodes(p.id).filter {it.isCompletable}.associate {it.id to it.position}
        assertTrue(nodes.reorderNode(second.id,null,true,layersOnly=true))
        assertEquals(before,nodes.getProjectNodes(p.id).filter {it.isCompletable}.associate {it.id to it.position})
        val ordered=com.r0ybt.arachn0de.ui.state.NodePresentationSort.children(nodes.getProjectNodes(p.id),com.r0ybt.arachn0de.ui.state.NodeSortMode.DUE_PRIORITY,layersFirst=true)
        assertEquals(listOf(second.id,first.id,task.id,other.id),ordered.map {it.id})
        db.technologyDao().insert(TechnologyEntity("tech","Assigned",null));db.technologyDao().assignNodes(listOf(NodeTechnologyEntity(other.id,"tech",0)))
        val original=nodes.getNode(other.id)!!
        assertTrue(nodes.convertPurpose(other.id,NodePurpose.LAYER))
        assertTrue(nodes.reorderNode(other.id,null,true,layersOnly=true))
        val converted=nodes.getNode(other.id)!!
        assertEquals(original.title,converted.title);assertEquals(original.description,converted.description);assertEquals(original.dueAt,converted.dueAt)
        assertEquals(listOf("tech"),db.technologyDao().forNodes(listOf(other.id)).map {it.technologyId})
        val prefs=NodeSortPreferenceRepository(db);prefs.set("${p.id}:project-root","DUE_PRIORITY",true)
        prefs.set("${p.id}:${first.id}","CREATED_NEWEST")
        prefs.set("${p.id}:${second.id}","DUE_PRIORITY",true)
        assertEquals("INHERIT",db.nodeSortPreferenceDao().all().first {it.context.endsWith(second.id)}.mode)
        prefs.set("${p.id}:project-root","PRIORITY")
        assertTrue(db.nodeSortPreferenceDao().all().first {it.context.endsWith(second.id)}.layersFirst)
        assertEquals("CREATED_NEWEST",db.nodeSortPreferenceDao().all().first {it.context.endsWith(first.id)}.mode)
    }
}
