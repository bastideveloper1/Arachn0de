package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PersonIntegrationTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:PersonRepository
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun setup()=runBlocking<Unit> {context.deleteDatabase("arachn0de.db");open();repo.save("a","Ana",null);repo.save("b","Bruno",null)}
    private fun open() {db=Arachn0deDatabase.create(context);repo=PersonRepository(db,AvatarStore(context, syncDirectory=BackupFixture::syncDirectory))}
    @After fun close() {db.close();context.deleteDatabase("arachn0de.db")}
    private fun edge(source:String="a",target:String="b",label:String="jefe/a",inverse:String="subordinado/a",mode:String="DIRECTED",id:String="edge",category:String="WORK")=PersonRelationEntity(id,source,target,category,label,inverse,"Significado ñ",mode,relationKey(source,target,category,label,inverse,mode))

    @Test fun allLevelsRenameAndDetachPreservePhaseOneAndCatalog()=runBlocking {
        db.technologyDao().insert(TechnologyEntity("k","Kotlin",null));repo.saveProfile(PersonProfileEntity("a",skills="Original"));repo.saveRelation(edge(),true)
        PersonProficiency.entries.forEach {repo.saveTechnology("a","k",it);assertEquals(it.name,db.personIntegrationDao().technologies().single().proficiency)}
        db.technologyDao().update(TechnologyEntity("k","New name",null));assertEquals("k",db.personIntegrationDao().technologies().single().technologyId)
        repo.removeTechnology("a","k");assertTrue(db.personIntegrationDao().technologies().isEmpty());assertNotNull(db.technologyDao().get("k"));assertEquals("Original",db.personProfileDao().profiles().single().skills);assertEquals(listOf(edge()),db.personProfileDao().relations())
    }
    @Test fun projectAndWorkQueriesCombineSourcesWithoutDuplicatingEntitiesOrCompletingTasks()=runBlocking {
        val projects=ProjectRepository(db.projectDao(),db);val nodes=NodeRepository(db)
        val p=projects.createProject("First");val q=projects.createProject("Second")
        val t=nodes.createNode(p.id,null,"Traditional and managed");val u=nodes.createNode(q.id,null,"Other")
        repo.setResponsiblePeople(t.id,setOf("a"));repo.setResponsiblePeople(u.id,setOf("a"))
        val c=nodes.management.configure(p.id,null,true,ManagementMethodology.HYBRID)!!.id
        nodes.management.participation.add(c,"a");nodes.management.participation.role(c,"a",ResponsibilityRole.PROJECT_LEAD,true);nodes.management.participation.role(c,"a",ResponsibilityRole.SUPERVISOR,true)
        nodes.management.participation.assign(c,"a",t.id,false,ResponsibilityRole.EXECUTOR);nodes.management.participation.assign(c,"a",t.id,false,ResponsibilityRole.VALIDATOR)
        val goal=nodes.management.planning.createObjective(c,"Goal");nodes.management.participation.assign(c,"a",goal.id,true,ResponsibilityRole.VALIDATOR)
        var work=repo.observeWork("a").first();assertEquals(setOf(p.id,q.id),work.map {it.projectId}.toSet())
        assertEquals(3,work.count {it.targetId==t.id});assertEquals(2,work.count {it.kind=="PROJECT"})
        assertEquals(setOf(t.id,u.id),com.r0ybt.arachn0de.ui.filterPersonWork(work,"ACTION","PENDING").map {it.targetId}.toSet())
        assertTrue(com.r0ybt.arachn0de.ui.filterPersonWork(work,"OBJECTIVE","COMPLETED").isEmpty());assertFalse(db.nodeDao().getById(t.id)!!.isCompleted)
        nodes.management.configure(p.id,null,false,ManagementMethodology.HYBRID);work=repo.observeWork("a").first();assertTrue(work.filter {it.contextId==c}.all {it.contextEnabled==false})
        val root=ProjectNestingRepository(db).move(p.id,q.id,null);work=repo.observeWork("a").first();assertEquals(setOf(q.id),work.map {it.projectId}.toSet());assertTrue(work.any {it.targetId==goal.id})
        ProjectNestingRepository(db).promote(root);assertEquals(2,repo.observeWork("a").first().map {it.projectId}.distinct().size)
    }
    @Test fun catalogPersonAndProjectDeletionHaveIndependentLifetimes()=runBlocking {
        val project=ProjectRepository(db.projectDao(),db).createProject("P");val task=NodeRepository(db).createNode(project.id,null,"T")
        db.technologyDao().insert(TechnologyEntity("k","Kotlin",null));repo.saveTechnology("a","k",PersonProficiency.BASIC);repo.saveTechnology("b","k",PersonProficiency.EXPERT)
        repo.saveRelation(edge(),true);repo.setResponsiblePeople(task.id,setOf("a"))
        ProjectRepository(db.projectDao(),db).deleteProject(project.id);assertTrue(repo.observeWork("a").first().isEmpty());assertEquals(2,db.personIntegrationDao().technologies().size);assertEquals(1,db.personProfileDao().relations().size)
        repo.delete("a");assertEquals(listOf(PersonTechnologyEntity("b","k","EXPERT")),db.personIntegrationDao().technologies());assertNotNull(db.technologyDao().get("k"))
        db.technologyDao().delete("k");assertTrue(db.personIntegrationDao().technologies().isEmpty());assertNotNull(db.personDao().get("b"))
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun sharedIconsSurviveRenameReplacementBackupAndRollbackWithoutCopies()=runBlocking {
        val technologies=TechnologyRepository(db,TechnologyIconStore(context,BackupFixture::syncDirectory))
        val file=java.io.File(context.cacheDir,"shared.png").apply {writeBytes(BackupFixture.png())}
        val icon=technologies.importIcon(android.net.Uri.fromFile(file));technologies.save("k","Kotlin",icon,true)
        repo.saveTechnology("a","k",PersonProficiency.ADVANCED);repo.saveTechnology("b","k",PersonProficiency.BASIC)
        repo.saveProfile(PersonProfileEntity("a",notes="Phase 1"));repo.saveRelation(edge(),true)
        technologies.save("k","Renamed",icon,false);assertEquals(2,db.personIntegrationDao().technologies().size)
        val replacement=technologies.importIcon(android.net.Uri.fromFile(file));technologies.save("k","Renamed",replacement,false)
        assertFalse(TechnologyIconStore(context).file(icon).exists());assertEquals(2,db.personIntegrationDao().technologies().size)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val before=backups.snapshot();assertEquals(1,before.technologyIcons.size)
        assertEquals(27,JSONObject(String(BackupJson.encode(before))).getInt("dataVersion"))
        assertEquals(before.personTechnologies,BackupJson.decode(BackupJson.encode(before)).personTechnologies)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_person_technology BEFORE INSERT ON person_technologies BEGIN SELECT RAISE(ABORT,'Injected failure'); END")
        assertTrue(runCatching {backups.restore(before)}.isFailure);assertEquals(before.personTechnologies,db.personIntegrationDao().technologies());assertEquals(before.personRelations,db.personProfileDao().relations());assertTrue(TechnologyIconStore(context).file(replacement).exists())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_person_technology")
        repo.removeTechnology("a","k");val inspected=backups.inspect(BackupFixture.archive(before).inputStream());try {backups.restore(inspected)} finally {backups.discard(inspected)}
        assertEquals(before.personTechnologies,db.personIntegrationDao().technologies());assertEquals(before.personProfiles,db.personProfileDao().profiles());assertEquals(before.personRelations,db.personProfileDao().relations())
        assertArrayEquals(before.technologyIcons.getValue(replacement),com.r0ybt.arachn0de.security.SecureFiles.read(TechnologyIconStore(context).file(db.technologyDao().get("k")!!.iconFile!!)))
        val restoredIcon=db.technologyDao().get("k")!!.iconFile!!
        repo.delete("a");assertTrue(TechnologyIconStore(context).file(restoredIcon).exists());assertEquals(1,db.personIntegrationDao().technologies().size)
        technologies.delete("k");assertFalse(TechnologyIconStore(context).file(restoredIcon).exists());assertTrue(db.personIntegrationDao().technologies().isEmpty());assertNotNull(db.personDao().get("b"))
    }
    @Test fun historical26AndInvalidNewReferencesAreSafe()=runBlocking {
        repo.saveProfile(PersonProfileEntity("a",notes="Historical"));val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val historical=backups.snapshot();val json=BackupJson.encode(historical);assertEquals(26,JSONObject(String(json)).getInt("dataVersion"));assertTrue(BackupJson.decode(json).personTechnologies.isEmpty())
        assertTrue(runCatching {repo.saveTechnology("a","missing",PersonProficiency.BASIC)}.isFailure)
        val invalid=historical.copy(personTechnologies=listOf(PersonTechnologyEntity("a","missing","EXPERT")));assertTrue(runCatching {backups.restore(invalid)}.isFailure);assertEquals(historical.personProfiles,db.personProfileDao().profiles())
        db.technologyDao().insert(TechnologyEntity("k","Kotlin",null));repo.saveTechnology("a","k",PersonProficiency.BASIC)
        val before=backups.snapshot();assertTrue(runCatching {before.copy(personTechnologies=before.personTechnologies+before.personTechnologies).validate()}.isFailure)
        assertTrue(runCatching {before.copy(personTechnologies=listOf(PersonTechnologyEntity("a","k","UNKNOWN"))).validate()}.isFailure)
        val inspected=backups.inspect(BackupFixture.archive(historical).inputStream());try {backups.restore(inspected)} finally {backups.discard(inspected)}
        assertTrue(db.personIntegrationDao().technologies().isEmpty());assertEquals(historical.personProfiles,db.personProfileDao().profiles())
    }
}
