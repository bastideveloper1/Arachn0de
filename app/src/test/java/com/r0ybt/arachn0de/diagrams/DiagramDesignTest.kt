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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramDesignTest {
    private fun node(x:Float=.2f,y:Float=.2f)=DiagramNode(x=x,y=y,w=.12f,h=.08f,text="A")
    @Test fun connectedDuplicationBuildsSelectedChainWithoutMovingExistingNodesAndHistoryIsAtomic() {
        var d=DiagramDocument(nodes=listOf(node()),page=DiagramPage(DiagramPageFormat.STORY,900,1600));var id=d.nodes.single().id
        val history=DiagramHistory(d)
        repeat(4) {
            val previous=d;val result=duplicateDiagramNode(d,id,true)!!;d=result.document
            assertEquals(previous.nodes,d.nodes.dropLast(1));assertEquals(id,d.edges.last().from);assertEquals(result.selected,d.edges.last().to)
            assertTrue(d.nodes.last().y>d.nodes[d.nodes.lastIndex-1].y);assertFalse(d.edges.last().flow.enabled)
            history.commit(d);assertEquals(previous,history.undo());assertEquals(d,history.redo());id=result.selected
        }
        assertEquals(5,d.nodes.size);assertEquals(4,d.edges.size);assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))
        val plain=duplicateDiagramNode(d,id,false)!!;assertEquals(d.edges,plain.document.edges)
    }
    @Test fun duplicateDirectionInheritsStyleAndFindsVacanciesWithoutOverlapping() {
        val a=node();val b=node(.45f);val edge=DiagramEdge(from=a.id,to=b.id,color=Color.MAGENTA,width=3f,flow=DiagramFlow(enabled=true,particleStyle=DiagramParticleStyle.PULSE))
        val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(edge),playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL,listOf(edge.id)))
        val r=duplicateDiagramNode(d,b.id,true,lastEdge=edge.id)!!;val c=r.document.nodes.last()
        assertTrue(c.x>b.x+b.w);assertEquals(Color.MAGENTA,r.document.edges.last().color);assertEquals(DiagramParticleStyle.PULSE,r.document.edges.last().flow.particleStyle)
        assertEquals(2,r.document.playback.route.size)
        val left=duplicateDiagramNode(d,a.id,true,DiagramDirection.LEFT)!!;assertTrue(left.document.nodes.last().x<a.x)
        assertNull(duplicateDiagramNode(DiagramDocument(nodes=listOf(a.copy(x=0f,y=0f,w=1f,h=1f))),a.id,true))
        val full=DiagramDocument(nodes=List(128) {node()})
        assertThrows(IllegalArgumentException::class.java) {duplicateDiagramNode(full,full.nodes.first().id,false)}
    }
    @Test fun realBoundariesClampOnlySelectedGroupAndViewportEdgesAreIndependent() {
        val a=node();val b=node(.6f,.6f);val d=DiagramDocument(nodes=listOf(a,b))
        for((delta,limit) in listOf(Offset(-2f,0f) to DiagramHandle.LEFT,Offset(2f,0f) to DiagramHandle.RIGHT,Offset(0f,-2f) to DiagramHandle.TOP,Offset(0f,2f) to DiagramHandle.BOTTOM)) {
            assertEquals(setOf(limit),diagramMoveLimits(d,setOf(a.id),delta.x,delta.y))
            val moved=moveDiagramSelection(d,setOf(a.id),delta.x,delta.y);assertEquals(b,moved.nodes[1]);DiagramCodec.validate(moved)
        }
        assertTrue(diagramMoveLimits(d,setOf(a.id),.1f,.1f).isEmpty())
        val moved=moveDiagramSelection(d,setOf(a.id,b.id),.1f,.1f);assertEquals(.1f,moved.nodes[1].x-b.x,.00001f)
        val camera=panDiagram(DiagramCamera(.5f,.5f,1f),Offset(0f,-200f),1f,Offset(100f,100f),300f,500f,1f)
        assertTrue(camera.cy>.5f);assertEquals(d.nodes,d.copy(camera=camera).nodes)
    }
    @Test fun allEightResizeHandlesUseOppositeBorderAndCenteredOptionAndUniformText() {
        val n=node(.4f,.4f).copy(w=.2f,h=.2f,font=32f)
        assertTrue(diagramResizeLimits(n.copy(x=0f),.02f,0f,DiagramHandle.RIGHT).isEmpty())
        assertEquals(setOf(DiagramHandle.LEFT),diagramResizeLimits(n.copy(x=0f),-.02f,0f,DiagramHandle.LEFT))
        DiagramHandle.entries.forEach {h->val r=resizeDiagramNode(n,h.fx*.04f,h.fy*.04f,h);assertTrue(r.w>=n.w);assertTrue(r.h>=n.h);assertEquals(n.font,r.font,0f);DiagramCodec.validate(DiagramDocument(nodes=listOf(r)))}
        val centered=resizeDiagramNode(n.copy(centeredResize=true),.03f,.05f,DiagramHandle.BR)
        assertEquals(n.x+n.w/2,centered.x+centered.w/2,.00001f);assertEquals(n.y+n.h/2,centered.y+centered.h/2,.00001f)
        val scaled=resizeDiagramNode(n.copy(lockAspect=true,resizeContent=DiagramResizeContent.SCALE),.1f,0f,DiagramHandle.RIGHT)
        assertEquals(n.w/n.h,scaled.w/scaled.h,.00001f);assertEquals(48f,scaled.font,.0001f)
        val image=n.copy(shape=DiagramShape.IMAGE,asset="unused",lockAspect=true);assertEquals(image.w/image.h,resizeDiagramNode(image,.1f,0f,DiagramHandle.RIGHT).let {it.w/it.h},.00001f)
    }
    @Test fun paperConversionPreservesPhysicalSizesAndExpansionDoesNotMoveWorldCoordinates() {
        val a=node(.1f,.1f);val d=DiagramDocument(nodes=listOf(a))
        DiagramPageFormat.entries.forEach {f->val changed=changeDiagramPage(d,DiagramPage(f,f.width,f.height));val n=changed.nodes.single();assertEquals(a.w*d.width,n.w*changed.width,.0001f);assertEquals(a.h*d.height,n.h*changed.height,.0001f);assertEquals(a.x*d.width,n.x*changed.width,.0001f);assertEquals(changed,DiagramCodec.decode(DiagramCodec.encode(changed)))}
        val expanded=changeDiagramPage(d,DiagramPage(width=2240,height=1400));assertEquals(a.y*d.height,expanded.nodes.single().y*expanded.height,.0001f)
        val outside=d.copy(nodes=listOf(a.copy(x=.8f)))
        assertTrue(runCatching {changeDiagramPage(outside,DiagramPage(DiagramPageFormat.STORY,900,1600))}.isFailure)
        val fitted=changeDiagramPage(outside,DiagramPage(DiagramPageFormat.STORY,900,1600),true);DiagramCodec.validate(fitted)
        assertEquals(a.w*d.width/(a.h*d.height),fitted.nodes.single().let {it.w*fitted.width/(it.h*fitted.height)},.0001f)
    }
    @Test fun exportProfilesRespectEachPaperRatioAndReferenceDimensionsAndGifBound() {
        val expected=mapOf(DiagramPageFormat.SQUARE to (1080 to 1080),DiagramPageFormat.STORY to (1080 to 1920),DiagramPageFormat.PORTRAIT to (1080 to 1350),DiagramPageFormat.WIDE to (1920 to 1080))
        expected.forEach {(f,pair)->val d=DiagramDocument(page=DiagramPage(f,f.width,f.height));assertEquals(pair,DiagramExportOptions(profile=DiagramExportProfile.HIGH).dimensions(d));for(format in DiagramFormat.entries) {val dims=DiagramExportOptions(format,profile=DiagramExportProfile.SHARE).dimensions(d);assertTrue(kotlin.math.abs(dims.first.toFloat()/dims.second-d.width.toFloat()/d.height)<.01f);assertTrue(maxOf(dims.first,dims.second)<=if(format==DiagramFormat.GIF)320 else 960)}}
    }
    @Test fun graphTraversalIgnoresCreationOrderAndRequiresBranchChoiceAndDefinesCycles() {
        val a=node();val b=node(.4f);val c=node(.7f);val ab=DiagramEdge(from=a.id,to=b.id,flow=DiagramFlow(enabled=true));val bc=ab.copy(id=diagramId(),from=b.id,to=c.id)
        val d=DiagramDocument(nodes=listOf(a,b,c),edges=listOf(bc,ab),playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL))
        assertEquals(listOf(ab.id,bc.id),diagramRoute(d).edges)
        val ac=ab.copy(id=diagramId(),to=c.id);val fork=d.copy(edges=d.edges+ac);assertEquals(setOf(ab.id,ac.id),diagramRoute(fork).choices.toSet())
        val cycle=d.copy(edges=d.edges+ab.copy(id=diagramId(),from=c.id,to=a.id));assertEquals(3,diagramRoute(cycle).choices.size)
        val chosen=cycle.copy(playback=cycle.playback.copy(route=listOf(ab.id)));assertEquals(3,diagramRoute(chosen).edges.size);assertTrue(diagramRoute(chosen).choices.isEmpty())
        assertTrue(runCatching {DiagramCodec.encode(d.copy(playback=d.playback.copy(route=listOf(bc.id,ab.id))))}.isFailure)
    }
    @Test fun globalModesPreserveConfigurationsAndSequentialParticleCrossesNodeWithoutRestart() {
        val a=node();val b=node(.45f);val c=node(.7f);val ab=DiagramEdge(from=a.id,to=b.id,flow=DiagramFlow(enabled=true,objectType=FlowObject.ICON,particleStyle=DiagramParticleStyle.LUMINOUS));val bc=ab.copy(id=diagramId(),from=b.id,to=c.id)
        val d=DiagramDocument(nodes=listOf(a,b,c),edges=listOf(ab,bc),playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL,loop=false))
        val timeline=diagramTimeline(d);assertEquals(1,timeline.particles(3.99f).size);assertEquals(1,timeline.particles(4.01f).size)
        val before=timeline.particles(3.99f).single();val after=timeline.particles(4.01f).single();assertTrue(kotlin.math.hypot(after.x-before.x,after.y-before.y)<10);assertEquals(before.flow.objectType,after.flow.objectType)
        assertTrue(timeline.particles(timeline.onceSeconds()+1).isEmpty())
        val looping=d.copy(playback=d.playback.copy(loop=true))
        assertEquals(diagramTimeline(looping).cycleSeconds,DiagramExportOptions(DiagramFormat.GIF,seconds=1).duration(looping),.0001f)
        val cycle=looping.copy(edges=looping.edges+ab.copy(id=diagramId(),from=c.id,to=a.id),playback=looping.playback.copy(route=listOf(ab.id)))
        val cyclic=diagramTimeline(cycle);val end=cyclic.particles(cyclic.cycleSeconds-.001f).single();val start=cyclic.particles(.001f).single()
        assertTrue(kotlin.math.hypot(end.x-start.x,end.y-start.y)<2)
        val off=d.copy(playback=d.playback.copy(mode=DiagramAnimationMode.OFF));assertTrue(diagramTimeline(off).particles(2f).isEmpty());assertEquals(d.edges,off.edges)
        assertEquals(2,diagramTimeline(d.copy(playback=DiagramPlayback())).particles(2f).size)
        assertEquals(bc.id,diagramTimeline(d.copy(playback=DiagramPlayback(DiagramAnimationMode.SEGMENT,listOf(bc.id)))).particles(2f).single().edge.id)
    }
    @Test fun imageObjectKeepsAssetInEveryModeAndOptionalHaloWorksEvenWithNormalStyle() {
        val image=Bitmap.createBitmap(16,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.TRANSPARENT);Canvas(image).drawRect(4f,2f,12f,6f,Paint().apply {color=Color.WHITE})
        val bytes=java.io.ByteArrayOutputStream().also {image.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();image.recycle()
        val id=DiagramCodec.hash(bytes);val raw=com.r0ybt.arachn0de.visual.canvasImage(bytes)
        val a=node(.1f);val b=node(.7f);val e=DiagramEdge(from=a.id,to=b.id,flow=DiagramFlow(enabled=true,objectType=FlowObject.IMAGE,asset=id,size=40f,color=Color.CYAN))
        val d=DiagramDocument(background=0,nodes=listOf(a,b),edges=listOf(e))
        listOf(DiagramAnimationMode.SEGMENT,DiagramAnimationMode.SEQUENTIAL,DiagramAnimationMode.SIMULTANEOUS).forEach {mode->val changed=d.copy(playback=DiagramPlayback(mode,listOf(e.id)));assertEquals(id,diagramTimeline(changed).particles(2f).single().flow.asset);assertEquals(changed,DiagramCodec.decode(DiagramCodec.encode(changed)))}
        val bitmap=Bitmap.createBitmap(800,500,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(mapOf(id to raw)).use {r->renderDiagramFrame(r,d,bitmap,2f);val without=bitmap.copy(Bitmap.Config.ARGB_8888,false);renderDiagramFrame(r,d.copy(edges=listOf(e.copy(flow=e.flow.copy(imageHalo=true)))),bitmap,2f);assertFalse(without.sameAs(bitmap));without.recycle()}} finally {bitmap.recycle()}
    }
    @Test fun preflightRejectsAmbiguousAnimationLongOnceAndImageLabelClipping() {
        val d=diagramTemplate(3);val a=d.nodes.first();val branch=d.edges.first().copy(id=diagramId(),to=d.nodes.last().id)
        assertTrue(runCatching {DiagramExportOptions(DiagramFormat.GIF).validateDocument(d.copy(edges=d.edges+branch,playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL)))}.isFailure)
        val nodes=(0..9).map {node(.1f+it%3*.3f,.05f+it/3*.2f)};val edges=nodes.zipWithNext {from,to->DiagramEdge(from=from.id,to=to.id,flow=DiagramFlow(enabled=true,speed=.5f))}
        val long=DiagramDocument(nodes=nodes,edges=edges,playback=DiagramPlayback(DiagramAnimationMode.SEQUENTIAL,loop=false))
        assertTrue(runCatching {DiagramExportOptions(DiagramFormat.MP4).validateDocument(long)}.isFailure)
        DiagramExportOptions(DiagramFormat.PNG).validateDocument(long)
        val image=a.copy(shape=DiagramShape.IMAGE,asset="fixture",y=.92f,h=.08f,text="Etiqueta")
        assertTrue(runCatching {DiagramExportOptions().validateDocument(DiagramDocument(nodes=listOf(image)))}.isFailure)
    }
    @Test fun legacyFieldsRemainHistoricalAndInvalidExtensionsAreRejected() {
        val n=node().copy(fill=Color.WHITE,ink=Color.BLACK);val j=JSONObject(DiagramCodec.encode(DiagramDocument(nodes=listOf(n),background=Color.WHITE)))
        listOf("page","playback","backgroundGradient").forEach(j::remove)
        val nj=j.getJSONArray("nodes").getJSONObject(0);listOf("borderColor","radius","font","lockAspect","centeredResize","resizeContent","gradient").forEach(nj::remove)
        val old=DiagramCodec.decode(j.toString());assertEquals(Color.WHITE,old.background);assertEquals(Color.BLACK,old.nodes.single().border);assertEquals(48f,old.nodes.single().font,0f);assertEquals(1600,old.width)
        nj.put("font",true);assertTrue(runCatching {DiagramCodec.decode(j.toString())}.isFailure)
        assertTrue(runCatching {DiagramCodec.encode(DiagramDocument(backgroundGradient=DiagramGradient(listOf(Color.RED))))}.isFailure)
        assertTrue(runCatching {DiagramCodec.library(mapOf("favorite_colors" to "[1,1]"))}.isFailure)
    }
    @Test fun gradientAndGlowShareRendererAcrossPngAndAnimatedFramesAndPulseChangesPixels() {
        val a=node();val b=node(.7f);val e=DiagramEdge(from=a.id,to=b.id,flow=DiagramFlow(enabled=true,particleStyle=DiagramParticleStyle.PULSE,color=Color.CYAN,haloColor=Color.MAGENTA,size=12f))
        val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(e),backgroundGradient=DiagramGradient(listOf(DIAGRAM_COAL,Color.BLUE),0f))
        val bitmap=Bitmap.createBitmap(800,500,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(emptyMap()).use {r->renderDiagramFrame(r,d,bitmap,null);assertNotEquals(bitmap.getPixel(0,0),bitmap.getPixel(799,0));val static=bitmap.copy(Bitmap.Config.ARGB_8888,false);renderDiagramFrame(r,d,bitmap,2f);assertFalse(static.sameAs(bitmap));static.recycle();val out=java.io.ByteArrayOutputStream();r.png(d,out,800,500);assertTrue(out.size()>0)}} finally {bitmap.recycle()}
    }
}
