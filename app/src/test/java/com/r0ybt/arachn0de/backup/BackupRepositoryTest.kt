package com.r0ybt.arachn0de.backup

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: BackupRepository
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("arachn0de.db")
        File(context.filesDir, "avatars").deleteRecursively()
        File(context.filesDir, "backup-restore-journal.json").delete()
        File(context.filesDir, "backup-restore-journal.part").delete()
        File(context.cacheDir, "backups").deleteRecursively()
        db = Arachn0deDatabase.create(context)
        repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private suspend fun seed(data: BackupData) {
        data.avatars.forEach { (name, png) -> File(File(context.filesDir, "avatars").apply { mkdirs() }, name).writeBytes(png) }
        db.withTransaction {
            data.projects.forEach { db.projectDao().insert(it) }
            data.persons.forEach { db.personDao().insert(it) }
            data.validate().forEach { db.nodeDao().insert(it) }
            db.personDao().assign(data.assignments)
        }
    }
    @Test fun stateAExportThenStateBThenRestoreReturnsExactlyToAIncludingAvatarsAndFlows() = runBlocking {
        seed(BackupFixture.complete())
        val stateA = repo.snapshot()
        val file = repo.create()
        BackupFixture.assertData(stateA, repo.snapshot()) // Creating a backup performs no normalization/writes.
        val input = repo.inspect(file.inputStream())
        val nodeRepo = NodeRepository(db)
        nodeRepo.updateLeaf("bill", "Modificado", "B", null, null, com.r0ybt.arachn0de.domain.model.Obligation(1, "CLP"))
        nodeRepo.moveNode("note", null)
        nodeRepo.setCompleted("task", true)
        ProjectRepository(db.projectDao()).createProject("Estado B")
        db.personDao().update("r", "Nombre B", null)
        nodeRepo.createNode("p", null, "Extra B")
        assertNotEquals(stateA.nodes, repo.snapshot().nodes)
        repo.restore(input)
        val restored = repo.snapshot()
        BackupFixture.assertData(stateA, restored)
        assertEquals(27, db.openHelper.readableDatabase.version)
        assertEquals(stateA.nodes.map { it.id }.toSet(), nodeRepo.observeAllState().first().nodesById.keys)
        assertEquals(restored.persons.first { it.id == "r" }.avatarFile, restored.persons.first { it.id == "s" }.avatarFile)
        assertFalse(File(context.filesDir, "avatars/${BackupFixture.avatar}").exists())
        assertEquals(1, File(context.filesDir, "avatars").listFiles()!!.size)
        assertFalse(File(context.filesDir, "backup-restore-journal.json").exists())
        file.delete()
        Unit
    }
    @Test fun uuidIdentitiesRemainExactAfterRestore() = runBlocking {
        val project = java.util.UUID.randomUUID().toString()
        val root = java.util.UUID.randomUUID().toString()
        val leaf = java.util.UUID.randomUUID().toString()
        val person = java.util.UUID.randomUUID().toString()
        val data = BackupFixture.empty().copy(
            projects = listOf(ProjectEntity(project, "UUID", "", 9, 1, 2)),
            nodes = listOf(NodeEntity(root, project, null, "Raíz", "", false, 7, 3, 4, purpose="LAYER"), NodeEntity(leaf, project, root, "Hoja", "", true, 8, 5, 6)),
            persons = listOf(PersonEntity(person, "Persona", null)), assignments = listOf(NodePersonEntity(leaf, person)),
        )
        val validated = repo.inspect(java.io.ByteArrayInputStream(BackupFixture.archive(data)))
        repo.restore(validated)
        BackupFixture.assertData(data, repo.snapshot())
    }

    @Test fun emptyBackupReplacesAllTablesAndRemovesOldReferencedFiles() = runBlocking {
        seed(BackupFixture.complete())
        repo.restore(BackupFixture.empty())
        BackupFixture.assertData(BackupFixture.empty(), repo.snapshot())
        assertTrue(File(context.filesDir, "avatars").listFiles().orEmpty().isEmpty())
    }
    @Test fun invalidBackupAndReadFailureLeaveCurrentDataAndFilesUntouched() = runBlocking {
        seed(BackupFixture.complete())
        val before = repo.snapshot()
        val filenames = File(context.filesDir, "avatars").listFiles()!!.map { it.name }.toSet()
        assertTrue(runCatching { repo.restore(before.copy(assignments = before.assignments + NodePersonEntity("missing", "r"))) }.isFailure)
        assertTrue(runCatching { repo.inspect(java.io.ByteArrayInputStream(byteArrayOf(1, 2))) }.isFailure)
        BackupFixture.assertData(before, repo.snapshot())
        assertEquals(before.persons, repo.snapshot().persons)
        assertEquals(filenames, File(context.filesDir, "avatars").listFiles()!!.map { it.name }.toSet())
    }
    @Test fun failureAfterDeletingOldTablesRollsBackAllRowsAndPreservesExactOldAvatars() = runBlocking {
        seed(BackupFixture.complete())
        val before = repo.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER injected_restore_failure BEFORE INSERT ON nodes WHEN NEW.title = 'Rollback' BEGIN SELECT RAISE(ABORT, 'Injected failure'); END")
        val incoming = before.copy(nodes = before.nodes.map { if (it.id == "bill") it.copy(title = "Rollback") else it })
        assertTrue(runCatching { repo.restore(incoming) }.isFailure)
        BackupFixture.assertData(before, repo.snapshot())
        assertEquals(before.persons, repo.snapshot().persons)
        assertEquals(listOf(BackupFixture.avatar), File(context.filesDir, "avatars").listFiles()!!.map { it.name })
        assertFalse(File(context.filesDir, "backup-restore-journal.json").exists())
    }
    @Test fun journalRecoveryKeepsCommittedReferencesAndRemovesOnlyRestoreOrphans() = runBlocking {
        seed(BackupFixture.complete())
        val files = BackupAvatarFiles(context, BackupFixture::syncDirectory)
        val orphan = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png"
        val unrelated = File(context.filesDir, "avatars/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.png").apply { writeBytes(BackupFixture.png()) }
        files.record(setOf(BackupFixture.avatar, orphan))
        files.write(orphan, BackupFixture.png()); files.finishStaging()
        repo.recover()
        assertFalse(File(context.filesDir, "avatars/$orphan").exists())
        assertTrue(File(context.filesDir, "avatars/${BackupFixture.avatar}").exists())
        assertTrue(unrelated.exists()) // A Person editor's uncommitted avatar is not ours to delete.
        BackupFixture.assertData(BackupFixture.complete(), repo.snapshot())
    }
    @Test fun concurrentTableWritesCannotProduceASplitSnapshot() = runBlocking {
        seed(BackupFixture.empty().copy(projects = listOf(ProjectEntity("p", "Inicial", "", 0, 1, 1)),
            nodes = listOf(NodeEntity("n", "p", null, "Inicial", "", false, 0, 1, 1)), persons = listOf(PersonEntity("r", "Inicial", null))))
        val writer = launch(Dispatchers.IO) {
            repeat(20) { index -> db.withTransaction {
                db.nodeDao().updateContent("n", "Estado $index", "", 10)
                db.personDao().update("r", "Estado $index", null)
            } }
        }
        repeat(20) {
            val snapshot = repo.snapshot()
            assertEquals(snapshot.nodes.single().title, snapshot.persons.single().name)
        }
        writer.join()
    }

    @Test fun unavailableAvatarPreventsExportAndDoesNotModifyAnyRows() = runBlocking {
        seed(BackupFixture.complete())
        val rows = db.backupDao().persons()
        File(context.filesDir, "avatars/${BackupFixture.avatar}").delete()
        assertTrue(runCatching { repo.create() }.isFailure)
        assertEquals(rows, db.backupDao().persons())
        assertEquals(7, db.backupDao().nodes().size)
        assertTrue(File(context.cacheDir, "backups").listFiles().orEmpty().isEmpty())
    }
    @Test fun deeplyNestedStateRestoresAndReplacesWithoutRecursiveCascadeFailure() = runBlocking {
        val nodes = (0 until 1100).map { NodeEntity("n$it", "p", if (it == 0) null else "n${it - 1}", "Nodo", "", false, it, it.toLong(), it.toLong(),purpose=if(it<1099) "LAYER" else "ACTION") }
        val data = BackupFixture.empty().copy(projects = listOf(ProjectEntity("p", "Profundo", "", 0, 1, 2)), nodes = nodes)
        repo.restore(data)
        BackupFixture.assertData(data, repo.snapshot())
        repo.restore(BackupFixture.empty())
        assertTrue(repo.snapshot().nodes.isEmpty())
    }
    @Test fun failedAvatarDurabilityLeavesOriginalDatabaseAndFilesUntouched() = runBlocking {
        seed(BackupFixture.complete())
        val before = repo.snapshot()
        var syncs = 0
        val failing = BackupRepository(db, context, BackupAvatarFiles(context) { directory ->
            if (++syncs == 4) throw java.io.IOException("Injected staging sync failure")
            BackupFixture.syncDirectory(directory)
        })
        assertTrue(runCatching { failing.restore(before) }.isFailure)
        BackupFixture.assertData(before, repo.snapshot())
        assertEquals(before.persons, repo.snapshot().persons)
        assertEquals(listOf(BackupFixture.avatar), File(context.filesDir, "avatars").listFiles()!!.map { it.name })
    }
}
