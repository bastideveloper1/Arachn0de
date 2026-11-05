package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramRefinementTest {
    @Test fun fitContainsNodesCurvesAndImageLabelsAndPanelResizePreservesScaleAndCenter() {
        val base=diagramTemplate(1)
        val d=base.copy(edges=base.edges.map {it.copy(route=DiagramRoute.CURVE,points=listOf(DPoint(.45f,.12f)))})
        val camera=fitDiagram(d,360f,600f);val bounds=diagramBounds(d)
        val v=diagramViewport(360f,600f,1f,camera)
        assertTrue(v.left+bounds.left/DIAGRAM_W*v.width>=15f)
        assertTrue(v.top+bounds.top/DIAGRAM_H*v.height>=15f)
        assertTrue(v.left+bounds.right/DIAGRAM_W*v.width<=345f)
        assertTrue(v.top+bounds.bottom/DIAGRAM_H*v.height<=585f)
        val small=diagramViewport(360f,240f,1f,camera)
        assertEquals(v.width,small.width,0f);assertEquals(v.height,small.height,0f)
        assertEquals(120f,small.top+camera.cy*small.height,.001f)
        val landscape=diagramViewport(700f,240f,1f,camera)
        assertEquals(v.width,landscape.width,0f)
        assertEquals(350f,landscape.left+camera.cx*landscape.width,.001f)
    }
    @Test fun pinchPreservesWorldPointAndDensityAndCameraAreRoundTripped() {
        val c=fitDiagram(diagramTemplate(3),400f,650f);val at=Offset(123f,250f)
        val v=diagramViewport(400f,650f,1f,c)
        val next=panDiagram(c,Offset.Zero,1.8f,at,400f,650f,1f)
        val nv=diagramViewport(400f,650f,1f,next)
        assertEquals((at.x-v.left)/v.width,(at.x-nv.left)/nv.width,.00001f)
        assertEquals((at.y-v.top)/v.height,(at.y-nv.top)/nv.height,.00001f)
        val d=diagramTemplate(2).copy(camera=next)
        assertEquals(d,DiagramCodec.decode(DiagramCodec.encode(d)))
        val old=org.json.JSONObject(DiagramCodec.encode(d)).apply {remove("camera")}.toString()
        assertNull(DiagramCodec.decode(old).camera)
        val invalid=org.json.JSONObject(DiagramCodec.encode(d));invalid.getJSONObject("camera").put("scale",-1)
        assertThrows(IllegalArgumentException::class.java) {DiagramCodec.decode(invalid.toString())}
    }
    @Test fun durationIncludesEveryOneShotParticleAndTimeIsSharedAcrossSpeeds() {
        val f=DiagramFlow(enabled=true,direction=FlowDirection.BOUNCE,count=8,speed=.5f,loop=false)
        assertEquals(23f,flowEnd(f),0f)
        val loop=f.copy(loop=true,direction=FlowDirection.FORWARD)
        repeat(loop.count) {assertEquals(flowProgress(0f,loop,it),flowProgress(8f,loop,it))}
        val d=diagramTemplate(3).let {it.copy(edges=it.edges.map {e->e.copy(flow=f)})}
        assertEquals(23f,DiagramExportOptions(DiagramFormat.GIF,320,4).duration(d),0f)
        assertNotNull(flowProgress(22.9f,f,7));assertNull(flowProgress(23.1f,f,7))
        assertEquals(.25f,flowProgress(1f,f.copy(speed=1f,direction=FlowDirection.FORWARD,loop=true,count=1),0)!!,.0001f)
        assertEquals(.5f,flowProgress(1f,f.copy(speed=2f,direction=FlowDirection.FORWARD,loop=true,count=1),0)!!,.0001f)
    }
    @Test fun actualGifDecodesDifferentFramesAlphaDelayAndLoopWithoutEditorControls() {
        val d=diagramTemplate(3).let {it.copy(background=0,edges=it.edges.map {e->e.copy(flow=e.flow.copy(size=90f))})}
        val out=ByteArrayOutputStream();val frame=Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(emptyMap()).use {r->DiagramGif(out,320,200,true).use {gif->
            listOf(0f,1f,2f).forEach {t->renderDiagramFrame(r,d,frame,t);gif.frame(frame)}
        }}} finally {frame.recycle()}
        ImageIO.createImageInputStream(out.toByteArray().inputStream()).use {input->
            val reader=ImageIO.getImageReadersByFormatName("gif").next();reader.input=input
            try {
                assertEquals(3,reader.getNumImages(true));assertEquals(320,reader.getWidth(0));assertEquals(200,reader.getHeight(0))
                val first=reader.read(0);val second=reader.read(1)
                assertEquals(0,first.getRGB(0,0) ushr 24)
                assertTrue((0 until 200).any {y->(0 until 320).any {x->first.getRGB(x,y)!=second.getRGB(x,y)}})
                val tree=reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0")
                fun find(node:org.w3c.dom.Node,name:String):org.w3c.dom.Node? {if(node.nodeName==name)return node;var child=node.firstChild;while(child!=null) {find(child,name)?.let {return it};child=child.nextSibling};return null}
                assertEquals("10",find(tree,"GraphicControlExtension")!!.attributes.getNamedItem("delayTime").nodeValue)
                assertTrue(out.toByteArray().toString(Charsets.ISO_8859_1).contains("NETSCAPE2.0"))
            } finally {reader.dispose()}
        }
        val noLoop=ByteArrayOutputStream();DiagramGif(noLoop,2,2,false).close()
        assertFalse(noLoop.toString("ISO-8859-1").contains("NETSCAPE2.0"))
    }
    @Test fun animatedCustomImageRetainsAspectAlphaAndGeometryFollowsNodeOnEveryRoute() {
        val icon=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888)
        icon.eraseColor(Color.TRANSPARENT);icon.setPixel(20,10,Color.RED)
        val raw=ByteArrayOutputStream().also {icon.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();icon.recycle()
        val id=DiagramCodec.hash(raw);val encoded=android.util.Base64.encodeToString(raw,android.util.Base64.NO_WRAP)
        val output=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(mapOf(id to encoded)).use {renderer->
            DiagramRoute.entries.forEach {route->
                val d=diagramTemplate(3).let {it.copy(background=0,edges=it.edges.map {e->e.copy(route=route,flow=e.flow.copy(objectType=FlowObject.IMAGE,asset=id,size=90f))})}
                renderDiagramFrame(renderer,d,output,1f)
                val path=android.graphics.PathMeasure(edgePath(d,d.edges.first()),false);val p=FloatArray(2);path.getPosTan(path.length*.25f,p,null)
                val x=(p[0]*.4f).toInt();val y=(p[1]*.4f).toInt()
                assertTrue((x-3..x+3).any {xx->(y-3..y+3).any {yy->Color.red(output.getPixel(xx,yy))>180&&Color.green(output.getPixel(xx,yy))<80}})
                val moved=d.copy(nodes=d.nodes.mapIndexed {i,n->if(i==1)n.copy(y=.7f) else n})
                val newPath=android.graphics.PathMeasure(edgePath(moved,moved.edges.first()),false)
                assertNotEquals(path.length,newPath.length)
            }
        }} finally {output.recycle()}
    }
    @Test fun videoYuvUsesCodecRowAndPixelStridesWithoutWritingPadding() {
        val b=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED)
        val planes=listOf(DiagramYuvPlane(java.nio.ByteBuffer.allocate(32),8,1),DiagramYuvPlane(java.nio.ByteBuffer.allocate(16),8,2),DiagramYuvPlane(java.nio.ByteBuffer.allocate(16),8,2))
        try {writeDiagramYuvPlanes(b,planes)} finally {b.recycle()}
        assertEquals(82,planes[0].buffer.get(0).toInt() and 255)
        assertEquals(90,planes[1].buffer.get(0).toInt() and 255)
        assertEquals(240,planes[2].buffer.get(0).toInt() and 255)
        assertEquals(82,planes[0].buffer.get(27).toInt() and 255)
        assertEquals(90,planes[1].buffer.get(10).toInt() and 255)
        assertEquals(0,planes[0].buffer.get(4).toInt());assertEquals(0,planes[1].buffer.get(1).toInt())
    }
    @Test fun videoFrameFlattensAlphaAndOutputLimitFailsBeforeOvershoot() {
        val frame=Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888)
        try {DiagramRenderer(emptyMap()).use {renderDiagramFrame(it,DiagramDocument(background=0),frame,0f,true)};assertEquals(Color.WHITE,frame.getPixel(0,0))} finally {frame.recycle()}
        val bytes=ByteArrayOutputStream();val stream=BoundedDiagramOutput(bytes,4)
        stream.write(byteArrayOf(1,2,3));assertThrows(IllegalStateException::class.java) {stream.write(byteArrayOf(4,5))}
        assertEquals(3,bytes.size());assertEquals(3L,stream.count)
    }
    @Test fun gifPreservesSolidColorsAndOpaqueBlackInExactAndFallbackPalettes() {
        val colors=listOf(Color.BLACK,Color.WHITE,Color.RED,Color.GREEN,Color.BLUE,0xff1976d2.toInt(),0xffa07030.toInt(),Color.TRANSPARENT)
        val bitmap=Bitmap.createBitmap(64,16,Bitmap.Config.ARGB_8888)
        val out=ByteArrayOutputStream()
        try {DiagramGif(out,64,16,true).use {gif->
            bitmap.setPixels(IntArray(64*16) {colors[it%colors.size]},0,64,0,0,64,16)
            gif.frame(bitmap,10)
            bitmap.setPixels(IntArray(64*16) {i->Color.rgb((i*37)%256,(i/4)%256,(i*17)%256)},0,64,0,0,64,16)
            bitmap.setPixel(0,0,Color.BLACK);bitmap.setPixel(1,0,Color.WHITE)
            gif.frame(bitmap,20)
        }} finally {bitmap.recycle()}
        java.io.File("/tmp/diagram-solid-colors.gif").writeBytes(out.toByteArray())
        ImageIO.createImageInputStream(out.toByteArray().inputStream()).use {input->
            val reader=ImageIO.getImageReadersByFormatName("gif").next();reader.input=input
            try {
                assertEquals(2,reader.getNumImages(true));assertEquals(64,reader.getWidth(0));assertEquals(16,reader.getHeight(0))
                val exact=reader.read(0)
                colors.forEachIndexed {i,color->assertEquals(color,exact.getRGB(i,0))}
                val fallback=reader.read(1)
                assertEquals(Color.BLACK,fallback.getRGB(0,0));assertEquals(Color.WHITE,fallback.getRGB(1,0))
            } finally {reader.dispose()}
        }
    }

}
