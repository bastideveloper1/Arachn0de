package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class ProjectTagsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var projects:ProjectRepository
    private lateinit var tags:TagRepository
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);projects=ProjectRepository(db.projectDao(),db);tags=TagRepository(db)}
    @After fun close() {db.close()}
    @Test fun projectMetadataSurvivesRealBackupReopenAndInvalidChangesRollBack()=runBlocking<Unit> {
        val tag=tags.create("Evaluar");db.personDao().insert(PersonEntity("person","Ana",null))
        assertTrue(projects.saveWithTechnologies(null,"root","Núcleo","Texto",null,setOf(tag.id),setOf("person"),Priority.HIGH))
        val before=db.conversionDao().project("root")!!
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val candidate=backup.inspect(backup.create().readBytes().inputStream())
        db.conversionDao().clearTags(before.id)
        backup.restore(candidate);backup.discard(candidate)
        assertEquals(setOf(tag.id),tags.observe().first().projectIds["root"])
        assertEquals(before,db.conversionDao().project("root"))
        assertEquals(listOf(ConversionPersonEntity(before.id,"person")),db.conversionDao().people())
        assertTrue(runCatching {projects.saveWithTechnologies("root","root","Cambio","",null,setOf("missing"))}.isFailure)
        assertEquals("Núcleo",db.projectDao().getById("root")!!.name)
        assertEquals(setOf(tag.id),tags.observe().first().projectIds["root"])
        val snapshot=backup.snapshot()
        assertTrue(runCatching {backup.restore(snapshot.copy(conversionTags=listOf(ConversionTagEntity(before.id,"missing"))))}.isFailure)
        assertEquals(snapshot.conversionTags,backup.snapshot().conversionTags)
        db.close();db=Arachn0deDatabase.create(context);tags=TagRepository(db);projects=ProjectRepository(db.projectDao(),db)
        assertEquals(setOf(tag.id),tags.observe().first().projectIds["root"])
        val old=BackupJson.decode(BackupJson.encode(BackupFixture.empty()))
        BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)).restore(old)
        assertTrue(tags.observe().first().projectIds.isEmpty())
    }
    @Test fun conversionKeepsTagsAndLegacyMultipleTagsRemainUntilAnExplicitReplacement()=runBlocking<Unit> {
        val a=tags.create("A");val b=tags.create("B")
        projects.saveWithTechnologies(null,"root","Origen","",null,setOf(a.id))
        val target=projects.createProject("Destino")
        val nesting=ProjectNestingRepository(db)
        val layer=nesting.move("root",target.id,null)
        assertEquals(setOf(a.id),tags.observe().first().nodeIds[layer])
        val restored=nesting.promote(layer)
        assertEquals(setOf(a.id),tags.observe().first().projectIds[restored])
        val root=db.conversionDao().project(restored)!!
        db.conversionDao().tags(listOf(ConversionTagEntity(root.id,b.id)))
        projects.saveWithTechnologies(restored,restored,"Editado","",null,setOf(a.id,b.id))
        assertEquals(setOf(a.id,b.id),tags.observe().first().projectIds[restored])
        assertTrue(runCatching {projects.saveWithTechnologies(null,"bad","Nuevo","",null,setOf(a.id,b.id))}.isFailure)
        assertNull(db.projectDao().getById("bad"))
        projects.saveWithTechnologies(restored,restored,"Editado","",null,setOf(b.id))
        assertEquals(setOf(b.id),tags.observe().first().projectIds[restored])
        projects.saveWithTechnologies(restored,restored,"Editado","",null,emptySet())
        assertTrue(tags.observe().first().projectIds[restored].isNullOrEmpty())
        assertEquals(2,db.tagDao().tags().size)
        projects.deleteProject(restored);assertTrue(db.conversionDao().tags().isEmpty())
    }
}
