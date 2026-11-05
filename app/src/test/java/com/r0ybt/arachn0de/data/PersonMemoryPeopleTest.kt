package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonMemoryPeopleTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var vault:VaultContext
    private lateinit var session:SecureFiles.Session
    private lateinit var people:PersonRepository
    private lateinit var attachments:AttachmentRepository
    private lateinit var repo:PersonMemoryRepository
    private lateinit var root:File
    private lateinit var entry:PersonMemoryEntity
    private lateinit var images:List<PersonMemoryImageEntity>
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup()=runBlocking<Unit> {
        val id=UUID.randomUUID();root=File(context.filesDir,"memory-people-$id").apply {mkdirs()}
        session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session)
        vault=VaultContext(context,session);db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        people=PersonRepository(db,AvatarStore(vault,syncDirectory=BackupFixture::syncDirectory))
        attachments=AttachmentRepository(db,AttachmentStore(vault,BackupFixture::syncDirectory));repo=PersonMemoryRepository(db,vault,attachments,people)
        for((person,name) in listOf("owner" to "Yo","ana" to "Ana","pedro" to "Pedro")) people.save(person,name,null)
        val source=File(context.cacheDir,"memory-person-${UUID.randomUUID()}.png").apply {writeBytes(BackupFixture.png())}
        try {
            val file=attachments.import(Uri.fromFile(source),"draft");val post=repo.create("owner",listOf(file.id),"draft")
            entry=db.personMemoryDao().memories().single {it.id==post};images=db.personMemoryDao().images()
        } finally {source.delete()}
    }
    @After fun close() {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    private fun free(name:String)=PersonMemoryPersonEntity(UUID.randomUUID().toString(),entry.id,null,name)
    private fun linked(id:String)=PersonMemoryPersonEntity(UUID.randomUUID().toString(),entry.id,id,id)
    private suspend fun save(rows:List<PersonMemoryPersonEntity>,visible:Boolean=true)=repo.save(entry.copy(peopleVisible=visible),images,"edit",rows)
    @Test fun addsSeveralPeopleAndRejectsRepeatedProfilesOrEquivalentFreeNames()=runBlocking<Unit> {
        val rows=listOf(linked("ana"),linked("pedro"),free("  Carmen  "))
        save(rows);val stored=db.personMemoryDao().people()
        assertEquals(3,stored.size);assertEquals("Carmen",stored.last().label)
        assertEquals(3,db.backupDao().persons().size)
        assertTrue(runCatching {save(rows+linked("ana"))}.isFailure)
        assertTrue(runCatching {save(rows+free("cArMeN"))}.isFailure)
        assertEquals(stored,db.personMemoryDao().people())
        assertTrue(runCatching {save(listOf(linked("missing")))}.isFailure)
    }
    @Test fun freeNamesCanBeEditedRemovedAndLinkedWithoutLosingIdentityOrOriginalText()=runBlocking<Unit> {
        val original=free("Ana de la reunión");save(listOf(original))
        val stored=db.personMemoryDao().people().single()
        save(listOf(stored.copy(label="Ana del sábado")))
        val edited=db.personMemoryDao().people().single();assertEquals(original.id,edited.id)
        save(listOf(edited.copy(personId="ana")))
        val attached=db.personMemoryDao().people().single()
        assertEquals(original.id,attached.id);assertEquals("Ana del sábado",attached.label);assertEquals("Ana",attached.profileName)
        assertEquals("Ana",attached.name(mapOf("ana" to "Ana")))
        save(emptyList());assertTrue(db.personMemoryDao().people().isEmpty());assertNotNull(db.personDao().get("ana"))
    }
    @Test fun profileRenameUpdatesPresentationAndDeletionKeepsLatestNameWithoutDanglingReferences()=runBlocking<Unit> {
        val original=free("Compañera");save(listOf(original.copy(personId="ana")))
        assertTrue(people.save("ana","Anita",null,isNew=false))
        val state=repo.observe().first();assertEquals("Anita",state.people(entry.id).single().name(state.profiles.associate {it.id to it.name}))
        val stale=db.personMemoryDao().people().single()
        assertTrue(people.delete("ana"))
        val detached=db.personMemoryDao().people().single()
        assertNull(detached.personId);assertEquals("Anita",detached.name(emptyMap()));assertEquals("Compañera",detached.label)
        save(listOf(stale)) // An editor opened before deletion must not resurrect the FK.
        assertNull(db.personMemoryDao().people().single().personId)
        assertEquals("Anita",db.personMemoryDao().people().single().name(emptyMap()))
    }
    @Test fun hidingSurvivesReopenAndOtherFieldEditsWithoutDeletingAssociations()=runBlocking<Unit> {
        save(listOf(linked("ana"),free("Invitada")),visible=false)
        val hidden=repo.observe().first();assertTrue(hidden.visiblePeople(hidden.entries.single()).isEmpty());assertEquals(2,hidden.people(entry.id).size)
        repo.save(entry.copy(description="Nueva descripción"),images,"other-field")
        assertFalse(db.personMemoryDao().memories().single().peopleVisible)
        save(db.personMemoryDao().people(),visible=true)
        val visible=repo.observe().first();assertEquals(2,visible.visiblePeople(visible.entries.single()).size)
        repo.deleteEntry(entry.id);assertTrue(db.personMemoryDao().people().isEmpty());assertNotNull(db.personDao().get("ana"))
    }
    @Test fun encryptedBackupRestoresHiddenAssociationsAndRejectsInvalidReferencesBeforeReplacement()=runBlocking<Unit> {
        save(listOf(linked("ana"),free("Invitada")),visible=false)
        val before=db.personMemoryDao().people()
        val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
        val archive=backup.create("people-backup-fixture".toCharArray())
        val portable=SecureFiles.read(archive);assertTrue(EncryptedBackup.isEncrypted(portable.copyOfRange(0,8)))
        val candidate=backup.inspect(portable.inputStream(),"people-backup-fixture".toCharArray())
        save(emptyList(),visible=true);backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
        assertEquals(before,db.personMemoryDao().people());assertFalse(db.personMemoryDao().memories().single().peopleVisible)
        val snapshot=backup.snapshot();assertEquals(40,JSONObject(BackupJson.encode(snapshot).toString(Charsets.UTF_8)).getInt("dataVersion"))
        val bad=snapshot.copy(personMemoryPeople=before.map {it.copy(personId="missing")})
        assertTrue(runCatching {backup.restore(bad)}.isFailure);assertEquals(before,db.personMemoryDao().people())
        assertFalse(db.personMemoryDao().memories().single().peopleVisible)
        assertArrayEquals(BackupFixture.png(),SecureFiles.read(attachments.localFile(db.attachmentDao().files().single())))
    }
    @Test fun historicalVersion39InitializesEmptyAssociationsAndDefaultPresentation()=runBlocking<Unit> {
        val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot();val json=JSONObject(BackupJson.encode(snapshot).toString(Charsets.UTF_8))
        assertEquals(39,json.getInt("dataVersion"))
        val old=BackupJson.decode(json.toString().toByteArray()).copy(attachmentContents=snapshot.attachmentContents)
        assertTrue(old.personMemoryPeople.isEmpty());assertTrue(old.personMemories.single().peopleVisible)
        save(listOf(free("Temporal")),visible=false)
        backup.restore(old)
        assertTrue(db.personMemoryDao().people().isEmpty());assertTrue(db.personMemoryDao().memories().single().peopleVisible)
        assertEquals(entry.description,db.personMemoryDao().memories().single().description)
    }
}
