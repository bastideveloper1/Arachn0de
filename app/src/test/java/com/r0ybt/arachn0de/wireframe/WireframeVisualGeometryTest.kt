package com.r0ybt.arachn0de.wireframe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class WireframeVisualGeometryTest {
    @Test fun compositionModesUseDocumentCoordinatesAndUniformSquareCells() {
        assertTrue(wireCompositionLines(WireComposition.NONE,1080f,2340f).isEmpty())
        val center=wireCompositionLines(WireComposition.CENTER,1080f,2340f)
        assertEquals(2,center.size);assertEquals(.5f,center.first().first.x,0f);assertEquals(.5f,center.last().first.y,0f)
        val thirds=wireCompositionLines(WireComposition.THIRDS,1920f,1080f)
        assertEquals(4,thirds.size);assertEquals(1f/3,thirds.first().first.x,.00001f)
        for(format in WireFormat.entries) {
            val grid=wireCompositionLines(WireComposition.GRID,format.width.toFloat(),format.height.toFloat())
            val x=grid.filter {it.first.x==it.second.x};val y=grid.filter {it.first.y==it.second.y}
            assertEquals(90f,(x[1].first.x-x[0].first.x)*format.width,.0001f)
            assertEquals(90f,(y[1].first.y-y[0].first.y)*format.height,.0001f)
            assertTrue(grid.all {it.first.x in 0f..1f && it.first.y in 0f..1f && it.second.x in 0f..1f && it.second.y in 0f..1f})
        }
    }
    @Test fun colorChannelsPreserveArgbAndClampComponents() {
        assertEquals(0x80abcdefL,wireColorChannel(0x12abcdefL,24,128))
        assertEquals(0x1278cdefL,wireColorChannel(0x12abcdefL,16,120))
        assertEquals(0x12ab00efL,wireColorChannel(0x12abcdefL,8,-1))
        assertEquals(0x12abcdffL,wireColorChannel(0x12abcdefL,0,999))
        assertTrue(WireColorPalette.all {it.second in 0..0xffffffffL})
        assertEquals(0xffffffffL,wireParseColor("FFFFFFFF"));assertEquals(0x1234abcdL,wireParseColor("1234abcd"))
        assertNull(wireParseColor("+1234567"));assertNull(wireParseColor("#FFFFFFF"));assertNull(wireParseColor("GGGGGGGG"))
    }
    @Test fun closestEdgesCentersAndMagnetAreIndependent() {
        val other=WireElement(x=.2f,y=.15f,w=.2f,h=.1f)
        val moved=WireElement(x=.203f,y=.55f,w=.1f,h=.1f)
        val doc=WireDocument(elements=listOf(other,moved))
        val free=wireSmartAlign(doc,moved,.01f,.01f,false)
        assertEquals(moved,free.element);assertEquals(.2f,free.vertical!!,.00001f)
        val snap=wireSmartAlign(doc,moved,.01f,.01f,true)
        assertEquals(.2f,snap.element.x,.00001f)
        val right=wireSmartAlign(doc,moved.copy(x=.299f),.01f,.01f,true)
        assertEquals(.4f,right.vertical!!,.00001f);assertEquals(.3f,right.element.x,.00001f)
        val center=wireSmartAlign(doc,moved.copy(x=.251f),.01f,.01f,true)
        assertEquals(.3f,center.vertical!!,.00001f);assertEquals(.25f,center.element.x,.00001f)
        val canvas=wireSmartAlign(doc,moved.copy(x=.451f,y=.451f),.01f,.01f,true)
        assertEquals(.5f,canvas.vertical!!,.00001f);assertEquals(.5f,canvas.horizontal!!,.00001f)
        val released=wireSmartAlign(doc,moved.copy(x=.65f,y=.7f),.01f,.01f,true)
        assertNull(released.vertical);assertNull(released.horizontal);assertEquals(.65f,released.element.x,0f)
    }
    @Test fun equalGapsUseDocumentPixelsAndOnlyTwoMeasurements() {
        val e=WireElement(x=.3f,y=.4f,w=.2f,h=.1f)
        val above=WireElement(x=.3f,y=.2f,w=.2f,h=.1f)
        val below=WireElement(x=.3f,y=.6f,w=.2f,h=.1f)
        val doc=WireDocument(format=WireFormat.DESKTOP,elements=listOf(above,e,below))
        val result=wireSmartAlign(doc,e,.01f,.01f,false)
        assertEquals(2,result.distances.size)
        assertTrue(result.distances.all {it.equal})
        result.distances.forEach {assertEquals(108f,it.pixels,.0001f)}
        val uneven=wireSmartAlign(doc.copy(elements=listOf(above,e,below.copy(y=.64f))),e,.01f,.01f,false)
        assertFalse(uneven.distances.any {it.equal})
    }
    @Test fun viewportKeepsDecorationsOutsideSheetAndMapsGesturesExactly() {
        for(format in WireFormat.entries) for(size in listOf(IntSize(360,500),IntSize(360,780),IntSize(780,360),IntSize(320,260),IntSize(200,140))) {
            for(density in listOf(1f,2f)) for(device in listOf(false,true)) for(tools in listOf(false,true)) {
                val view=wireViewport(size,format,density,device,tools)
                assertTrue(view.scale>0)
                assertTrue(view.frame.left>=-.001f && view.frame.top>=-.001f && view.frame.right<=size.width+.001f && view.frame.bottom<=size.height+.001f)
                assertTrue(view.sheet.left>=view.frame.left && view.sheet.right<=view.frame.right)
                view.toolbar?.let {bar->
                    assertFalse("Tools overlap $format $size",bar.overlaps(view.frame))
                    assertTrue(Rect(0f,0f,size.width.toFloat(),size.height.toFloat()).contains(bar.center))
                }
                listOf(Offset.Zero,Offset(.37f,.83f),Offset(1f,1f)).forEach {point->
                    val restored=view.document(view.screen(point))
                    assertEquals(point.x,restored.x,.00001f);assertEquals(point.y,restored.y,.00001f)
                }
            }
        }
    }
    @Test fun thresholdIsMeasuredInScreenPixelsRegardlessOfScale() {
        val e=WireElement(x=.3f,y=.3f,w=.2f,h=.1f)
        val doc=WireDocument(elements=listOf(e))
        for(size in listOf(IntSize(360,600),IntSize(720,1200))) {
            val view=wireViewport(size,doc.format,1f,true,true)
            val tx=6f/(view.scale*view.width);val ty=6f/(view.scale*view.height)
            val near=e.copy(x=.4f+tx*.8f)
            val far=e.copy(x=.4f+tx*1.2f)
            assertEquals(.4f,wireSmartAlign(doc,near,tx,ty,true).element.x,.00001f)
            assertEquals(far.x,wireSmartAlign(doc,far,tx,ty,true).element.x,0f)
        }
    }
}
