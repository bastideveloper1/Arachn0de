package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CompletionTimesQueryTest {
    private val queries=java.util.Collections.synchronizedList(mutableListOf<String>())
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private var now=100L
    @Before fun setup()=runBlocking<Unit> {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),Arachn0deDatabase::class.java)
            .setQueryCallback({sql,_->queries.add(sql)},java.util.concurrent.Executor {it.run()}).build()
        nodes=NodeRepository(db) {now}
        for(id in listOf("p","other")) db.projectDao().insert(ProjectEntity(id,id,"",0,1,1))
    }
    @After fun close() {db.close()}
    private suspend fun old(project:String):Map<String,Long> {
        val result=mutableMapOf<String,Long>()
        db.nodeDao().getProjectNodes(project).map {it.id}.chunked(500).forEach {ids->
            db.nodeEventDao().eventsForNodes(ids).forEach {e->if(e.type=="COMPLETED")result.putIfAbsent(e.nodeId,e.occurredAt)}
        }
        return result
    }
    @Test fun aggregateMatchesHistoryWithReopeningRepeatedTimestampsAndOtherProjects()=runBlocking<Unit> {
        val a=nodes.createNode("p",null,"A");val b=nodes.createNode("p",null,"B")
        val outside=nodes.createNode("other",null,"Other");nodes.setCompleted(outside.id,true)
        val events=(0 until 1200).map {i->NodeEventEntity("event:$i",a.id,if(i%3==0)"COMPLETED" else "REOPENED",(i/2).toLong())}
        db.nodeEventDao().insertAll(events)
        nodes.setCompleted(b.id,true);now=800;nodes.setCompleted(b.id,false)
        val expected=old("p")
        queries.clear()
        val actual=nodes.observeCompletionTimes("p").first()
        assertEquals(expected,actual);assertEquals(2,actual.size);assertFalse(outside.id in actual)
        assertEquals(100L,actual[b.id]) // Reopening retains the historical completion timestamp.
        val reads=synchronized(queries) {queries.filter {it.contains("node_events") && it.trimStart().startsWith("SELECT",true)}}
        assertEquals(1,reads.size);assertTrue(reads.single().contains("MAX(e.occurredAt)"))
        val sql="EXPLAIN QUERY PLAN SELECT e.nodeId,MAX(e.occurredAt) FROM nodes n JOIN node_events e ON e.nodeId=n.id WHERE n.projectId='p' AND e.type='COMPLETED' GROUP BY e.nodeId"
        db.openHelper.readableDatabase.query(sql).use {cursor->while(cursor.moveToNext())println("Completion query plan: ${cursor.getString(3)}")}
        assertTrue(nodes.observeCompletionTimes("missing").first().isEmpty())
    }
    @Test fun activeObservationTracksCompleteReopenRecompleteMoveAndDeletion()=runBlocking<Unit> {
        val node=nodes.createNode("p",null,"Task")
        val channel=Channel<Map<String,Long>>(Channel.UNLIMITED)
        val observer=launch {nodes.observeCompletionTimes("p").collect {channel.send(it)}}
        suspend fun await(expected:Map<String,Long>)=withTimeout(15000) {while(channel.receive()!=expected) {}}
        try {
            await(emptyMap());now=200;nodes.setCompleted(node.id,true);await(mapOf(node.id to 200L))
            now=300;nodes.setCompleted(node.id,false)
            assertEquals(old("p"),nodes.observeCompletionTimes("p").first())
            now=400;nodes.setCompleted(node.id,true);await(mapOf(node.id to 400L))
            db.withTransaction {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET projectId='other' WHERE id=?",arrayOf(node.id))}
            await(emptyMap());assertEquals(mapOf(node.id to 400L),nodes.observeCompletionTimes("other").first())
            nodes.deleteNode(node.id);assertTrue(nodes.observeCompletionTimes("other").first().isEmpty())
        } finally {observer.cancelAndJoin();channel.close()}
    }
    @Test fun projectObservationsIgnoreOtherProjectsWhileGlobalProgressStaysLive()=runBlocking<Unit> {
        val local=nodes.createNode("p",null,"Local");val other=nodes.createNode("other",null,"Other")
        val project=Channel<com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot>(Channel.UNLIMITED)
        val global=Channel<com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot>(Channel.UNLIMITED)
        val projectObserver=launch {nodes.observeProjectState("p").collect {project.send(it)}}
        val globalObserver=launch {nodes.observeAllState().collect {global.send(it)}}
        try {
            withTimeout(5000) {project.receive();global.receive()}
            nodes.setCompleted(other.id,true)
            withTimeout(5000) {while(global.receive().projectProgressById["other"]?.completed!=1) {}}
            assertNull(withTimeoutOrNull(200) {project.receive()})
            nodes.setCompleted(local.id,true)
            assertEquals(1,withTimeout(5000) {project.receive()}.projectProgressById["p"]!!.completed)
            val latest=withTimeout(5000) {var value=global.receive();while(value.projectProgressById["p"]?.completed!=1)value=global.receive();value}
            assertEquals(1,latest.projectProgressById["other"]!!.completed)
        } finally {projectObserver.cancelAndJoin();globalObserver.cancelAndJoin();project.close();global.close()}
    }

}
