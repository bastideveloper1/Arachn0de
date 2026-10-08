package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.BackupRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AttachmentRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: Arachn0deDatabase
    private lateinit var store: AttachmentStore
    private lateinit var attachments: AttachmentRepository
    private lateinit var nodes: NodeRepository
    private lateinit var projects: ProjectRepository
    private lateinit var node: Node
    @Before fun setup() = runBlocking {
        context.deleteDatabase("arachn0de.db")
        File(context.filesDir, "attachments").deleteRecursively()
        db = Arachn0deDatabase.create(context)
        store = AttachmentStore(context, syncDirectory = com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory); attachments = AttachmentRepository(db, store)
        nodes = NodeRepository(db); projects = ProjectRepository(db.projectDao(), db)
        projects.createProject("Source", creationId = "source")
        projects.createProject("Target", creationId = "target")
        node = nodes.createNode("source", null, "Task")
    }
    @After fun close() { db.close() }
    private fun image(format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
        val input = File(context.cacheDir, "same-name.png")
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        input.outputStream().use { assertTrue(bitmap.compress(format, 90, it)) }; bitmap.recycle()
        return input
    }
    private suspend fun imported() = attachments.import(Uri.fromFile(image()), "draft")
    private fun marker(file: AttachmentFileEntity) = "[[arachnode:image:${file.id}]]"

    @Test fun pngKeepsBytesDimensionsAndSurvivesOriginalRemoval() = runBlocking {
        val input = image(); val bytes = input.readBytes()
        val file = attachments.import(Uri.fromFile(input), "draft")
        input.delete()
        assertArrayEquals(bytes, store.file(file.storageName).readBytes())
        assertEquals(800, file.width); assertEquals(600, file.height)
        assertEquals(bytes.size.toLong(), file.byteSize); assertEquals("image/png", file.mimeType)
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, file.sha256)
    }
    @Test fun jpegDetectedFromContentAndOriginalBytesPreserved() = runBlocking {
        val input = image(Bitmap.CompressFormat.JPEG); val bytes = input.readBytes()
        val file = attachments.import(Uri.fromFile(input), "draft")
        assertEquals("image/jpeg", file.mimeType); assertTrue(file.storageName.endsWith(".jpg"))
        assertArrayEquals(bytes, store.file(file.storageName).readBytes())
    }
    @Test fun invalidContentLeavesNoRowsOrStagedFiles() = runBlocking {
        val input = File(context.cacheDir, "fake.png").apply { writeText("not an image") }
        assertTrue(runCatching { attachments.import(Uri.fromFile(input), "draft") }.isFailure)
        assertTrue(db.attachmentDao().files().isEmpty())
        assertTrue(File(context.filesDir, "attachments/staging").listFiles().orEmpty().isEmpty())
    }
    @Test fun duplicateNamesHaveIndependentIdentities() = runBlocking {
        val first = imported(); val second = imported()
        assertEquals(first.originalName, second.originalName)
        assertNotEquals(first.id, second.id); assertNotEquals(first.storageName, second.storageName)
    }
    @Test fun sharedFileSurvivesOneDetachAndLastOwnerDeletesIt() = runBlocking {
        val file = imported()
        attachments.associateNode(node.id, file.id, "Before ${marker(file)} after")
        attachments.associateProject("source", file.id, marker(file))
        assertEquals(2, db.attachmentDao().references(file.id))
        attachments.detachNode(node.id, file.id)
        assertEquals("Before  after", db.nodeDao().getById(node.id)!!.description)
        assertTrue(store.file(file.storageName).exists())
        attachments.detachProject("source", file.id)
        assertNull(db.attachmentDao().get(file.id)); assertFalse(store.file(file.storageName).exists())
    }
    @Test fun failedDeletionRemainsPendingAndRetries() = runBlocking {
        val file = imported(); attachments.associateNode(node.id, file.id)
        val failing = AttachmentRepository(db, object : AttachmentStore(context, syncDirectory = com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory) { override fun delete(name: String) = false })
        failing.detachNode(node.id, file.id)
        assertEquals("DELETE_PENDING", db.attachmentDao().get(file.id)!!.lifecycleState)
        assertTrue(runCatching { attachments.associateNode(node.id, file.id) }.isFailure)
        attachments.cleanup(); assertNull(db.attachmentDao().get(file.id))
    }
    @Test fun recoveryPreservesPendingDraftAndRemovesInterruptedImport() = runBlocking {
        val file = imported()
        val root = File(context.filesDir, "attachments")
        val orphan = "00000000-0000-0000-0000-000000000001"
        File(root, "$orphan.png").writeBytes(byteArrayOf(1))
        File(root, "staging/$orphan.lease").writeText("interrupted")
        File(root, "staging/$orphan.part").writeBytes(byteArrayOf(2))
        val restarted = AttachmentRepository(db, AttachmentStore(context, syncDirectory = com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        restarted.cleanup()
        assertTrue(store.file(file.storageName).exists())
        assertEquals(listOf(file), restarted.pendingImports("draft"))
        assertFalse(File(root, "$orphan.png").exists()); assertFalse(File(root, "staging/$orphan.part").exists())
        restarted.discardDraft("draft"); assertNull(db.attachmentDao().get(file.id))
    }
    @Test fun deletingProjectPreservesFileUsedByOtherProjectThenCleansLast() = runBlocking {
        val file = imported()
        attachments.associateNode(node.id, file.id); attachments.associateProject("target", file.id)
        projects.deleteProject("source"); attachments.cleanup()
        assertTrue(store.file(file.storageName).exists())
        projects.deleteProject("target"); attachments.cleanup()
        assertFalse(store.file(file.storageName).exists())
    }
    @Test fun deletingLayerCleansDescendantAttachments() = runBlocking {
        val layer = nodes.createNode("source", null, "Layer", purpose = NodePurpose.LAYER)
        val child = nodes.createNode("source", layer.id, "Child")
        val file = imported(); attachments.associateNode(child.id, file.id)
        nodes.deleteNode(layer.id); attachments.cleanup()
        assertNull(db.attachmentDao().get(file.id))
    }
    @Test fun projectNestingPreservesBothKindsOfRelationships() = runBlocking {
        val file = imported()
        attachments.associateNode(node.id, file.id, marker(file))
        attachments.associateProject("source", file.id, marker(file))
        val layer = projects.moveInside("source", "target", null)
        attachments.cleanup()
        assertEquals(setOf(file.id), db.attachmentDao().forNode(node.id).map { it.attachmentId }.toSet())
        assertEquals(setOf(file.id), db.attachmentDao().forNode(layer).map { it.attachmentId }.toSet())
        assertEquals(marker(file), db.nodeDao().getById(layer)!!.description)
        assertEquals(2, db.attachmentDao().references(file.id))
    }
    @Test fun groupDescriptionCreatesRelationsAtomicallyAndRejectsMissingFiles() = runBlocking {
        val other = nodes.createNode("source", null, "Other")
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET creationGroupId='group' WHERE id IN (?, ?)", arrayOf(node.id, other.id))
        val file = imported(); val text = marker(file)
        attachments.associateNode(node.id, file.id)
        nodes.updateGroup(node.id, SharedNodePatch(description = text), setOf(node.id, other.id), node.title, text, null, null, false, null, false, emptySet(), Priority.NONE, emptySet())
        assertEquals(2, db.attachmentDao().references(file.id))
        val missing = "[[arachnode:image:00000000-0000-0000-0000-000000000009]]"
        assertTrue(runCatching { nodes.updateGroup(node.id, SharedNodePatch(description = missing), setOf(node.id, other.id), node.title, missing, null, null, false, null, false, emptySet(), Priority.NONE, emptySet()) }.isFailure)
        assertEquals(text, db.nodeDao().getById(other.id)!!.description)
    }
    @Test fun laterAttachmentPreventsUndoCreation() = runBlocking {
        val token = nodes.captureCreation(listOf(node.id))
        attachments.associateNode(node.id, imported().id)
        assertFalse(nodes.undoCreation(token)); assertNotNull(nodes.getNode(node.id))
    }
    @Test fun descriptionAssociationRollsBackOnMissingMarker() = runBlocking {
        val file = imported()
        assertTrue(runCatching { attachments.associateNode(node.id, file.id, "[[arachnode:image:00000000-0000-0000-0000-000000000009]]") }.isFailure)
        assertTrue(db.attachmentDao().forNode(node.id).isEmpty())
        assertEquals("", db.nodeDao().getById(node.id)!!.description)
        assertTrue(store.reserved(file.id))
    }
    @Test fun backupCapturesConfirmedAttachmentsWithoutModifyingThem() = runBlocking {
        val backup = BackupRepository(db, context)
        val file = imported(); attachments.associateNode(node.id, file.id)
        val data = backup.snapshot()
        assertEquals(listOf(file), data.attachmentFiles)
        assertEquals(listOf(NodeAttachmentEntity(node.id, file.id)), data.nodeAttachments)
        assertEquals(1, db.attachmentDao().references(file.id)); assertTrue(store.file(file.storageName).exists())
    }
    @Test fun markerParserPreservesUnknownAndEscapedText() {
        val id = "00000000-0000-0000-0000-000000000001"
        val valid = "[[arachnode:image:$id]]"
        val text = "unknown [[arachnode:image:bad]] \\$valid $valid"
        assertEquals(setOf(id), AttachmentReferences.ids(text))
        assertEquals("unknown [[arachnode:image:bad]] \\$valid ", AttachmentReferences.remove(text, id))
    }
    @Test fun unconfirmedMarkerCannotConsumeAnotherDraftImport() = runBlocking {
        val file = imported()
        assertTrue(runCatching { nodes.updateNode(node.id, node.title, marker(file)) }.isFailure)
        assertTrue(db.attachmentDao().forNode(node.id).isEmpty())
        assertEquals(listOf(file), attachments.pendingImports("draft"))
        attachments.associateNode(node.id, file.id, marker(file))
        assertTrue(attachments.pendingImports("draft").isEmpty())
    }
    @Test fun concurrentCleanupAndConfirmationKeepFileAndRelationship() = runBlocking {
        val file = imported()
        kotlinx.coroutines.coroutineScope {
            val clean = async(kotlinx.coroutines.Dispatchers.IO) { repeat(5) { attachments.cleanup() } }
            val attach = async(kotlinx.coroutines.Dispatchers.IO) { attachments.associateNode(node.id, file.id, marker(file)) }
            clean.await(); attach.await()
        }
        assertEquals(1, db.attachmentDao().references(file.id))
        assertTrue(store.file(file.storageName).exists())
    }
    @Test fun sqlCannotAssociateMissingFilesOrDeleteReferencedMetadata() = runBlocking {
        val file = imported(); attachments.associateNode(node.id, file.id)
        assertTrue(runCatching { db.attachmentDao().delete(file.id) }.isFailure)
        assertTrue(runCatching { db.attachmentDao().attachProject(listOf(ProjectAttachmentEntity("target", "missing"))) }.isFailure)
    }

    @Test fun undoSnapshotIncludesAttachmentsPresentAtCapture() = runBlocking {
        val file = imported(); attachments.associateNode(node.id, file.id)
        val token = nodes.captureCreation(listOf(node.id))
        assertEquals(setOf(file.id), token.attachments[node.id])
        assertTrue(nodes.undoCreation(token)); attachments.cleanup()
        assertFalse(store.file(file.storageName).exists())
    }

    @Test fun newNodeDraftConfirmsMultipleImagesAndDescriptionAtomically() = runBlocking {
        val first = imported(); val second = imported()
        val description = "Before\n${marker(first)}\nBetween\n${marker(second)}\nAfter"
        attachments.saveNodeDraft("draft", listOf("created"), description, emptySet()) { clean ->
            nodes.createNode("source", null, "Created", clean, creationId = "created")
        }
        assertEquals(description, nodes.getNode("created")!!.description)
        assertEquals(setOf(first.id, second.id), db.attachmentDao().forNode("created").map { it.attachmentId }.toSet())
        assertTrue(attachments.pendingImports("draft").isEmpty())
    }
    @Test fun projectDraftSaveReopenAndRemovalPreserveAnotherOwner() = runBlocking {
        val file = imported(); val description = "Before ${marker(file)} After"
        attachments.saveProjectDraft("draft", "created-project", description, emptySet()) { clean ->
            projects.createProject("Created", clean, "created-project")
        }
        attachments.associateNode(node.id, file.id)
        assertEquals(description, projects.getProject("created-project")!!.description)
        attachments.saveProjectDraft("next-draft", "created-project", "Before  After", setOf(file.id)) { clean ->
            projects.updateProject("created-project", "Created", clean)
        }
        assertTrue(db.attachmentDao().forProject("created-project").isEmpty())
        assertEquals(1, db.attachmentDao().references(file.id)); assertTrue(store.file(file.storageName).exists())
    }
    @Test fun failedDraftSaveKeepsPendingImagesAndRollsBackOwnerCreation() = runBlocking {
        val file = imported()
        assertTrue(runCatching {
            attachments.saveNodeDraft("draft", listOf("failed"), marker(file), emptySet()) { clean ->
                nodes.createNode("source", null, "New", clean, creationId = "failed")
                error("Failed editor save")
            }
        }.isFailure)
        assertNull(nodes.getNode("failed")); assertEquals(listOf(file), attachments.pendingImports("draft"))
    }
    @Test fun removingMarkerWhileEditingRemovesRelationshipOnlyAtSave() = runBlocking {
        val file = imported(); attachments.associateNode(node.id, file.id, marker(file))
        assertEquals(1, db.attachmentDao().references(file.id))
        attachments.saveNodeDraft("edit", listOf(node.id), "Plain text", emptySet()) { clean ->
            nodes.updateNode(node.id, node.title, clean)
        }
        assertEquals("Plain text", nodes.getNode(node.id)!!.description)
        assertNull(db.attachmentDao().get(file.id))
    }

    @Test fun committedNewOwnerSaveCanBeRetriedWithoutDuplicatingOrLosingImages() = runBlocking {
        val file = imported(); val description = "Text ${marker(file)}"
        repeat(2) {
            attachments.saveNodeDraft("draft", listOf("stable-node"), description, emptySet()) { text ->
                nodes.createNode("source", null, "Stable", text, creationId = "stable-node")
            }
            attachments.saveProjectDraft("draft", "stable-project", description, emptySet()) { text ->
                projects.createProject("Stable project", text, "stable-project")
            }
        }
        assertEquals(description, nodes.getNode("stable-node")!!.description)
        assertEquals(description, projects.getProject("stable-project")!!.description)
        assertEquals(2, db.attachmentDao().references(file.id))
    }

    @Test fun labelsAndFormattingPersistPerUseWithoutChangingFileMetadata() = runBlocking {
        val first = imported(); val second = imported()
        val nodeText = "**Antes** ${AttachmentReferences.token(first.id, "Gráfico")} ${AttachmentReferences.token(second.id, "Gráfico")} __Después__"
        attachments.saveNodeDraft("draft", listOf(node.id), nodeText, emptySet()) { clean ->
            nodes.updateNode(node.id, node.title, clean)
        }
        val projectText = "*Otro contexto* ${AttachmentReferences.token(first.id, "Bug de interfaz")}"
        attachments.saveProjectDraft("project-edit", "source", projectText, emptySet()) { clean ->
            projects.updateProject("source", "Source", clean)
        }
        db.close(); db = Arachn0deDatabase.create(context)
        assertEquals(nodeText, db.nodeDao().getById(node.id)!!.description)
        assertEquals(projectText, db.projectDao().getById("source")!!.description)
        assertEquals(first, db.attachmentDao().get(first.id)); assertEquals(second, db.attachmentDao().get(second.id))
        assertEquals(2, db.attachmentDao().references(first.id)); assertEquals(1, db.attachmentDao().references(second.id))
        val parserParts = DescriptionParser.parse(db.nodeDao().getById(node.id)!!.description)
        assertEquals(listOf("Gráfico", "Gráfico"), parserParts.filter { it.imageId != null }.map { it.label })
        assertEquals("Bug de interfaz", DescriptionParser.parse(projectText).last().label)
    }
}
