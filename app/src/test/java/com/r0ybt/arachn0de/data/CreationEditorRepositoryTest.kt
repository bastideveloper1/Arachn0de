package com.r0ybt.arachn0de.data

import androidx.room.Room
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class CreationEditorRepositoryTest {
    @Test fun individualEditorSavesCommonFieldsAtomicallyAndDoesNotRewriteCreated() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),Arachn0deDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.projectDao().insert(ProjectEntity("p","Project","",0,1,1));db.personDao().insert(PersonEntity("person","Person",null))
            val repo=NodeRepository(db);val tag=repo.tags.create("tag")
            val node=repo.createNode("p",null,"Original")
            val events=db.nodeEventDao().all()
            assertTrue(repo.updateEditor(node.id,"Changed","Description",100,200,Obligation(25000,"CLP"),false,true,setOf(tag.id),Priority.HIGH,setOf("person")))
            val saved=repo.getNode(node.id)!!;assertEquals("Changed",saved.title);assertEquals(100L,saved.startAt);assertEquals(Priority.HIGH,saved.priority);assertEquals(Obligation(25000,"CLP"),saved.obligation)
            assertEquals(setOf("person"),db.personDao().assignmentIds(node.id).toSet());assertEquals(setOf(tag.id),db.tagDao().nodeIds(node.id).toSet());assertEquals(events,db.nodeEventDao().all())
        } finally { db.close() }
    }
    @Test fun assignmentFailureRollsBackAllEditorFieldsAndKeepsPreviousRelations() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),Arachn0deDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.projectDao().insert(ProjectEntity("p","Project","",0,1,1));db.personDao().insert(PersonEntity("person","Person",null))
            val repo=NodeRepository(db);val tag=repo.tags.create("tag");val node=repo.createNode("p",null,"Original")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER ux_assignment_fail BEFORE INSERT ON node_person BEGIN SELECT RAISE(ABORT,'fail'); END")
            assertTrue(runCatching { repo.updateEditor(node.id,"Changed","Description",100,200,Obligation(25000,"CLP"),false,true,setOf(tag.id),Priority.HIGH,setOf("person")) }.isFailure)
            assertEquals(node,repo.getNode(node.id));assertTrue(db.tagDao().nodeIds(node.id).isEmpty());assertTrue(db.personDao().assignmentIds(node.id).isEmpty());assertEquals(1,db.nodeEventDao().all().size)
        } finally { db.close() }
    }
}
