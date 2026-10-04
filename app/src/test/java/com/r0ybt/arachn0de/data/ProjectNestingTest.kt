package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class ProjectNestingTest {
    private lateinit var db:Arachn0deDatabase;private lateinit var nodes:NodeRepository;private lateinit var projects:ProjectRepository;private lateinit var backup:BackupRepository
    @Before fun setup()=runBlocking {
        val c=RuntimeEnvironment.getApplication();c.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(c);nodes=NodeRepository(db);projects=ProjectRepository(db.projectDao(),db)
        db.projectDao().insert(ProjectEntity("source","Same name","Description",7,10,20));db.projectDao().insert(ProjectEntity("target","Same name","Destination",3,11,21))
        backup=BackupRepository(db,c,BackupAvatarFiles(c,syncDirectory=BackupFixture::syncDirectory))
    }
    @After fun close() { db.close() }
    @Test fun emptyProjectBecomesEmptyLayerAtEndWithProjectMetadata()=runBlocking {
        val sibling=nodes.createNode("target",null,"Sibling")
        val id=projects.moveInside("source","target",null)
        val layer=nodes.getNode(id)!!
        assertEquals(NodePurpose.LAYER,layer.purpose);assertTrue(layer.isStructural);assertFalse(layer.hasChildren);assertEquals("Description",layer.description)
        assertEquals(10L,layer.createdAt);assertEquals(20L,layer.updatedAt);assertEquals(sibling.position+1,layer.position);assertNotEquals("source",id)
        assertNull(projects.getProject("source"));assertEquals(NodeProgressState.NO_WORK,NodeTreeSnapshot(listOf(layer)).progressById.getValue(id).state)
        val snapshot=backup.snapshot();backup.restore(BackupJson.decode(BackupJson.encode(snapshot)));assertEquals(layer,nodes.getNode(id))
    }
    @Test fun preservesEveryAssociationDefaultsAndRecurrenceAndDerivedProjections()=runBlocking {
        val parent=nodes.createNode("source",null,"Inner",creationId="inner",purpose=NodePurpose.LAYER)
        db.personDao().insert(PersonEntity("person","Roy",null));val tag=nodes.tags.create("Tag")
        val task=nodes.createNode("source",parent.id,"Bill",creationId="bill",startAt=1,dueAt=2,obligation=Obligation(15000,"CLP"),priority=Priority.HIGH,responsibleIds=setOf("person"),tagIds=setOf(tag.id))
        nodes.setCompleted(task.id,true);db.nodeDao().patchShared(task.id,"details",15000,"CLP","HIGH",30)
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET creationGroupId='group' WHERE id='bill'")
        nodes.createNode("source",parent.id,"Pending",creationId="pending",dueAt=2,priority=Priority.MEDIUM)
        nodes.createNode("source",null,"Note",creationId="note",purpose=NodePurpose.NOTE)
        nodes.creationDefaults.save(DefaultsScope.Project("source"),CreationDefaults(currency=DefaultValue.Own("USD"),people=DefaultValue.Own(setOf("person")),tags=DefaultValue.Own(setOf(tag.id))))
        nodes.creationDefaults.save(DefaultsScope.Layer("source",parent.id),CreationDefaults(priority=DefaultValue.Own(Priority.HIGH)))
        val rule=RecurrenceRuleEntity("rule","source",parent.id,"Repeat","",null,null,1,"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        nodes.recurrence.create(rule);nodes.recurrence.materializeBatch(86400000L)
        nodes.recurrence.create(rule.copy(id="paused",parentId=null,status="ACTIVE"));db.recurrenceDao().update(rule.copy(id="paused",parentId=null,status="PAUSED"))
        nodes.recurrence.create(rule.copy(id="finished",parentId=null));db.recurrenceDao().update(rule.copy(id="finished",parentId=null,status="FINISHED"))
        val before=backup.snapshot()
        val id=projects.moveInside("source","target",null);val after=backup.snapshot()
        assertEquals(before.nodes.size+1,after.nodes.size)
        before.nodes.forEach { original -> assertEquals(original.copy(projectId="target",parentId=original.parentId ?: id),db.nodeDao().getById(original.id)) }
        assertEquals(before.assignments,after.assignments);assertEquals(before.nodeTags,after.nodeTags);assertEquals(before.nodeEvents.toSet(),after.nodeEvents.toSet())
        assertEquals(before.recurrenceOccurrences,after.recurrenceOccurrences);assertEquals(before.recurrenceAssignments,after.recurrenceAssignments)
        before.recurrenceRules.forEach { old -> assertEquals(old.copy(projectId="target",parentId=old.parentId ?: id),db.recurrenceDao().get(old.id)) }
        val effective=nodes.creationDefaults.resolve("target",parent.id);assertEquals("USD",effective.currency);assertEquals(Priority.HIGH,effective.priority);assertEquals(setOf("person"),effective.people);assertEquals(setOf(tag.id),effective.tags)
        val tree=NodeTreeSnapshot(nodes.getProjectNodes("target"));assertEquals(1,tree.progressById.getValue(id).completed);assertEquals(3,tree.progressById.getValue(id).total)
        assertTrue(CalendarSnapshot(tree,java.util.TimeZone.getTimeZone("UTC")).tasksByDay.values.flatten().any { it.id=="pending" })
        nodes.recurrence.materializeBatch(86400000L);assertEquals(after.recurrenceOccurrences,db.recurrenceDao().occurrences())
        backup.restore(BackupJson.decode(BackupJson.encode(after)));assertEquals(after.nodes.sortedBy { it.id },backup.snapshot().nodes.sortedBy { it.id })
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
    @Test fun layerDestinationKeepsSiblingPositionsAndAllowsDuplicateNames()=runBlocking {
        val parent=nodes.createNode("target",null,"Same name",purpose=NodePurpose.LAYER)
        val sibling=nodes.createNode("target",parent.id,"Same name")
        val id=projects.moveInside("source","target",parent.id)
        assertEquals(parent.id,nodes.getNode(id)!!.parentId);assertEquals(sibling,nodes.getNode(sibling.id));assertEquals(sibling.position+1,nodes.getNode(id)!!.position)
    }
    @Test fun rollbackAfterSourceDeletionRestoresEverything()=runBlocking {
        val parent=nodes.createNode("source",null,"Layer",purpose=NodePurpose.LAYER);nodes.createNode("source",parent.id,"Task")
        nodes.creationDefaults.save(DefaultsScope.Project("source"),CreationDefaults(priority=DefaultValue.Own(Priority.HIGH)))
        val before=backup.snapshot()
        assertTrue(runCatching { ProjectNestingRepository(db) { error("Injected before commit") }.move("source","target",null) }.isFailure)
        val after=backup.snapshot();assertEquals(before.copy(createdAt=after.createdAt),after)
    }
    @Test fun invalidTargetsNeverChangeSource()=runBlocking {
        val action=nodes.createNode("target",null,"Task");val note=nodes.createNode("target",null,"Note",purpose=NodePurpose.NOTE)
        for((target,parent) in listOf("source" to null,"missing" to null,"target" to action.id,"target" to note.id,"target" to "missing")) {
            val before=backup.snapshot();assertTrue(runCatching { projects.moveInside("source",target,parent) }.isFailure);val after=backup.snapshot();assertEquals(before.copy(createdAt=after.createdAt),after)
        }
    }
    @Test fun largeDestinationAppendsWithoutRewritingOtherPositions()=runBlocking {
        for(i in 0 until 600) db.nodeDao().insert(NodeEntity("target$i","target",null,"Task","",false,i*2,1,2))
        val before=db.nodeDao().getProjectNodes("target")
        val id=projects.moveInside("source","target",null)
        assertEquals(1199,nodes.getNode(id)!!.position)
        assertEquals(before,db.nodeDao().getProjectNodes("target").filter { it.id!=id })
    }
    @Test fun cancellationBeforeCommitRollsBackWholeConversion()=runBlocking {
        nodes.createNode("source",null,"Task")
        val before=backup.snapshot()
        assertTrue(runCatching { ProjectNestingRepository(db) { throw kotlinx.coroutines.CancellationException("Injected cancellation") }.move("source","target",null) }.isFailure)
        val after=backup.snapshot();assertEquals(before.copy(createdAt=after.createdAt),after)
    }
    @Test fun deepTreePreservesIdentityWithoutRecursiveDelete()=runBlocking {
        for(i in 0 until 1100) db.nodeDao().insert(NodeEntity("n$i","source",if(i==0) null else "n${i-1}","Node","",false,i,1,2,purpose=if(i<1099) "LAYER" else "ACTION"))
        val id=projects.moveInside("source","target",null)
        val tree=NodeTreeSnapshot(nodes.getProjectNodes("target"));assertEquals(1101,tree.nodes.size);assertEquals(1,tree.progressById.getValue(id).total);assertEquals("n1098",tree.nodesById.getValue("n1099").parentId)
    }
}
