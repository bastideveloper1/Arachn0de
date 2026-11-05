package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.graphics.*
import androidx.compose.ui.geometry.Offset
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramVisualTest {
    private fun asset():Pair<String,String> {
        val bitmap=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED)
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        return DiagramCodec.hash(bytes) to com.r0ybt.arachn0de.visual.canvasImage(bytes)
    }
    @Test fun historicalDocumentsDefaultToSolidAndNoBackgroundAndRejectUnknownStyles() {
        val d=diagramTemplate(3);val j=JSONObject(DiagramCodec.encode(d));j.remove("backgroundAsset");j.remove("backgroundFit")
        val edges=j.getJSONArray("edges");for(i in 0 until edges.length())edges.getJSONObject(i).remove("strokeStyle")
        assertEquals(d,DiagramCodec.decode(j.toString()))
        edges.getJSONObject(0).put("strokeStyle","INVALID")
        assertThrows(IllegalArgumentException::class.java) {DiagramCodec.decode(j.toString())}
        val bg=JSONObject(DiagramCodec.encode(d)).put("backgroundFit",false)
        assertTrue(runCatching {DiagramCodec.decode(bg.toString())}.isFailure)
    }
    @Test fun newPortraitTemplatesAreVerticalAndLandscapeChoiceAndExistingCoordinatesRemainFree() {
        for(i in 1..5) {
            val d=diagramTemplate(i,true);assertTrue(d.nodes.zipWithNext().all {(a,b)->a.y<b.y});DiagramCodec.validate(d)
            val landscape=diagramTemplate(i,false);assertTrue(landscape.nodes.zipWithNext().all {(a,b)->a.x<b.x})
            val saved=DiagramCodec.encode(landscape);diagramTemplate(i,true);assertEquals(landscape,DiagramCodec.decode(saved))
        }
        assertEquals(listOf("PC","Router","Servidor"),diagramTemplate(3,true).nodes.map {it.text})
    }
    @Test fun backgroundFitFillPreserveAspectStayBehindNodesAndParticipateInIntegrityValidation() {
        val (id,raw)=asset();val d=DiagramDocument(background=0,backgroundAsset=id)
        assertEquals(setOf(id),d.assets());assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))
        assertTrue(runCatching {DiagramCodec.library(mapOf("doc_${d.id}" to DiagramCodec.encode(d)))}.isFailure)
        assertEquals(1,DiagramCodec.library(mapOf("doc_${d.id}" to DiagramCodec.encode(d),"asset_$id" to raw)).assets.size)
        val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(mapOf(id to raw)).use {r->
            r.draw(Canvas(bitmap),d);assertEquals(0,Color.alpha(bitmap.getPixel(800,50)));assertEquals(Color.RED,bitmap.getPixel(800,500))
            r.draw(Canvas(bitmap),d.copy(backgroundFit=DiagramBackgroundFit.FILL));assertEquals(Color.RED,bitmap.getPixel(800,50))
            val node=DiagramNode(x=.4f,y=.4f,w=.2f,h=.2f,text="",fill=Color.BLUE,stroke=0f)
            r.draw(Canvas(bitmap),d.copy(nodes=listOf(node)));assertEquals(Color.BLUE,bitmap.getPixel(800,500))
            r.draw(Canvas(bitmap),d.copy(backgroundAsset=null));assertEquals(0,Color.alpha(bitmap.getPixel(800,500)))
        }} finally {bitmap.recycle()}
    }
    @Test fun actualRendererSeparatesLineStyleAndRouteAndGifAndVideoFramesUseTheSameBackground() {
        val a=DiagramNode(x=.1f,y=.45f,w=.1f,h=.1f,text="");val b=a.copy(id=diagramId(),x=.8f)
        val edge=DiagramEdge(from=a.id,to=b.id,color=Color.BLACK,width=4f,endArrow=false)
        val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(edge),background=Color.WHITE)
        val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888)
        val counts=mutableListOf<Int>()
        try {DiagramRenderer(emptyMap()).use {r->DiagramStroke.entries.forEach {stroke->
            val changed=d.copy(edges=listOf(edge.copy(stroke=stroke)));r.draw(Canvas(bitmap),changed)
            counts+=(330..1270).count {Color.red(bitmap.getPixel(it,500))<80}
            DiagramRoute.entries.forEach {route->assertEquals(stroke,DiagramCodec.decode(DiagramCodec.encode(changed.copy(edges=listOf(edge.copy(stroke=stroke,route=route))))).edges.single().stroke)}
        }}} finally {bitmap.recycle()}
        assertTrue(counts[0]>counts[1]);assertTrue(counts[1]>counts[2]);assertTrue(counts[2]>0)
        val (id,raw)=asset();val frame=Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888)
        val output=ByteArrayOutputStream();val withBackground=d.copy(backgroundAsset=id,backgroundFit=DiagramBackgroundFit.FILL,edges=listOf(edge.copy(stroke=DiagramStroke.DASHED,flow=DiagramFlow(enabled=true,size=90f))))
        try {DiagramRenderer(mapOf(id to raw)).use {r->
            DiagramGif(output,320,200,true).use {gif->listOf(0f,1f).forEach {time->renderDiagramFrame(r,withBackground,frame,time);gif.frame(frame)}}
            renderDiagramFrame(r,withBackground,frame,0f,true);assertEquals(Color.RED,frame.getPixel(160,10))
        }} finally {frame.recycle()}
        javax.imageio.ImageIO.createImageInputStream(output.toByteArray().inputStream()).use {input->val reader=javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();reader.input=input
            try {assertEquals(2,reader.getNumImages(true));assertEquals(Color.RED,reader.read(0).getRGB(160,10));val first=reader.read(0);val second=reader.read(1);assertTrue((0 until 200).any {y->(0 until 320).any {x->first.getRGB(x,y)!=second.getRGB(x,y)}})} finally {reader.dispose()}
        }
    }
    @Test fun curveRemainsVisiblyCurvedForAlignedVerticalAndHorizontalNodes() {
        for(portrait in listOf(false,true)) {
            val d=diagramTemplate(3,portrait);val e=d.edges.first()
            val line=PathMeasure(edgePath(d,e),false);val curved=PathMeasure(edgePath(d,e.copy(route=DiagramRoute.CURVE)),false)
            assertTrue(curved.length>line.length+1)
            val a=FloatArray(2);val b=FloatArray(2);line.getPosTan(line.length/2,a,null);curved.getPosTan(curved.length/2,b,null)
            assertTrue(kotlin.math.hypot(a[0]-b[0],a[1]-b[1])>1f)
        }
    }
    @Test fun multiAlignmentAndDistributionPreserveIdsConnectionsAndStayWithinBounds() {
        val nodes=listOf(DiagramNode(x=.1f,y=.1f,w=.1f,h=.1f),DiagramNode(x=.35f,y=.35f,w=.15f,h=.1f),DiagramNode(x=.8f,y=.8f,w=.1f,h=.1f))
        val edge=DiagramEdge(from=nodes.first().id,to=nodes.last().id)
        val d=DiagramDocument(nodes=nodes,edges=listOf(edge));val ids=nodes.map {it.id}.toSet()
        DiagramArrange.entries.forEach {action->val next=arrangeDiagram(d,ids,action);DiagramCodec.validate(next);assertEquals(ids,next.nodes.map {it.id}.toSet());assertEquals(d.edges,next.edges)}
        val horizontal=arrangeDiagram(d,ids,DiagramArrange.HSPACE).nodes.sortedBy {it.x}
        assertEquals(horizontal[1].x-horizontal[0].x-horizontal[0].w,horizontal[2].x-horizontal[1].x-horizontal[1].w,.00001f)
        assertEquals(d,arrangeDiagram(d,setOf(nodes[0].id),DiagramArrange.LEFT))
        assertFalse(canArrange(d,setOf(nodes[0].id,nodes[1].id),DiagramArrange.HSPACE))
        val before=DiagramHistory(d);val next=arrangeDiagram(d,ids,DiagramArrange.TOP);before.commit(next);assertEquals(d,before.undo());assertEquals(next,before.redo())
    }
    @Test fun badgesKeepSessionNumbersAfterDeleteAndZoomAndNeverChangeDocumentData() {
        val d=diagramTemplate(3);val extra=DiagramEdge(from=d.nodes.first().id,to=d.nodes.last().id)
        val full=d.copy(edges=d.edges+extra);val order=full.edges.map {it.id}
        val deleted=full.copy(edges=full.edges.filterNot {it.id==order[1]});val before=DiagramCodec.encode(deleted)
        for(scale in listOf(.25f,.5f,1f)) {
            val badges=diagramBadges(deleted,order,diagramViewport(400f,650f,1f,DiagramCamera(.5f,.5f,scale)),400f,650f,12f)
            assertEquals(listOf(1,3),badges.map {it.number});assertEquals(deleted.edges.map {it.id},badges.map {it.edgeId});assertTrue(badges.all {it.point.x.isFinite()&&it.point.y.isFinite()})
        }
        assertEquals(before,DiagramCodec.encode(deleted));assertFalse(JSONObject(before).has("numbers"))
    }
    @Test fun gridStepAndWireframeMagneticAdapterAreIndependentOfVisibilityAndContrastIsExplicit() {
        val node=DiagramNode(x=.12f,y=.2f,w=.2f,h=.1f)
        assertEquals(.2f,moveDiagramNode(node,.06f,0f,false,true,.1f).x,.0001f)
        assertEquals(.18f,moveDiagramNode(node,.06f,0f,false,false,.1f).x,.0001f)
        val moved=node.copy(x=.397f);val aligned=diagramSmartAlign(DiagramDocument(nodes=listOf(moved)),moved,.01f,.01f)
        assertEquals(.4f,aligned.first.x,.0001f);assertEquals(.5f,aligned.second!!,.0001f)
        val d=DiagramDocument(nodes=listOf(node.copy(shape=DiagramShape.TEXT,ink=Color.BLACK)),background=Color.BLACK)
        assertEquals(Color.BLACK,d.nodes.single().ink);assertEquals(Color.WHITE,adaptDiagramContrast(d).nodes.single().ink)
    }
}
