package com.r0ybt.arachn0de.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.*

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TechnologyBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: BackupRepository
    private val icon = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png"
    @Before fun setup() {
        context.deleteDatabase("arachn0de.db")
        listOf("avatars", "technology-icons", "backup-restore-journal.json", "backup-restore-journal.part", "technology-icon-journal.json", "technology-icon-journal.part").forEach { File(context.filesDir, it).deleteRecursively() }
        File(context.cacheDir, "backups").deleteRecursively()
        db = Arachn0deDatabase.create(context)
        repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private fun iconFile(name: String) = File(context.filesDir, "technology-icons/$name")
    private suspend fun seed(): BackupData {
        val data = BackupFixture.complete().copy(
            technologies = listOf(TechnologyEntity("python", "Python personalizada ñ", icon), TechnologyEntity("docker", "Docker", null), TechnologyEntity("unused", "Herramienta sin asignar", icon)),
            nodeTechnologies = listOf(NodeTechnologyEntity("root", "python"), NodeTechnologyEntity("inner", "python"), NodeTechnologyEntity("task", "python"), NodeTechnologyEntity("task", "docker")),
            projectTechnologies = listOf(ProjectTechnologyEntity("p", "python"), ProjectTechnologyEntity("p", "docker"), ProjectTechnologyEntity("q", "python")),
            technologyIcons = mapOf(icon to BackupFixture.png()),
        )
        data.avatars.forEach { (name, bytes) -> File(File(context.filesDir, "avatars").apply { mkdirs() }, name).writeBytes(bytes) }
        data.technologyIcons.forEach { (name, bytes) -> iconFile(name).apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
        db.withTransaction {
            data.projects.forEach { db.projectDao().insert(it) }; data.persons.forEach { db.personDao().insert(it) }
            data.validate().forEach { db.nodeDao().insert(it) }; db.personDao().assign(data.assignments)
            data.technologies.forEach { db.technologyDao().insert(it) }
            db.technologyDao().assignNodes(data.nodeTechnologies); db.technologyDao().assignProjects(data.projectTechnologies)
        }
        return repo.snapshot()
    }
    private fun assertTechnologyData(before: BackupData, after: BackupData) {
        assertEquals(before.nodeTechnologies.toSet(), after.nodeTechnologies.toSet()); assertEquals(before.projectTechnologies.toSet(), after.projectTechnologies.toSet())
        before.technologies.forEach { old ->
            val restored = after.technologies.single { it.id == old.id }
            assertEquals(old.copy(iconFile = restored.iconFile), restored)
            if (old.iconFile == null) assertNull(restored.iconFile)
            else assertArrayEquals(before.technologyIcons.getValue(old.iconFile), after.technologyIcons.getValue(restored.iconFile!!))
        }
        assertEquals(before.technologies.size, after.technologies.size); assertEquals(before.technologyIcons.size, after.technologyIcons.size)
    }
    @Test fun freshInstallationRestoresEntireCatalogIconsAndProjectLayerTaskLinks() = runBlocking {
        val before = seed()
        val external = File.createTempFile("portable-technology-backup-", ".arachnode")
        try {
            repo.create().copyTo(external, overwrite = true)
            db.close(); context.deleteDatabase("arachn0de.db")
            File(context.filesDir, "avatars").deleteRecursively(); File(context.filesDir, "technology-icons").deleteRecursively()
            db = Arachn0deDatabase.create(context); repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
            val candidate = repo.inspect(external.inputStream())
            repo.restore(candidate)
            val after = repo.snapshot()
            BackupFixture.assertData(before, after); assertTechnologyData(before, after)
            assertEquals(1, File(context.filesDir, "technology-icons").listFiles()!!.size)
            assertEquals(after.technologies.first { it.id == "python" }.iconFile, after.technologies.first { it.id == "unused" }.iconFile)
            assertTrue(after.technologyIcons.keys.all { iconFile(it).isFile })
            assertEquals(17, JSONObject(String(BackupJson.encode(after))).getInt("dataVersion"))
            repo.discard(candidate)
        } finally { external.delete() }
    }
    @Test fun v10AttachmentCapableBackupRestoresWithEmptyTechnologyDefaults() = runBlocking {
        seed()
        val payload = JSONObject(String(BackupJson.encode(BackupFixture.empty()))).apply {
            put("dataVersion", 10); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession"); remove("conversionRoots"); remove("conversionPeople"); remove("conversionTags"); remove("conversionEvents"); remove("conversionWorkStates"); remove("nodeSortPreferences"); remove("imageFiles"); remove("projectPhotos"); remove("projectPhotoImages"); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons")
        }.toString().toByteArray()
        val archive = ByteArrayOutputStream().also { BackupContainer.write(payload, it) }.toByteArray().also { bytes ->
            // v2 and v1 share the header; recompute the checksum after changing its version.
            bytes[13] = 2
            val digest = java.security.MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, 26); update(payload) }.digest()
            digest.copyInto(bytes, 26)
        }
        repo.restore(repo.inspect(archive.inputStream()))
        assertTrue(db.technologyDao().catalog().isEmpty()); assertTrue(db.technologyDao().nodes().isEmpty()); assertTrue(db.technologyDao().projects().isEmpty())
        assertTrue(File(context.filesDir, "technology-icons").listFiles().orEmpty().isEmpty())
    }
    @Test fun genuineV10BackupWithOriginalAttachmentRestoresWithoutTechnologies() = runBlocking {
        seed()
        val bytes = BackupFixture.png()
        val id = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
        val attachment = AttachmentFileEntity(id, "$id.png", "Original.png", "image/png", bytes.size.toLong(), 32, 32,
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, 1)
        val data = BackupFixture.empty().copy(projects = listOf(ProjectEntity("old", "Old", "", 0, 1, 2)),
            nodes = listOf(NodeEntity("old-task", "old", null, "Old task", com.r0ybt.arachn0de.domain.model.AttachmentReferences.token(id), false, 0, 1, 2)),
            attachmentFiles = listOf(attachment), nodeAttachments = listOf(NodeAttachmentEntity("old-task", id)))
        val payload = JSONObject(String(BackupJson.encode(data))).apply {
            put("dataVersion", 10); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession"); remove("conversionRoots"); remove("conversionPeople"); remove("conversionTags"); remove("conversionEvents"); remove("conversionWorkStates"); remove("nodeSortPreferences"); remove("imageFiles"); remove("projectPhotos"); remove("projectPhotoImages"); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons")
        }.toString().toByteArray()
        val metadata = ByteArrayOutputStream().also { BackupContainer.write(payload, it) }.toByteArray().also { archive ->
            archive[13] = 2
            java.security.MessageDigest.getInstance("SHA-256").apply { update(archive, 0, 26); update(payload) }.digest().copyInto(archive, 26)
        }
        val candidate = repo.inspect((metadata + bytes).inputStream())
        repo.restore(candidate)
        val after = repo.snapshot()
        assertTrue(after.technologies.isEmpty()); assertEquals(data.nodes, after.nodes)
        assertEquals(data.nodeAttachments, after.nodeAttachments)
        assertArrayEquals(bytes, after.attachmentContents.values.single().readBytes())
        assertTrue(File(context.filesDir, "technology-icons").listFiles().orEmpty().isEmpty())
        repo.discard(candidate)
    }
    @Test fun missingPrivateIconPreventsExportWithoutChangingAnyRows() = runBlocking {
        val before = seed(); iconFile(icon).delete()
        assertTrue(runCatching { repo.create() }.isFailure)
        assertEquals(before.technologies.sortedBy { it.id }, db.technologyDao().catalog())
        assertEquals(before.nodeTechnologies, db.technologyDao().nodes()); assertEquals(before.projectTechnologies, db.technologyDao().projects())
        assertTrue(File(context.cacheDir, "backups").listFiles().orEmpty().isEmpty())
    }
    @Test fun missingCorruptOrExtraIconsAndBrokenLinksFailBeforeMutation() = runBlocking {
        val before = seed()
        val variants = listOf(before.copy(technologyIcons = emptyMap()), before.copy(technologyIcons = mapOf(icon to before.technologyIcons.getValue(icon).copyOf(30))),
            before.copy(technologyIcons = before.technologyIcons + ("../outside.png" to BackupFixture.png())),
            before.copy(nodeTechnologies = listOf(NodeTechnologyEntity("missing", "python"))),
            before.copy(projectTechnologies = listOf(ProjectTechnologyEntity("p", "missing"))),
            before.copy(technologies = before.technologies + before.technologies.first()))
        variants.forEach { assertTrue(runCatching { repo.restore(it) }.isFailure) }
        assertEquals(before.technologies.sortedBy { it.id }, db.technologyDao().catalog())
        assertArrayEquals(before.technologyIcons.getValue(icon), iconFile(icon).readBytes())
        val bytes = repo.create().readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { repo.inspect(bytes.inputStream()) }.isFailure)
        assertEquals(before.technologies.sortedBy { it.id }, db.technologyDao().catalog())
    }
    @Test fun failureAfterTablesAreDeletedRollsBackCatalogAssociationsIconsAndOtherData() = runBlocking {
        val before = seed(); val candidate = repo.inspect(repo.create().inputStream())
        val unrelated = iconFile("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.png").apply { writeBytes(BackupFixture.png()) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_technology_restore BEFORE INSERT ON technologies BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { repo.restore(candidate) }.isFailure)
        val after = repo.snapshot()
        BackupFixture.assertData(before, after); assertTechnologyData(before, after)
        assertEquals(before.technologies, after.technologies); assertTrue(unrelated.exists())
        assertEquals(2, File(context.filesDir, "technology-icons").listFiles()!!.size)
        assertFalse(File(context.filesDir, "technology-icon-journal.json").exists())
    }
    @Test fun iconDurabilityFailureKeepsPreviousRowsAndFiles() = runBlocking {
        val before = seed()
        val failing = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory),
            technologyIcons = BackupAvatarFiles(context, "technology-icons", "technology-icon-journal") { directory ->
                if (directory.name == "technology-icons" && directory.listFiles().orEmpty().size > 1) throw IOException("Injected icon fsync failure")
                BackupFixture.syncDirectory(directory)
            })
        assertTrue(runCatching { failing.restore(before) }.isFailure)
        assertEquals(before.technologies, repo.snapshot().technologies)
        assertTrue(iconFile(icon).exists()); assertEquals(1, File(context.filesDir, "technology-icons").listFiles()!!.size)
        repo.recover(); assertFalse(File(context.filesDir, "technology-icon-journal.json").exists())
    }
    @Test fun interruptedIconJournalRecoveryKeepsOnlyCommittedReferencesAndUnrelatedFiles() = runBlocking {
        seed()
        val files = BackupAvatarFiles(context, "technology-icons", "technology-icon-journal", BackupFixture::syncDirectory)
        val orphan = "cccccccc-cccc-cccc-cccc-cccccccccccc.png"
        val unrelated = "dddddddd-dddd-dddd-dddd-dddddddddddd.png"
        files.write(orphan, BackupFixture.png()); files.write(unrelated, BackupFixture.png()); files.record(setOf(icon, orphan))
        repo.recover()
        assertTrue(iconFile(icon).exists()); assertFalse(iconFile(orphan).exists()); assertTrue(iconFile(unrelated).exists())
    }
    @Test fun avatarsAndTechnologyIconsShareExistingTotalLimit() {
        val bytes = BackupFixture.png()
        val icons = (0 until 9).associate { index -> "%08d-0000-0000-0000-000000000000.png".format(index) to bytes.copyOf(BackupLimits.AVATAR_BYTES) }
        val data = BackupFixture.empty().copy(technologies = icons.keys.mapIndexed { index, name -> TechnologyEntity("t$index", "Tool", name) }, technologyIcons = icons)
        assertTrue(runCatching { data.validate() }.exceptionOrNull()!!.message!!.contains("demasiado grandes"))
    }
}
