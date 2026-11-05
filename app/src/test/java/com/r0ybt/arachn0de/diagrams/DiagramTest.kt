package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.graphics.*
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.json.JSONObject
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun png():ByteArray {val b=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);b.eraseColor(Color.TRANSPARENT);Canvas(b).drawCircle(40f,30f,10f,Paint().apply {color=Color.RED});return ByteArrayOutputStream().also {b.compress(Bitmap.CompressFormat.PNG,100,it);b.recycle()}.toByteArray()}
    @Test fun templatesHaveOneEditableModelAndSurviveRoundTrip() {
        for(i in 0..5) {val d=diagramTemplate(i);assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))}
        val d=diagramTemplate(3);assertEquals(listOf("PC","Router","Servidor"),d.nodes.map {it.text});assertTrue(d.edges.all {it.flow.enabled})
        val bad=JSONObject(DiagramCodec.encode(d)).apply {put("version",1.5)};assertTrue(runCatching {DiagramCodec.decode(bad.toString())}.isFailure)
    }
    @Test fun tablesPreserveFieldsConnectionsAndRenderInExport() {
        val table=diagramTableNode().copy(fields=listOf(DiagramField(name="id",type="INTEGER",primaryKey=true),DiagramField(name="parent_id",foreignKey=true)))
        val other=DiagramNode(x=.72f,y=.65f)
        val d=DiagramDocument(nodes=listOf(table,other),edges=listOf(DiagramEdge(from=table.id,to=other.id,fromAnchor=DiagramAnchor.RIGHT,toAnchor=DiagramAnchor.LEFT)))
        val raw=DiagramCodec.encode(d);assertEquals(3,JSONObject(raw).getInt("version"));assertEquals(d,DiagramCodec.decode(raw))
        val old=diagramTemplate(1);assertEquals(1,JSONObject(DiagramCodec.encode(old)).getInt("version"));assertEquals(old,DiagramCodec.decode(DiagramCodec.encode(old)))
        assertTrue(runCatching {DiagramCodec.decode(JSONObject(raw).put("version",2).toString())}.isFailure)
        assertTrue(runCatching {DiagramCodec.encode(d.copy(nodes=listOf(table.copy(fields=table.fields+table.fields.first()),other)))}.isFailure)
        val history=DiagramHistory(d);val moved=d.copy(nodes=listOf(moveDiagramNode(table,.03f,.04f,false,false),other));history.commit(moved)
        assertEquals(table.fields,history.current.nodes.first().fields);assertEquals(d,history.undo());assertEquals(moved,history.redo())
        fun png(doc:DiagramDocument)=ByteArrayOutputStream().also {out->DiagramRenderer(emptyMap()).use {it.png(doc,out)}}.toByteArray()
        val exported=png(d);assertTrue(exported.size>0)
        assertFalse(exported.contentEquals(png(d.copy(nodes=listOf(table.copy(fields=emptyList()),other)))))
        DiagramFormat.entries.forEach {DiagramExportOptions(it).validateDocument(d)}
        assertTrue(android.graphics.PathMeasure(edgePath(moved,moved.edges.single()),false).length>0)
    }
    @Test fun nodeMovementResizeGuidesAndHistoryPreserveStableConnections() {
        val d=diagramTemplate(3);val n=d.nodes.first();val moved=moveDiagramNode(n,.08f,.12f,false,true)
        assertEquals(((n.x+.08f)/.025f).let {kotlin.math.round(it)*.025f},moved.x,.0001f)
        val resized=moveDiagramNode(moved,.12f,.04f,true,false);assertEquals(n.id,resized.id)
        val changed=d.copy(nodes=listOf(resized)+d.nodes.drop(1));assertNotEquals(PathMeasure(edgePath(d,d.edges.first()),false).length,PathMeasure(edgePath(changed,changed.edges.first()),false).length)
        val history=DiagramHistory(d);history.commit(changed);assertEquals(d,history.undo());assertEquals(changed,history.redo())
        assertTrue(changed.removeNode(n.id).edges.none {it.from==n.id||it.to==n.id})
    }
    @Test fun straightOrthogonalAndCurvedTrajectoriesIncludeAdjustedIntermediatePoints() {
        val d=diagramTemplate(3)
        DiagramRoute.entries.forEach {route->val edge=d.edges.first().copy(route=route,points=listOf(DPoint(.4f,.1f),DPoint(.5f,.8f)))
            val measure=PathMeasure(edgePath(d,edge),false);assertTrue(measure.length>0)
            val p=FloatArray(2);assertTrue(measure.getPosTan(measure.length*.6f,p,null));assertTrue(p.all {it.isFinite()})
        }
        val curve=d.edges.first().copy(route=DiagramRoute.CURVE,points=listOf(DPoint(.3f,.05f)))
        assertNotEquals(PathMeasure(edgePath(d,d.edges.first()),false).length,PathMeasure(edgePath(d,curve),false).length)
    }
    @Test fun particlesFollowTimingDirectionsOnceOrLoopAndKeepIndependentSize() {
        val forward=DiagramFlow(enabled=true,loop=false)
        assertEquals(.5f,flowProgress(2f,forward,0)!!,.0001f)
        assertEquals(1f,flowProgress(4f,forward,0)!!,.0001f);assertNull(flowProgress(5f,forward,0))
        assertEquals(.75f,flowProgress(1f,forward.copy(direction=FlowDirection.REVERSE),0)!!,.0001f)
        assertEquals(.5f,flowProgress(6f,forward.copy(direction=FlowDirection.BOUNCE),0)!!,.0001f)
        assertEquals(.25f,flowProgress(5f,forward.copy(loop=true),0)!!,.0001f)
        val d=diagramTemplate(3);val updated=d.copy(edges=d.edges.map {it.copy(flow=it.flow.copy(size=80f,count=4,speed=2f))});assertEquals(d.nodes,updated.nodes);assertEquals(updated,DiagramCodec.decode(DiagramCodec.encode(updated)))
    }
    @Test fun transparentResourceAndFullStaticPngContainNoEditorOrParticles() {
        val source=File(context.cacheDir,"transparent-${UUID.randomUUID()}.png").apply {writeBytes(png())}
        val asset=try {runBlocking {com.r0ybt.arachn0de.visual.importCanvasImage(context,android.net.Uri.fromFile(source))}} finally {source.delete()}
        val bytes=Base64.decode(asset,Base64.NO_WRAP);val id=DiagramCodec.hash(bytes)
        val d=DiagramDocument(nodes=listOf(DiagramNode(shape=DiagramShape.IMAGE,asset=id,text="",x=.7f,y=.7f,w=.2f,h=.2f)),background=0)
        DiagramRenderer(mapOf(id to asset)).use {renderer->val out=ByteArrayOutputStream();renderer.png(d,out);val bitmap=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size());assertEquals(1600,bitmap.width);assertEquals(1000,bitmap.height);assertEquals(0,Color.alpha(bitmap.getPixel(0,0)));assertNotEquals(0,Color.alpha(bitmap.getPixel(1280,800)));bitmap.recycle()}
    }
    @Test fun encryptedLibraryDeduplicatesIconsRestoresDocumentsAndRejectsBrokenBackup()=runBlocking<Unit> {
        val id=UUID.randomUUID();val root=File(context.filesDir,"diagrams-$id").apply {mkdirs()}
        val session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session);val vault=VaultContext(context,session)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val repo=DiagramRepository(vault.getSharedPreferences(DiagramCodec.NAME,0));val image=com.r0ybt.arachn0de.visual.canvasImage(png())
            val asset=repo.importImage(image);assertEquals(asset,repo.importImage(image))
            val base=diagramTemplate(3);val d=base.copy(camera=DiagramCamera(.42f,.51f,.35f),backgroundAsset=asset,backgroundFit=DiagramBackgroundFit.FILL,nodes=base.nodes.map {it.copy(shape=DiagramShape.IMAGE,asset=asset)},edges=base.edges.map {it.copy(stroke=DiagramStroke.DASHED,flow=it.flow.copy(objectType=FlowObject.IMAGE,asset=asset))})
            val enhanced=d.copy(backgroundGradient=DiagramGradient(listOf(DIAGRAM_COAL,Color.BLUE),45f),playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL,d.edges.map {it.id},false),nodes=d.nodes.mapIndexed {index,n->n.copy(shape=if(index==0)DiagramShape.TABLE else n.shape,fields=if(index==0)listOf(DiagramField(name="id",type="INTEGER",primaryKey=true),DiagramField(name="owner_id",type="UUID",foreignKey=true)) else emptyList(),border=Color.MAGENTA,font=30f,radius=14f,centeredResize=true,resizeContent=DiagramResizeContent.SCALE,gradient=DiagramGradient(listOf(Color.BLUE,Color.CYAN)))},edges=d.edges.map {it.copy(fromAnchor=DiagramAnchor.BOTTOM,toAnchor=DiagramAnchor.TOP,flow=it.flow.copy(particleStyle=DiagramParticleStyle.PULSE,color=Color.GREEN,haloColor=Color.CYAN,glow=.8f,imageHalo=true))})
            repo.favorites(listOf(Color.CYAN,Color.MAGENTA))
            val raw=repo.save(enhanced,null);assertEquals(enhanced,repo.load(d.id).first)
            val beforeFailedWrite=db.privatePreferenceDao().get(DiagramCodec.NAME)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER diagram_write_failure BEFORE INSERT ON private_preferences WHEN NEW.name='diagram_library' BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching {repo.save(enhanced.copy(name="No confirmar"),raw)}.isFailure)
            assertEquals(enhanced,repo.load(d.id).first);assertEquals(beforeFailedWrite,db.privatePreferenceDao().get(DiagramCodec.NAME))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER diagram_write_failure")
            val copy=enhanced.copy(id=diagramId(),name="Otro diagrama");val copyRaw=repo.save(copy,null)
            assertEquals(1,vault.getSharedPreferences(DiagramCodec.NAME,0).all.keys.count {it.startsWith("asset_")})
            assertTrue(runCatching {repo.removeAsset(asset)}.isFailure)
            assertTrue(runCatching {repo.save(enhanced.copy(name="Cambio"),null)}.isFailure)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory));val archive=backup.create("diagram-test".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"diagram-test".toCharArray())
            repo.delete(d.id,raw);assertTrue(runCatching {repo.removeAsset(asset)}.isFailure);repo.delete(copy.id,copyRaw);repo.removeAsset(asset)
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertEquals(enhanced,repo.load(d.id).first);assertEquals(listOf(Color.CYAN,Color.MAGENTA),DiagramCodec.library(vault.getSharedPreferences(DiagramCodec.NAME,0).all).colors)
            val before=backup.snapshot();val broken=before.copy(privatePreferences=before.privatePreferences.map {row->if(row.name==DiagramCodec.NAME) row.copy(payload=EncryptedPreferences.encode(mapOf("doc_${d.id}" to raw))) else row})
            assertTrue(runCatching {backup.restore(broken)}.isFailure);assertEquals(enhanced,repo.load(d.id).first)
            val damaged=JSONObject(raw).apply {getJSONArray("nodes").getJSONObject(0).getJSONArray("fields").getJSONObject(0).put("pk","incorrecto")}.toString()
            val corruptTable=before.copy(privatePreferences=before.privatePreferences.map {row->if(row.name==DiagramCodec.NAME) row.copy(payload=EncryptedPreferences.encode(mapOf("doc_${d.id}" to damaged))) else row})
            assertTrue(runCatching {backup.restore(corruptTable)}.isFailure);assertEquals(enhanced,repo.load(d.id).first)
            repo.delete(d.id,raw);assertTrue(runCatching {repo.removeAsset(asset)}.isFailure);repo.delete(copy.id,copyRaw);repo.removeAsset(asset);repo.favorites(emptyList());assertTrue(DiagramCodec.library(vault.getSharedPreferences(DiagramCodec.NAME,0).all).documents.isEmpty())
            backup.restore(BackupFixture.empty());assertTrue(vault.getSharedPreferences(DiagramCodec.NAME,0).all.isEmpty())
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    }
}
