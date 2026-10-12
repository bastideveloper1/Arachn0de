package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PersonEvolutionTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:PersonRepository
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun setup()=runBlocking<Unit> {context.deleteDatabase("arachn0de.db");open();repo.save("a","Ana",null);repo.save("b","Bruno",null)}
    private fun open() {db=Arachn0deDatabase.create(context);repo=PersonRepository(db,AvatarStore(context, syncDirectory=BackupFixture::syncDirectory))}
    @After fun close() {db.close();context.deleteDatabase("arachn0de.db")}
    private fun edge(source:String="a",target:String="b",label:String="jefe/a",inverse:String="subordinado/a",mode:String="DIRECTED",id:String="edge",category:String="WORK")=PersonRelationEntity(id,source,target,category,label,inverse,"Significado ñ",mode,relationKey(source,target,category,label,inverse,mode))
    @Test fun optionalSectionsPersistWithoutChangingAvatarOrAssignments()=runBlocking {
        val p=ProjectRepository(db.projectDao()).createProject("Project");val n=NodeRepository(db).createNode(p.id,null,"Task")
        repo.setResponsiblePeople(n.id,setOf("a"))
        repo.saveProfile(PersonProfileEntity("a","Alias","Descripción","Notas","123\n456","a@example.test","Dirección","https://example.test","Cualidades","Kotlin","Música","Tardes"))
        val profile=db.personProfileDao().profiles().single();db.close();open()
        assertEquals(profile,db.personProfileDao().profiles().single());assertEquals(listOf("a"),db.personDao().assignmentIds(n.id))
        assertEquals(PersonEntity("a","Ana",null),db.personDao().get("a"));assertEquals(11,profile.values().size)
    }
    @Test fun directedInverseIsOneRecordAndReverseDuplicateRollsBack()=runBlocking {
        val r=edge();repo.saveRelation(r,true);assertEquals("subordinado/a de",r.presentation("b"));assertEquals("a",r.other("b"))
        val reverse=edge("b","a","subordinado/a","jefe/a",id="reverse")
        assertEquals(r.relationKey,reverse.relationKey);assertTrue(runCatching {repo.saveRelation(reverse,true)}.isFailure)
        assertEquals(listOf(r),db.personProfileDao().relations())
        val mother=edge(label="madre",inverse="hijo/a",id="family",category="FAMILY");repo.saveRelation(mother,true)
        val child=edge("b","a","hijo/a","progenitor/a",id="child",category="FAMILY")
        assertEquals(mother.relationKey,child.relationKey);assertTrue(runCatching {repo.saveRelation(child,true)}.isFailure)
    }
    @Test fun customNonReciprocalAndReciprocalAreExplicit()=runBlocking {
        val r=edge(label="Mentor personal",inverse="",mode="ONE_WAY",category="CUSTOM");repo.saveRelation(r,true)
        assertEquals("Vínculo entrante: Mentor personal (sin reciprocidad)",r.presentation("b"))
        val reverse=edge("b","a","Mentor personal","","ONE_WAY","reverse","CUSTOM");repo.saveRelation(reverse,true)
        assertEquals(2,db.personProfileDao().relations().size)
        val reciprocal=edge(label="amigo/a",inverse="amigo/a",mode="RECIPROCAL",id="mutual",category="SOCIAL");repo.saveRelation(reciprocal,true)
        assertEquals("amigo/a de",reciprocal.presentation("b"))
        assertTrue(runCatching {repo.saveRelation(reciprocal.copy(id="duplicate",sourceId="b",targetId="a"),true)}.isFailure)
    }
    @Test fun editingDeletionAndInvalidReferencesPreserveOtherPerson()=runBlocking {
        val r=edge();repo.saveRelation(r,true)
        val edited=edge(label="supervisor",inverse="supervisado");repo.saveRelation(edited,false);assertEquals(listOf(edited),db.personProfileDao().relations())
        assertTrue(runCatching {repo.saveRelation(edge(target="a",id="self"),true)}.isFailure)
        assertTrue(runCatching {repo.saveRelation(edge(target="missing",id="missing"),true)}.isFailure)
        repo.deleteRelation("edge");assertTrue(db.personProfileDao().relations().isEmpty());assertNotNull(db.personDao().get("b"))
        repo.saveRelation(r,true);repo.saveProfile(PersonProfileEntity("a",notes="Private"));repo.delete("a")
        assertTrue(db.personProfileDao().relations().isEmpty());assertTrue(db.personProfileDao().profiles().isEmpty());assertNotNull(db.personDao().get("b"))
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun newBackupRestoresFullProfilesRelationsAndTraditionalParticipants()=runBlocking {
        val p=ProjectRepository(db.projectDao()).createProject("Project");val n=NodeRepository(db).createNode(p.id,null,"Task")
        val contextId=NodeRepository(db).management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
        val participation=NodeRepository(db).management.participation;participation.add(contextId,"a");participation.assign(contextId,"a",n.id,false,ResponsibilityRole.EXECUTOR)
        val input=java.io.File(context.cacheDir,"advanced-avatar.png").apply {writeBytes(BackupFixture.png())}
        val avatar=repo.importAvatar(android.net.Uri.fromFile(input))
        repo.save("a","Ana",avatar,isNew=false,zoom=3f,x=.4f,y=-.2f);repo.save("b","Bruno",avatar,isNew=false)
        repo.setResponsiblePeople(n.id,setOf("a"));repo.saveProfile(PersonProfileEntity("a",notes="Full notes ñ",emails="one\ntwo"));repo.saveRelation(edge(),true)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val before=backups.snapshot()
        val json=BackupJson.encode(before);assertEquals(26,JSONObject(String(json)).getInt("dataVersion"));val decoded=BackupJson.decode(json)
        assertEquals(before.personProfiles,decoded.personProfiles);assertEquals(before.personRelations,decoded.personRelations)
        val filesBefore=java.io.File(context.filesDir,"avatars").listFiles()!!.map {it.name}.toSet()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER injected_person_relation_failure BEFORE INSERT ON person_relations BEGIN SELECT RAISE(ABORT, 'Injected relation restore failure'); END")
        assertTrue(runCatching {backups.restore(before)}.isFailure)
        assertEquals(before.personProfiles,db.personProfileDao().profiles());assertEquals(before.personRelations,db.personProfileDao().relations())
        assertEquals(before.persons,db.backupDao().persons());assertEquals(filesBefore,java.io.File(context.filesDir,"avatars").listFiles()!!.map {it.name}.toSet())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER injected_person_relation_failure")
        repo.delete("a");assertTrue(AvatarStore(context).exists(avatar));val inspected=backups.inspect(BackupFixture.archive(before).inputStream())
        try {backups.restore(inspected)} finally {backups.discard(inspected)}
        assertEquals(before.personProfiles,db.personProfileDao().profiles());assertEquals(before.personRelations,db.personProfileDao().relations());assertEquals(before.nodeResponsibilities,db.participationDao().nodes());assertEquals(listOf("a"),db.personDao().assignmentIds(n.id))
        val restored=db.personDao().get("a")!!;assertEquals(3f,restored.avatarZoom,0f);assertEquals(.4f,restored.avatarX,0f)
        assertEquals(restored.avatarFile,db.personDao().get("b")!!.avatarFile)
        assertArrayEquals(before.avatars.getValue(avatar),com.r0ybt.arachn0de.security.SecureFiles.read(java.io.File(context.filesDir,"avatars/${restored.avatarFile}")))
    }
    @Test fun historicalBackupsDefaultEmptyAndInvalidNewBackupCannotReplaceData()=runBlocking {
        val old=BackupFixture.empty().copy(persons=listOf(PersonEntity("old","Old",null)))
        val oldJson=BackupJson.encode(old);assertTrue(JSONObject(String(oldJson)).getInt("dataVersion")<26)
        val data=BackupJson.decode(oldJson);assertTrue(data.personProfiles.isEmpty());assertTrue(data.personRelations.isEmpty())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val before=backups.snapshot()
        val corrupt=before.copy(personRelations=listOf(edge(target="missing")))
        assertTrue(runCatching {corrupt.validate()}.isFailure);assertEquals(before.persons,backups.snapshot().persons)
        val archive=BackupFixture.archive(old);val inspected=backups.inspect(archive.inputStream());try {backups.restore(inspected)} finally {backups.discard(inspected)}
        assertEquals(listOf(PersonEntity("old","Old",null)),db.backupDao().persons());assertTrue(db.personProfileDao().profiles().isEmpty())
    }
    @Test fun limitsUnknownModesAndMissingInverseAreRejectedWithoutWrites()=runBlocking {
        assertTrue(runCatching {repo.saveProfile(PersonProfileEntity("a",notes="x".repeat(16001)))}.isFailure)
        assertTrue(runCatching {repo.saveRelation(edge(inverse=""),true)}.isFailure)
        assertTrue(runCatching {repo.saveRelation(edge(mode="UNKNOWN"),true)}.isFailure)
        assertTrue(db.personProfileDao().profiles().isEmpty());assertTrue(db.personProfileDao().relations().isEmpty())
    }
}
