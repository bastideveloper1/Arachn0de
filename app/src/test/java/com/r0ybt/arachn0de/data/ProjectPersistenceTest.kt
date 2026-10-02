package com.r0ybt.arachn0de.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.ProjectEntity
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProjectPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: Arachn0deDatabase
    private lateinit var repository: ProjectRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("arachn0de.db")
    }

    @Test
    fun createAndReadPreservesFieldsAndAllowsDuplicateNames() = runBlocking {
        assertTrue(repository.observeProjects().first().isEmpty())
        val project = repository.createProject("  Arachn0de  ", "Organización local 🕷\nSin cuenta")
        val another = repository.createProject("Arachn0de")

        assertEquals(project, repository.getProject(project.id))
        assertEquals("Arachn0de", project.name)
        assertEquals("Organización local 🕷\nSin cuenta", project.description)
        assertEquals(now, project.createdAt)
        assertEquals(now, project.updatedAt)
        assertEquals(project.id, UUID.fromString(project.id).toString())
        assertNotEquals(project.id, another.id)
        assertEquals("", another.description)
        assertEquals(2, repository.observeProjects().first().size)
    }

    @Test
    fun updatePreservesIdentityAndCreationTimeWithoutChangingOtherProjects() = runBlocking {
        val project = repository.createProject("Original", "Before")
        val other = repository.createProject("Other")
        now = 2_000L

        assertTrue(repository.updateProject(project.id, "  Edited  ", "After"))
        assertEquals(
            project.copy(name = "Edited", description = "After", updatedAt = now),
            repository.getProject(project.id),
        )
        assertEquals(other, repository.getProject(other.id))
    }

    @Test
    fun deleteOnlyRemovesTheRequestedProjectAndMissingOperationsAreExplicit() = runBlocking {
        val project = repository.createProject("Delete")
        val other = repository.createProject("Keep")

        assertTrue(repository.deleteProject(project.id))
        assertNull(repository.getProject(project.id))
        assertFalse(repository.deleteProject(project.id))
        assertFalse(repository.updateProject(project.id, "Missing", ""))
        assertEquals(listOf(other.copy(position = 0)), repository.observeProjects().first())
    }

    @Test
    fun blankNamesAreRejectedWithoutWritingChanges() = runBlocking {
        val project = repository.createProject("Valid")
        for (name in listOf("", "  ", "\t\n")) {
            expectInvalidName { repository.createProject(name) }
            expectInvalidName { repository.updateProject(project.id, name, "Changed") }
        }
        assertEquals(listOf(project), repository.observeProjects().first())
    }

    @Test
    fun orderingUsesCreationTimeAndIdToBreakTies() = runBlocking {
        val dao = database.projectDao()
        dao.insert(ProjectEntity("z", "First alphabetically", "", 100, 100, 0))
        dao.insert(ProjectEntity("a", "Last alphabetically", "", 100, 100, 1))
        dao.insert(ProjectEntity("older", "Older", "", 50, 50, 2))

        assertEquals(listOf("older", "a", "z"), repository.observeProjects().first().map { it.id })
        repository.updateProject("z", "Renamed", "")
        assertEquals(listOf("older", "a", "z"), repository.observeProjects().first().map { it.id })
    }

    @Test
    fun projectOrderUsesPositionAndPersistsAcrossReopen() = runBlocking {
        val first = repository.createProject("First")
        val second = repository.createProject("Second")
        val third = repository.createProject("Third")

        assertEquals(listOf(first.id, second.id, third.id), repository.observeProjects().first().map { it.id })
        assertEquals(listOf(0, 1, 2), repository.observeProjects().first().map { it.position })

        assertTrue(repository.reorderProject(second.id, true))
        assertEquals(listOf(second.id, first.id, third.id), repository.observeProjects().first().map { it.id })
        assertEquals(listOf(0, 1, 2), repository.observeProjects().first().map { it.position })

        assertTrue(repository.reorderProject(second.id, false))
        assertEquals(listOf(first.id, second.id, third.id), repository.observeProjects().first().map { it.id })

        assertTrue(repository.reorderProjectTo(third.id, first.id))
        assertEquals(listOf(third.id, first.id, second.id), repository.observeProjects().first().map { it.id })

        assertTrue(repository.deleteProject(second.id))
        assertEquals(listOf(0, 1), repository.observeProjects().first().map { it.position })

        database.close()
        openDatabase()
        assertEquals(listOf(third.id, first.id), repository.observeProjects().first().map { it.id })
    }

    @Test
    fun migration3To4PreservesLegacyOrderAndAssignsSequentialPositions() = runBlocking {
        val legacy = context.openOrCreateDatabase("arachn0de.db", Context.MODE_PRIVATE, null)
        legacy.setVersion(3)
        legacy.execSQL("DROP TABLE IF EXISTS nodes")
        legacy.execSQL("DROP TABLE IF EXISTS projects")
        legacy.execSQL(
            "CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
        )
        legacy.execSQL(
            "CREATE TABLE nodes (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, parentId TEXT, title TEXT NOT NULL, description TEXT NOT NULL, isCompleted INTEGER NOT NULL, position INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE ON UPDATE NO ACTION, FOREIGN KEY(projectId, parentId) REFERENCES nodes(projectId, id) ON DELETE CASCADE ON UPDATE NO ACTION)",
        )
        legacy.execSQL("CREATE UNIQUE INDEX index_nodes_projectId_id ON nodes(projectId, id)")
        legacy.execSQL("CREATE INDEX index_nodes_projectId_parentId ON nodes(projectId, parentId)")
        legacy.execSQL("INSERT INTO projects VALUES ('c', 'C', '', 300, 300)")
        legacy.execSQL("INSERT INTO projects VALUES ('a', 'A', '', 100, 100)")
        legacy.execSQL("INSERT INTO projects VALUES ('b', 'B', '', 200, 200)")
        legacy.close()

        database.close()
        database = Arachn0deDatabase.create(context)
        repository = ProjectRepository(database.projectDao()) { now }

        val ordered = repository.observeProjects().first().map { it.id }
        assertEquals(listOf("a", "b", "c"), ordered)
        assertEquals(listOf(0, 1, 2), repository.observeProjects().first().map { it.position })
    }

    @Test
    fun duplicateIdentityFailsWithoutReplacingExistingData() = runBlocking {
        val dao = database.projectDao()
        val original = ProjectEntity("same-id", "Original", "", 100, 100)
        dao.insert(original)
        try {
            dao.insert(original.copy(name = "Replacement"))
            fail("Expected a primary key conflict")
        } catch (_: SQLiteConstraintException) {
            // Conflicts must not silently replace a project.
        }
        assertEquals(original, dao.getById(original.id))
    }

    @Test
    fun activeObserverReceivesCreateUpdateAndDelete() = runBlocking {
        withTimeout(10_000) {
            val emissions = Channel<List<Project>>(Channel.UNLIMITED)
            val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.observeProjects().collect { emissions.send(it) }
            }
            try {
                assertTrue(emissions.receive().isEmpty())
                val project = repository.createProject("Observed")
                assertEquals(listOf(project), emissions.receive())
                now = 2_000L
                repository.updateProject(project.id, "Updated", "")
                assertEquals(
                    listOf(project.copy(name = "Updated", updatedAt = now)),
                    emissions.receive(),
                )
                repository.deleteProject(project.id)
                assertTrue(emissions.receive().isEmpty())
            } finally {
                observer.cancel()
                emissions.close()
            }
        }
    }

    @Test
    fun fileDatabaseRetainsCreatesUpdatesAndDeletesAfterClosingAndReopening() = runBlocking {
        val retained = repository.createProject("Retained", "Description")
        val edited = repository.createProject("Before")
        val deleted = repository.createProject("Deleted")
        now = 2_000L
        repository.updateProject(edited.id, "After", "Updated description")
        repository.deleteProject(deleted.id)
        val expected = repository.observeProjects().first()

        database.close()
        assertTrue(context.getDatabasePath("arachn0de.db").isFile)
        openDatabase()

        assertEquals(expected, repository.observeProjects().first())
        assertEquals(retained, repository.getProject(retained.id))
        assertEquals("After", repository.getProject(edited.id)?.name)
        assertNull(repository.getProject(deleted.id))
    }

    private fun openDatabase() {
        database = Arachn0deDatabase.create(context)
        repository = ProjectRepository(database.projectDao()) { now }
    }

    private suspend fun expectInvalidName(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected an invalid project name to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
