package com.r0ybt.arachn0de.diagrams
import android.app.Application
import android.graphics.PathMeasure
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramTouchTest {
    @Test fun anchorsRoundtripKeepOldPathsAndValidate() {
        val old=diagramTemplate(1);val raw=JSONObject(DiagramCodec.encode(old))
        for(i in 0 until raw.getJSONArray("edges").length()) {raw.getJSONArray("edges").getJSONObject(i).remove("fromAnchor");raw.getJSONArray("edges").getJSONObject(i).remove("toAnchor")}
        assertEquals(old,DiagramCodec.decode(raw.toString()))
        val d=old.copy(edges=old.edges.map {it.copy(fromAnchor=DiagramAnchor.TOP,toAnchor=DiagramAnchor.BOTTOM)})
        assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))
        val m=PathMeasure(edgePath(d,d.edges.first()),false);val p=FloatArray(2)
        m.getPosTan(0f,p,null);assertEquals(d.nodes.first().y*d.height,p[1],.01f)
        m.getPosTan(m.length,p,null);assertEquals((d.nodes[1].y+d.nodes[1].h)*d.height,p[1],.01f)
        val bad=JSONObject(DiagramCodec.encode(d));bad.getJSONArray("edges").getJSONObject(0).put("toAnchor","INVALID")
        assertTrue(runCatching {DiagramCodec.decode(bad.toString())}.isFailure)
    }
    @Test fun retargetPreservesStylesIdentityAndUndo() {
        val base=diagramTemplate(3);val e=base.edges.first().copy(route=DiagramRoute.CURVE,stroke=DiagramStroke.DASHED,points=listOf(DPoint(.5f,.2f)));val d=base.copy(edges=listOf(e)+base.edges.drop(1));val next=reconnectDiagram(d,e,false,d.nodes.last().id,DiagramAnchor.LEFT)
        assertEquals(e.copy(to=d.nodes.last().id,toAnchor=DiagramAnchor.LEFT),next.edges.first())
        val h=DiagramHistory(d);h.commit(next);assertEquals(d,h.undo());assertEquals(next,h.redo())
        assertEquals(d,reconnectDiagram(d,e,false,e.from,DiagramAnchor.TOP))
    }
    @Test fun centersEdgesAboveResizeAndIndependentGrid() {
        val other=DiagramNode(x=.12f,y=.1f,w=.16f,h=.1f);val n=DiagramNode(x=.4f,y=.4f,w=.1f,h=.1f);val d=DiagramDocument(nodes=listOf(other,n))
        val moved=diagramGestureAlign(d,n,-.198f,0f,null,false,.025f,true,.004f,.004f)
        assertEquals(.2f,moved.first.x,.00001f);assertEquals(.2f,moved.second!!,.00001f)
        val resized=diagramGestureAlign(d,n,0f,-.198f,DiagramHandle.TOP,false,.025f,true,.004f,.004f)
        assertEquals(.2f,resized.first.y,.00001f);assertEquals(.5f,resized.first.y+resized.first.h,.00001f);assertEquals(.2f,resized.third!!,.00001f)
        val free=diagramGestureAlign(d,n,-.198f,0f,null,false,.025f,false,.004f,.004f)
        assertEquals(.202f,free.first.x,.00001f);assertNotNull(free.second)
        val grid=diagramGestureAlign(d,n,.014f,.014f,null,true,.025f,false,.004f,.004f)
        assertEquals(.425f,grid.first.x,.00001f)
        for(width in listOf(400f,800f,1600f))assertEquals(.2f,diagramGestureAlign(d,n,-.2f+3f/width,0f,null,false,.025f,true,8/width,8/width).first.x,.00001f)
    }
}
