package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.WorkState
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class SprintBackupTest {
    private fun data()=BackupFixture.empty().copy(projects=listOf(ProjectEntity("p","P","",0,1,1)),nodes=listOf(
        NodeEntity("root","p",null,"Sprint","",false,0,1,1,purpose="LAYER",sprintMode=true),
        NodeEntity("empty","p",null,"Empty Sprint","",false,1,1,1,purpose="LAYER",sprintMode=true),
        NodeEntity("normal","p","root","Normal","",false,0,1,1,purpose="LAYER"),
        NodeEntity("note","p","root","Note","",false,1,1,1,purpose="NOTE")) + WorkState.entries.mapIndexed { index,state ->
        NodeEntity(state.name,"p","root",state.label,"",state.completed,index+2,1,1,workState=state.name)
    })
    @Test fun v9RoundTripKeepsEmptySprintAndEveryWorkState() {
        val data=data();val bytes=BackupJson.encode(data)
        assertEquals(9,JSONObject(String(bytes)).getInt("dataVersion"));assertEquals(data,BackupJson.decode(bytes))
    }
    @Test fun v8RestoresNormalModeAndBinaryCompletion() {
        val historical=data().copy(nodes=data().nodes.map { it.copy(sprintMode=false,workState=null) })
        val json=JSONObject(String(BackupJson.encode(historical))).apply {
            put("dataVersion",8)
            val rows=getJSONArray("nodes");for(i in 0 until rows.length()) { rows.getJSONObject(i).remove("sprintMode");rows.getJSONObject(i).remove("workState") }
        }
        assertEquals(historical,BackupJson.decode(json.toString().toByteArray()))
    }
    @Test fun restorePreservesWorkflowAndHistoryInRoom() = kotlinx.coroutines.runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        val db = Arachn0deDatabase.create(context)
        try {
            val repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
            val original = data().copy(nodeEvents=listOf(NodeEventEntity("completed","DONE","COMPLETED",10)))
            repo.restore(BackupJson.decode(BackupJson.encode(original)))
            assertEquals(original.nodes.toSet(),repo.snapshot().nodes.toSet())
            assertEquals(original.nodeEvents,repo.snapshot().nodeEvents)
            val nodes = com.r0ybt.arachn0de.data.repository.NodeRepository(db)
            nodes.advanceWorkState("DOING")
            assertEquals("DONE",db.nodeDao().getById("DOING")!!.workState)
            assertEquals(1,db.nodeEventDao().forNode("DOING").count { it.type=="COMPLETED" })
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }
    @Test fun backupRejectsImpossibleStatesAndContext() {
        val data=data()
        fun reject(id:String,transform:(NodeEntity)->NodeEntity) = assertTrue(runCatching { data.copy(nodes=data.nodes.map { if(it.id==id) transform(it) else it }).validate() }.isFailure)
        reject("DOING") { it.copy(isCompleted=true) };reject("VALIDATED") { it.copy(isCompleted=false) }
        reject("note") { it.copy(workState="UNPLANNED") };reject("normal") { it.copy(workState="UNPLANNED") }
        reject("root") { it.copy(sprintMode=false) };reject("PLANNED") { it.copy(workState=null) }
        reject("DOING") { it.copy(workState="UNKNOWN") };reject("DOING") { it.copy(sprintMode=true) }
    }
}
