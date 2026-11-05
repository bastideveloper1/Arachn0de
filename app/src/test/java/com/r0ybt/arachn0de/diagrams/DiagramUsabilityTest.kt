package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramUsabilityTest {
    @Test fun automaticConnectionsUseFacingSidesInAllFourDirectionsWithoutDetours() {
        val a=DiagramNode(x=.4f,y=.4f,w=.1f,h=.1f)
        listOf(Triple(.4f,.7f,DiagramAnchor.BOTTOM),Triple(.4f,.1f,DiagramAnchor.TOP),Triple(.7f,.4f,DiagramAnchor.RIGHT),Triple(.1f,.4f,DiagramAnchor.LEFT)).forEach {(x,y,side)->
            val b=a.copy(id=diagramId(),x=x,y=y)
            val other=when(side) {DiagramAnchor.TOP->DiagramAnchor.BOTTOM;DiagramAnchor.BOTTOM->DiagramAnchor.TOP;DiagramAnchor.LEFT->DiagramAnchor.RIGHT;DiagramAnchor.RIGHT->DiagramAnchor.LEFT}
            DiagramRoute.entries.forEach {route->
                val e=DiagramEdge(from=a.id,to=b.id,route=route)
                val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(e))
                val path=edgePath(d,e);val measure=PathMeasure(path,false);val out=FloatArray(2)
                val start=diagramAnchorPoint(a,side);val end=diagramAnchorPoint(b,other)
                measure.getPosTan(0f,out,null);assertEquals(start.x*d.width,out[0],.01f);assertEquals(start.y*d.height,out[1],.01f)
                measure.getPosTan(measure.length,out,null);assertEquals(end.x*d.width,out[0],.01f);assertEquals(end.y*d.height,out[1],.01f)
                val distance=kotlin.math.hypot((start.x-end.x)*d.width,(start.y-end.y)*d.height)
                if(route!=DiagramRoute.CURVE)assertEquals("No detour for $side / $route",distance,measure.length,.1f)
            }
        }
    }
    @Test fun explicitAnchorsAndWaypointsRemainAuthoritativeAfterLegacyRoundTrip() {
        val a=DiagramNode(x=.2f,y=.2f);val b=DiagramNode(x=.6f,y=.6f)
        val e=DiagramEdge(from=a.id,to=b.id,route=DiagramRoute.ORTHOGONAL,fromAnchor=DiagramAnchor.LEFT,toAnchor=DiagramAnchor.RIGHT,points=listOf(DPoint(.1f,.5f)))
        val d=DiagramCodec.decode(DiagramCodec.encode(DiagramDocument(nodes=listOf(a,b),edges=listOf(e))))
        val out=FloatArray(2);val m=PathMeasure(edgePath(d,d.edges.single()),false)
        m.getPosTan(0f,out,null);assertEquals(a.x*d.width,out[0],.01f)
        m.getPosTan(m.length,out,null);assertEquals((b.x+b.w)*d.width,out[0],.01f)
        assertEquals(e.points,d.edges.single().points)
        val legacy=e.copy(fromAnchor=null,toAnchor=null)
        assertEquals(null,DiagramCodec.decode(DiagramCodec.encode(d.copy(edges=listOf(legacy)))).edges.single().fromAnchor)
    }
    @Test fun movingAllTranslatesWaypointsRigidlyAndSupportsHistoryAndReopening()=runBlocking {
        val a=DiagramNode(x=.2f,y=.2f);val b=DiagramNode(x=.5f,y=.5f)
        val e=DiagramEdge(from=a.id,to=b.id,points=listOf(DPoint(.45f,.4f)))
        val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(e),camera=DiagramCamera(.4f,.4f,.6f))
        val moved=translateDiagramContent(d,.1f,.1f)
        assertEquals(.55f,moved.edges.single().points.single().x,.00001f)
        assertEquals(b.x-a.x,moved.nodes[1].x-moved.nodes[0].x,.00001f)
        assertEquals(a.w,moved.nodes[0].w,0f);assertEquals(d.camera,moved.camera)
        val history=DiagramHistory(d);history.commit(moved);assertEquals(d,history.undo());assertEquals(moved,history.redo())
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val name="polish-${diagramId()}";val prefs=context.getSharedPreferences(name,0)
        try {DiagramRepository(prefs).save(moved,null);assertEquals(moved,DiagramRepository(context.getSharedPreferences(name,0)).load(d.id).first)} finally {prefs.edit().clear().commit()}
    }
    @Test fun centeringUsesExportablePageAndPreservesRelativeGeometryAtLimits() {
        val a=DiagramNode(x=.1f,y=.1f,w=.1f,h=.1f);val b=a.copy(id=diagramId(),x=.3f,y=.3f)
        val d=DiagramDocument(nodes=listOf(a,b),edges=listOf(DiagramEdge(from=a.id,to=b.id,points=listOf(DPoint(.25f,.3f)))))
        val centered=centerDiagramContent(d);val bnd=diagramBounds(centered)
        assertEquals(d.width/2f,bnd.centerX(),.01f);assertEquals(d.height/2f,bnd.centerY(),.01f)
        val limited=translateDiagramContent(d,2f,-2f)
        assertEquals(.2f,limited.nodes[1].x-limited.nodes[0].x,.00001f)
        assertEquals(.2f,limited.nodes[1].y-limited.nodes[0].y,.00001f)
        DiagramCodec.validate(limited)
    }

    @Test fun exportedPngAndGifUseWholePageRatherThanEditorCameraAfterCentering() {
        val base=diagramTemplate(3,true)
        val d=centerDiagramContent(base.copy(camera=DiagramCamera(-.8f,1.8f,6f)))
        val options=DiagramExportOptions(DiagramFormat.PNG,320)
        val (w,h)=options.dimensions(d)
        val frame=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
        val other=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            DiagramRenderer(emptyMap()).use {r->renderDiagramFrame(r,d,frame,0f);renderDiagramFrame(r,d.copy(camera=null),other,0f)}
            assertTrue(frame.sameAs(other))
            val png=java.io.ByteArrayOutputStream();assertTrue(frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png))
            val decoded=android.graphics.BitmapFactory.decodeByteArray(png.toByteArray(),0,png.size())
            try {assertEquals(w,decoded.width);assertEquals(h,decoded.height);assertTrue(decoded.sameAs(frame))} finally {decoded.recycle()}
            val gif=java.io.ByteArrayOutputStream()
            DiagramGif(gif,w,h,true).use {g->g.frame(frame);DiagramRenderer(emptyMap()).use {renderDiagramFrame(it,d,frame,1f)};g.frame(frame)}
            javax.imageio.ImageIO.createImageInputStream(gif.toByteArray().inputStream()).use {input->
                val reader=javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();reader.input=input
                try {assertEquals(2,reader.getNumImages(true));assertEquals(w,reader.getWidth(0));assertEquals(h,reader.getHeight(0))} finally {reader.dispose()}
            }
            val bounds=diagramBounds(d)
            assertTrue(bounds.left>=-.01f&&bounds.top>=-.01f&&bounds.right<=d.width+.01f&&bounds.bottom<=d.height+.01f)
        } finally {frame.recycle();other.recycle()}
    }
}
