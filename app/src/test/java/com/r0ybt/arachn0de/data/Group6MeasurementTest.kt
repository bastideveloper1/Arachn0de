package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Group6MeasurementTest {
    @Test fun thumbnailLockAndWarmReadMeasurements()=runBlocking<Unit> {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.filesDir,"group6-${UUID.randomUUID()}").apply {mkdirs()}
        val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
        SecureFiles.register(owner);SecureFiles.activeProfile=owner.id
        val context=VaultContext(base,owner)
        try {
            val folder=File(context.filesDir,"project-photos").apply {mkdirs()}
            val source=File(folder,"${UUID.randomUUID()}.png")
            val image=Bitmap.createBitmap(2048,2048,Bitmap.Config.ARGB_8888)
            val random=java.util.Random(11)
            image.setPixels(IntArray(2048*2048) {random.nextInt() or (0xff shl 24)},0,2048,0,0,2048,2048)
            SecureFiles.output(source).use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
            val cache=File(context.cacheDir,"project-thumbnails").apply {mkdirs()}
            repeat(1000) {File(cache,"${UUID.randomUUID()}-${"a".repeat(64)}.png").writeBytes(byteArrayOf(1))}
            val store=AvatarStore(context,"project-photos",BackupFixture::syncDirectory)
            val worker=async(Dispatchers.IO) {measureNanoTime {assertNotNull(store.readThumbnail(source.name,2048))}}
            while(!AttachmentRepository.fileOperations.isLocked && !worker.isCompleted)delay(1)
            val wait=measureNanoTime {AttachmentRepository.fileOperations.withLock {}}
            val cold=worker.await()
            val warm=List(5) {measureNanoTime {assertNotNull(store.readThumbnail(source.name,2048))}}.sorted()[2]
            println("GROUP6_R11 cold_ms=${cold/1e6} contender_wait_ms=${wait/1e6} warm_median_ms=${warm/1e6} entries=1000 size=2048")
        } finally {context.clearMemory();SecureFiles.revoke(owner);root.deleteRecursively()}
    }
    @Test fun hierarchicalProjectionMeasurements() {
        val project=Project("p","P","",0,0,0,verificationEnabled=false)
        for(depth in listOf(2,64)) {
            val layers=(0 until depth).map {i->Node("l$i","p",if(i==0)null else "l${i-1}","L","",false,0,0,0,true,purpose=NodePurpose.LAYER)}
            val tasks=(0 until 2000).map {i->Node("t$i","p","l${depth-1}","T","",false,0,0,0,false)}
            val tree=NodeTreeSnapshot(layers+tasks);val eligible=tasks.mapTo(hashSetOf()) {it.id}
            repeat(3) {TaskViewProjection.build(tree,project,null,eligible,null)}
            val bean=java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
            val times=mutableListOf<Long>();val allocations=mutableListOf<Long>()
            repeat(5) {
                val before=bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
                times+=measureNanoTime {assertEquals(2000,TaskViewProjection.build(tree,project,null,eligible,null).total)}
                allocations+=(bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0)-before
            }
            println("GROUP6_R12 depth=$depth tasks=2000 median_ms=${times.sorted()[2]/1e6} allocated_median_bytes=${allocations.sorted()[2]}")
        }
    }

    @Test fun projectionMatchesPreviousAlgorithmAcrossScopesFiltersAndOverrides() {
        for(seed in 0..7) {
            val random=java.util.Random(seed.toLong())
            val layers=(0 until 12).map {i->Node("l$i","p",if(i==0)null else "l${random.nextInt(i)}","L","",false,0,0,0,true,purpose=NodePurpose.LAYER,taskStateCount=if(i%3==0)2+random.nextInt(2) else null)}
            val tasks=(0 until 80).map {i->Node("t$i","p","l${random.nextInt(12)}","T","",i%3==0,0,0,0,false,awaitingVerification=i%3==1,taskStateCount=if(i%7==0)2+random.nextInt(2) else null).let {if(i%5==0)it.copy(purpose=NodePurpose.NOTE) else it}}
            val tree=NodeTreeSnapshot(layers+tasks)
            val eligible=tree.nodes.filter {random.nextBoolean()}.mapTo(hashSetOf()) {it.id}
            for(verification in listOf(false,true))for(parent in listOf(null,"l0","l6"))for(status in listOf(null)+TaskStatus.entries) {
                val project=Project("p","P","",0,0,0,verificationEnabled=verification)
                assertEquals(previousProjection(tree,project,parent,eligible,status),TaskViewProjection.build(tree,project,parent,eligible,status))
            }
        }
    }

    private fun previousProjection(snapshot:NodeTreeSnapshot,project:Project,parent:String?,eligible:Set<String>,status:TaskStatus?):TaskViewProjection {
            val rows=ScopedNodeFilter.apply(snapshot,project.id,parent,NodeFilter(),emptyMap(),emptyMap())
            val tasks=rows.map {it.node}.filter {it.isCompletable}.distinctBy {it.id}
            val candidates=tasks.filter {it.id in eligible}
            val counts=TaskStatus.entries.associateWith {state->candidates.count {it.taskStatus==state}}
            val matching=candidates.filter {status==null || it.taskStatus==status}.mapTo(hashSetOf()) {it.id}
            val totals=mutableMapOf<String,Int>()
            matching.forEach {id->
                var cursor=snapshot.nodesById[id]?.parentId;val visited=hashSetOf<String>()
                while(cursor!=null && visited.add(cursor)) {totals[cursor]=(totals[cursor] ?: 0)+1;cursor=snapshot.nodesById[cursor]?.parentId}
            }
            return TaskViewProjection(rows.filter {it.node.isStructural || it.node.hasChildren || it.node.id in matching || (status==null && it.node.id in eligible)}.map {it.copy(isMatch=it.node.id in matching)},counts,matching,totals,
                tasks.any {it.workState==null && TaskStatePolicy.count(it,snapshot.nodesById,project)==3})
    }
}
