package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramModelsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun twentyModelsCreateIndependentEditableDocumentsAndRoundTrip()=runBlocking<Unit> {
        assertEquals(20,DiagramModels.items.size);assertEquals(6,DiagramModels.items.map {it.category}.distinct().size)
        assertEquals(20,DiagramModels.items.map {it.id}.distinct().size)
        val repo=DiagramRepository(context.getSharedPreferences("model_test_library",0))
        DiagramModels.items.forEach {model->
            val d=model.create();val second=model.create();val original=DiagramCodec.encode(d)
            assertNotEquals(d.id,second.id);assertTrue(d.nodes.map {it.id}.intersect(second.nodes.map {it.id}.toSet()).isEmpty())
            assertTrue(d.nodes.flatMap {it.fields}.map {it.id}.intersect(second.nodes.flatMap {it.fields}.map {it.id}.toSet()).isEmpty())
            val shapes=d.nodes.filter {it.shape!=DiagramShape.TEXT}
            assertTrue(model.id,shapes.size>=3)
            shapes.forEachIndexed {i,a->shapes.drop(i+1).forEach {b->assertFalse("Overlapping nodes in ${model.id}",a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y)}}
            d.nodes.filter {it.shape==DiagramShape.DIAMOND}.forEach {n->assertTrue("Unbranched decision in ${model.id}: ${n.text}",d.edges.count {it.from==n.id}>=2)}
            assertEquals(d,DiagramCodec.decode(original))
            val first=d.nodes.first();val moved=moveDiagramNode(first,.005f,.005f,false,false)
            val edited=d.copy(name=d.name+" editado",nodes=listOf(moved)+d.nodes.drop(1))
            val history=DiagramHistory(d);history.commit(edited);assertEquals(d,history.undo());assertEquals(edited,history.redo())
            val raw=repo.save(edited,null);assertEquals(edited,repo.load(d.id).first)
            assertEquals(original,DiagramCodec.encode(d));assertEquals(first.fields,moved.fields)
            repo.delete(d.id,raw)
        }
        for(i in 0..5) {val old=diagramTemplate(i);assertEquals(old,DiagramCodec.decode(DiagramCodec.encode(old)))}
    }
    @Test fun allModelsRenderWithNativeExporterAndRealThumbnails() {
        val output=File("/tmp/arachn0de-model-previews").apply {mkdirs()}
        val collisions=mutableListOf<String>()
        DiagramModels.items.forEach {model->
            val d=model.create();val before=DiagramCodec.encode(d)
            val thumb=diagramLibraryBitmap(d,emptyMap());assertTrue(thumb.width<=240&&thumb.height<=240);thumb.recycle()
            val image=Bitmap.createBitmap(800,(800f*d.height/d.width).toInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.scale(image.width.toFloat()/d.width,image.height.toFloat()/d.height)
            DiagramRenderer(emptyMap()).use {renderer->renderer.draw(canvas,d);val bytes=java.io.ByteArrayOutputStream();renderer.png(d,bytes,800,image.height);assertTrue(bytes.size()>100)}
            File(output,"${model.id}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            assertEquals(before,DiagramCodec.encode(d))
            d.edges.forEachIndexed {index,e->
                val measure=PathMeasure(edgePath(d,e),false);val pos=FloatArray(2)
                val blockers=d.nodes.filter {it.id!=e.from&&it.id!=e.to&&it.shape!=DiagramShape.TEXT}
                val hit=blockers.filter {n->(0..(measure.length/4).toInt()).any {i->measure.getPosTan(i*4f,pos,null);pos[0]>n.x*d.width+2&&pos[0]<(n.x+n.w)*d.width-2&&pos[1]>n.y*d.height+2&&pos[1]<(n.y+n.h)*d.height-2}}
                if(hit.isNotEmpty())collisions+="${model.id}: edge $index -> ${hit.joinToString {it.text.replace('\n',' ')}}"
            }
        }
        File(output,"collisions.txt").writeText(collisions.joinToString("\n"))
        assertTrue("Connections cross nodes; see ${output.path}/collisions.txt",collisions.isEmpty())
    }
    @Test fun allModelDocumentsRestoreThroughExistingBackupAndInvalidReferencesPreserveState()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val documents=DiagramModels.items.map {it.create()}+diagramTemplate(1)
            val row=PrivatePreferenceEntity(DiagramCodec.NAME,EncryptedPreferences.encode(documents.associate {"doc_${it.id}" to DiagramCodec.encode(it)}))
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            backup.restore(BackupFixture.empty().copy(privatePreferences=listOf(row)))
            val exported=BackupJson.decode(BackupJson.encode(backup.snapshot()))
            backup.restore(BackupFixture.empty());backup.restore(exported)
            val restored=DiagramCodec.library(EncryptedPreferences.decode(db.privatePreferenceDao().get(DiagramCodec.NAME)!!.payload)).documents
            assertEquals(documents.toSet(),restored.toSet())
            val broken=org.json.JSONObject(DiagramCodec.encode(documents.first())).apply {getJSONArray("edges").getJSONObject(0).put("to",diagramId())}.toString()
            val bad=exported.copy(privatePreferences=listOf(row.copy(payload=EncryptedPreferences.encode(mapOf("doc_${documents.first().id}" to broken)))))
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(row,db.privatePreferenceDao().get(DiagramCodec.NAME))
        } finally {db.close()}
    }
    @Test fun initialMobileViewPreservesReadableScaleAndOverviewRemainsAvailable() {
        val clipped=mutableListOf<String>()
        DiagramModels.items.forEach {model->
            val d=openDiagramModel(model,320f,500f)
            DiagramCodec.validate(d);assertTrue(d.camera!!.scale>=.45f)
            val overview=fitDiagram(d,320f,500f);assertTrue(overview.scale>0)
            if(d.nodes.none {it.shape==DiagramShape.TABLE})d.nodes.forEach {n->if(diagramTextLayout(n,d).height>n.h*d.height)clipped+="${model.id}: ${n.text}"}
        }
        assertTrue("Text clipped: ${clipped.joinToString()}",clipped.isEmpty())
    }
    @Test fun keyGlyphsDifferWithoutChangingStructuredFlags() {
        val table=diagramTableNode().copy(fields=listOf(DiagramField(name="id",type="INT",primaryKey=true)))
        val d=DiagramDocument(nodes=listOf(table))
        fun png(doc:DiagramDocument)=java.io.ByteArrayOutputStream().also {out->DiagramRenderer(emptyMap()).use {it.png(doc,out)}}.toByteArray()
        val pk=png(d)
        val fk=d.copy(nodes=listOf(table.copy(fields=table.fields.map {it.copy(primaryKey=false,foreignKey=true)})))
        assertFalse(pk.contentEquals(png(fk)))
        assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))
        assertEquals(fk,DiagramCodec.decode(DiagramCodec.encode(fk)))
    }
    @Test fun fourRepresentativeModelsHaveRequiredBranchesAndInformativeTables() {
        val login=DiagramModels.items.first {it.id=="login"}.create()
        assertTrue(login.nodes.any {it.text.contains("hash")});assertTrue(login.nodes.count {it.shape==DiagramShape.DIAMOND}>=3)
        val checkout=DiagramModels.items.first {it.id=="checkout"}.create()
        assertTrue(checkout.nodes.any {it.text.contains("idempotente")});assertTrue(checkout.nodes.any {it.text.contains("Validar confirmación")})
        val network=DiagramModels.items.first {it.id=="network"}.create()
        assertEquals(11,network.nodes.size);assertTrue(network.edges.all {!it.endArrow});assertTrue(network.nodes.any {n->network.edges.count {it.from==n.id||it.to==n.id}>=4})
        val blog=DiagramModels.items.first {it.id=="erd-blog"}.create()
        assertEquals(4,blog.nodes.size);assertTrue(blog.edges.isEmpty());assertTrue(blog.nodes.all {it.shape==DiagramShape.TABLE&&it.fields.any {f->f.primaryKey}&&it.fields.all {f->f.type.isNotBlank()}})
    }
}
