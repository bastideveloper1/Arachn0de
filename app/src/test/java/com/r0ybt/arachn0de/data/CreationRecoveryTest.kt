package com.r0ybt.arachn0de.data

import android.os.Bundle
import android.os.Parcel
import androidx.compose.runtime.saveable.SaverScope
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.OperationState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CreationRecoveryTest {
    private lateinit var db: Arachn0deDatabase
    @Before fun setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(RuntimeEnvironment.getApplication())
    }
    @After fun close() { db.close() }

    /** Cross a Parcel boundary: the restored draft cannot retain the old in-memory object. */
    private fun restore(draft: EditorDraft): EditorDraft {
        val saved = with(EditorDraft.Saver) { SaverScope { true }.save(draft) }!!
        @Suppress("UNCHECKED_CAST")
        val bundle = Bundle().apply { putStringArrayList("draft", ArrayList(saved as List<String>)) }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            val input = Parcel.obtain()
            try {
                input.unmarshall(bytes, 0, bytes.size)
                input.setDataPosition(0)
                val values = input.readBundle(javaClass.classLoader)!!.getStringArrayList("draft")!!
                return EditorDraft.Saver.restore(values)!!
            } finally { input.recycle() }
        } finally { parcel.recycle() }
    }

    @Test fun processStateContainsOnlyDraftAndRestoresUnsubmitted() = runBlocking {
        val draft = EditorDraft(null, "parent", "Texto sin guardar", "Descripción")
        val restored = restore(draft)
        assertNotSame(draft, restored)
        assertEquals(draft.creationId, restored.creationId)
        assertEquals(draft.parentId, restored.parentId)
        assertEquals(draft.title, restored.title)
        assertEquals(draft.description, restored.description)
        assertFalse(OperationState(this).busy)
        assertTrue(db.projectDao().observeAll().first().isEmpty())
    }

    @Test fun lostSuccessCallbackAndDatabaseReopenDoNotDuplicateProject() = runBlocking {
        val draft = EditorDraft(null, null, "Proyecto", "detalle")
        val state = OperationState(this)
        val committed = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = OperationState(scope)
        var success = false
        pending.submit("failure", {
            ProjectRepository(db.projectDao()).createProject(draft.title, draft.description, draft.creationId)
            committed.complete(Unit)
            awaitCancellation()
        }, { success = true })
        committed.await()
        scope.cancel()
        assertFalse(success)
        db.close()
        db = Arachn0deDatabase.create(RuntimeEnvironment.getApplication())
        val restored = restore(draft)
        val repository = ProjectRepository(db.projectDao())
        val before = repository.getProject(restored.creationId)
        val result = repository.createProject(restored.title, restored.description, restored.creationId)
        assertEquals(before, result)
        assertEquals(1, db.projectDao().observeAll().first().size)
        assertFalse(state.busy)
    }

    @Test fun concurrentRetriesCreateOneProjectAndOneChildWithoutChangingOrder() = runBlocking {
        val projects = ProjectRepository(db.projectDao())
        val projectDraft = EditorDraft(null, null, "Proyecto", "")
        coroutineScope {
            repeat(8) { launch(Dispatchers.Default) { projects.createProject("Proyecto", creationId = projectDraft.creationId) } }
        }
        val nodes = NodeRepository(db)
        val parent = nodes.createNode(projectDraft.creationId, null, "Padre")
        val draft = EditorDraft(null, parent.id, "Hijo", "")
        coroutineScope {
            repeat(8) { launch(Dispatchers.Default) { nodes.createNode(projectDraft.creationId, parent.id, "Hijo", creationId = draft.creationId) } }
        }
        assertEquals(1, db.projectDao().observeAll().first().size)
        val child = nodes.getNode(draft.creationId)!!
        nodes.setCompleted(child.id, true)
        val completed = nodes.getNode(child.id)
        val restored = restore(draft)
        assertEquals(completed, nodes.createNode(projectDraft.creationId, restored.parentId, restored.title, creationId = restored.creationId))
        assertEquals(2, nodes.getProjectNodes(projectDraft.creationId).size)
        assertEquals(0, child.position)
    }

    @Test fun retryWithChangedContentRejectsWithoutOverwritingConfirmedData() = runBlocking {
        val projects = ProjectRepository(db.projectDao())
        val project = projects.createProject("Confirmado", creationId = "project")
        try {
            projects.createProject("Otro texto", creationId = project.id)
            fail("Must reject a different payload")
        } catch (_: IllegalStateException) { }
        val nodes = NodeRepository(db)
        val child = nodes.createNode(project.id, null, "Confirmado", creationId = "child")
        try {
            nodes.createNode(project.id, null, "Otro texto", creationId = child.id)
            fail("Must reject a different payload")
        } catch (_: IllegalStateException) { }
        assertEquals(project, projects.getProject(project.id))
        assertEquals(child, nodes.getNode(child.id))
    }
}
