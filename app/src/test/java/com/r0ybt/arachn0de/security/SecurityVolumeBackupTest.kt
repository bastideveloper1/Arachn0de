package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class SecurityVolumeBackupTest {
    @Test fun hundredPeopleAndFiveHundredTechnologiesKeepImagesAndRelationshipsThroughEncryptedBackup()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val sourceRoot=File(app.filesDir,"volume-source-${UUID.randomUUID()}").apply { mkdir() }
        val legacy=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir():File=File(sourceRoot,"files").apply { mkdirs() }
            override fun getCacheDir():File=File(sourceRoot,"cache").apply { mkdirs() }
        }
        val targetRoot=File(app.filesDir,"security-v1/stores/${UUID.randomUUID()}").apply { mkdirs() }
        val access=SecureFiles.Session(UUID.randomUUID(),targetRoot,VaultCrypto.randomKey());SecureFiles.register(access)
        val targetContext=VaultContext(app,access)
        // In-memory Room tests the actual repository and file pipeline. Native SQLCipher opening
        // requires Android runtime validation and is not simulated as proof of page encryption.
        val source=Room.inMemoryDatabaseBuilder(legacy,Arachn0deDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(targetContext,Arachn0deDatabase::class.java).build();targetContext.database=target
        try {
            val original=BackupFixture.complete();val png=BackupFixture.png()
            val people=(0 until 100).map { PersonEntity("volume-person-$it","Persona $it","${UUID.randomUUID()}.png") }
            val tech=(0 until 500).map { TechnologyEntity("volume-tech-$it","Tecnología $it","${UUID.randomUUID()}.png") }
            val data=original.copy(persons=original.persons+people,avatars=original.avatars+people.associate { it.avatarFile!! to png },technologies=tech,
                technologyIcons=tech.associate { it.iconFile!! to png },nodeTechnologies=tech.mapIndexed { i,t->NodeTechnologyEntity("task",t.id,i) },
                privatePreferences=listOf(PrivatePreferenceEntity("appearance_preferences",EncryptedPreferences.encode(mapOf("theme" to "DeepMoss","text_size" to "Large")))))
            val sourceRepo=BackupRepository(source,legacy,BackupAvatarFiles(legacy,BackupFixture::syncDirectory));sourceRepo.restore(data)
            val plaintext=sourceRepo.create();val password="respaldo portable ñ".toCharArray()
            val encrypted=ByteArrayOutputStream();plaintext.inputStream().use { EncryptedBackup.write(it,encrypted,password) }
            assertFalse(String(encrypted.toByteArray(),Charsets.ISO_8859_1).contains("Persona 99"))
            val targetRepo=BackupRepository(target,targetContext,BackupAvatarFiles(targetContext,BackupFixture::syncDirectory))
            val inspected=targetRepo.inspect(ByteArrayInputStream(encrypted.toByteArray()),password)
            val cached=targetContext.getSharedPreferences("appearance_preferences",0)
            assertTrue(cached.edit().putString("theme","Minimalist").commit())
            var refreshed=false
            cached.registerOnSharedPreferenceChangeListener {_,key->if(key=="theme") refreshed=true}
            targetRepo.restore(inspected);targetRepo.discard(inspected)
            assertFalse(inspected.inspectionDirectory!!.exists())
            assertEquals("DeepMoss",cached.getString("theme",null));assertTrue(refreshed)
            assertEquals(103,target.backupDao().persons().size)
            assertEquals(500,target.technologyDao().catalog().size)
            assertEquals(500,target.technologyDao().nodes().size)
            val restored=targetRepo.snapshot()
            assertEquals(data.persons.map { it.id }.toSet(),restored.persons.map { it.id }.toSet())
            assertEquals(data.nodes.associateBy { it.id },restored.nodes.associateBy { it.id })
            assertEquals(data.assignments.toSet(),restored.assignments.toSet())
            assertEquals(data.privatePreferences,restored.privatePreferences)
            for(person in restored.persons.filter { it.id.startsWith("volume-") }) {
                val file=File(targetContext.filesDir,"avatars/${person.avatarFile}")
                assertArrayEquals(png,SecureFiles.read(file));assertFalse(png.contentEquals(file.readBytes()))
            }
            for(technology in restored.technologies) assertArrayEquals(png,SecureFiles.read(File(targetContext.filesDir,"technology-icons/${technology.iconFile}")))
            val before=restored.nodes
            assertTrue(runCatching { targetRepo.inspect(ByteArrayInputStream(encrypted.toByteArray()),"incorrecta".toCharArray()) }.isFailure)
            assertEquals(before,targetRepo.snapshot().nodes)
            assertEquals("DeepMoss",EncryptedPreferences.decode(target.privatePreferenceDao().all().single().payload)["theme"])
            password.fill('\u0000')
        } finally { source.close();target.close();targetContext.clearMemory();SecureFiles.revoke(access);sourceRoot.deleteRecursively();targetRoot.deleteRecursively() }
    }
}
