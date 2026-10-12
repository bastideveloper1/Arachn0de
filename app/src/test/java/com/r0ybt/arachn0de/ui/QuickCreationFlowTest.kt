package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[28])
class QuickCreationFlowTest {
    @Test fun existingSaveFlowKeepsDestinationAndPreventsDuplicatesForTaskAndNote()=runBlocking<Unit> {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(context)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            db.projectDao().insert(ProjectEntity("project","Proyecto","",0,1,1))
            val nodes=NodeRepository(db)
            val layer=nodes.createNode("project",null,"Capa",purpose=NodePurpose.LAYER)
            val actions=NodeActions(nodes,scope)
            for((parent,purpose) in listOf(null to NodePurpose.ACTION,layer.id to NodePurpose.ACTION,layer.id to NodePurpose.NOTE)) {
                val draft=EditorDraft(null,parent,"${purpose.name}:${parent}","",purpose=purpose)
                val saved=CompletableDeferred<Unit>()
                repeat(2) {actions.saveDraft("project",draft,purpose==NodePurpose.ACTION) {saved.complete(Unit)}}
                withTimeout(15000) {saved.await()}
                val matches=nodes.getProjectNodes("project").filter {it.id==draft.creationId}
                assertEquals(1,matches.size);assertEquals(parent,matches.single().parentId)
                assertEquals(purpose,matches.single().purpose)
                withTimeout(15000) {while(actions.operation.busy) delay(1)}
            }
        } finally {scope.cancel();db.close();context.deleteDatabase("arachn0de.db")}
    }
}
