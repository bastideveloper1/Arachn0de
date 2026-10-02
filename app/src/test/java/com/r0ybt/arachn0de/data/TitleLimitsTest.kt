package com.r0ybt.arachn0de.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TitleLimitsTest {
    @Test fun limitsProtectCreateAndEditWithoutLosingDescriptionsOrExistingContent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Arachn0deDatabase::class.java).build()
        try {
            val projects = ProjectRepository(db.projectDao())
            val nodes = NodeRepository(db)
            val project = projects.createProject("p".repeat(60))
            val node = nodes.createNode(project.id, null, "😀".repeat(100), "Detalle opcional")
            suspend fun rejected(action: suspend () -> Unit) {
                try { action(); fail("Expected title validation") } catch (_: IllegalArgumentException) { }
            }
            rejected { projects.createProject("p".repeat(61)) }
            rejected { projects.updateProject(project.id, "p".repeat(61), "") }
            rejected { nodes.createNode(project.id, null, "n".repeat(101)) }
            rejected { nodes.updateNode(node.id, "n".repeat(101), "No debe reemplazar") }
            assertEquals(project, projects.getProject(project.id))
            assertEquals(node, nodes.getNode(node.id))
            assertTrue(nodes.updateNode(node.id, "Breve", "Descripción editada"))
            assertEquals("Descripción editada", nodes.getNode(node.id)?.description)
            assertTrue(nodes.updateNode(node.id, "Breve", ""))
            assertEquals("", nodes.getNode(node.id)?.description)
        } finally { db.close() }
    }
}
