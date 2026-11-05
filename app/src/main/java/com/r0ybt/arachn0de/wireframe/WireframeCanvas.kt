package com.r0ybt.arachn0de.wireframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize

internal fun wireHit(document:WireDocument,x:Float,y:Float)=document.elements.lastOrNull {x in it.x..it.x+it.w && y in it.y..it.y+it.h}

/** Gesture drafts remain local until pointer up: one gesture is one undo operation. */
internal fun wireTransform(e:WireElement,dx:Float,dy:Float,resize:Boolean):WireElement {
    if(!resize) return e.copy(x=(e.x+dx).coerceIn(0f,1-e.w),y=(e.y+dy).coerceIn(0f,1-e.h))
    val minW=minOf(e.w,.035f);val minH=minOf(e.h,.02f)
    if(e.lockAspect) {
        val ratio=maxOf(1+dx/e.w,1+dy/e.h).coerceIn(maxOf(minW/e.w,minH/e.h),minOf((1-e.x)/e.w,(1-e.y)/e.h))
        return wireScaleContent(e,e.copy(w=e.w*ratio,h=e.h*ratio))
    }
    return wireScaleContent(e,e.copy(w=(e.w+dx).coerceIn(minW,1-e.x),h=(e.h+dy).coerceIn(minH,1-e.y)))
}
internal fun wireAlign(e:WireElement,toleranceX:Float,toleranceY:Float):WireElement = e.copy(
    x=if(abs(e.x+e.w/2-.5f)<=toleranceX) (1-e.w)/2 else e.x,
    y=if(abs(e.y+e.h/2-.5f)<=toleranceY) (1-e.h)/2 else e.y)

@Composable internal fun WireCanvas(document:WireDocument,renderer:WireframeRenderer,selected:String?,enabled:Boolean,onSelect:(String?)->Unit,onChange:(WireDocument)->Unit,
    showGuides:Boolean=false,magnetic:Boolean=showGuides,showMargins:Boolean=false,showDevice:Boolean=false,toolbarVisible:Boolean=false,composition:WireComposition=WireComposition.NONE,onCompositionChange:(WireComposition)->Unit={},showAnnotations:Boolean=true,annotationMode:Boolean=false,placingAnnotation:Boolean=false,selectedAnnotation:String?=null,onAnnotationSelect:(String?)->Unit={},onAnnotationPlace:(Offset)->Unit={},onVisualToggle:(WireVisualTool)->Unit={}) {
    var size by remember {mutableStateOf(IntSize.Zero)}
    var active by remember {mutableStateOf<String?>(null)}
    var alignment by remember {mutableStateOf<WireAlignment?>(null)}
    val magnet by rememberUpdatedState(magnetic)
    var draft by remember {mutableStateOf<WireDocument?>(null)}
    val current by rememberUpdatedState(document);val select by rememberUpdatedState(onSelect)
    val selection by rememberUpdatedState(selected);val commit by rememberUpdatedState(onChange)
    val annotationSelect by rememberUpdatedState(onAnnotationSelect)
    val annotationPlace by rememberUpdatedState(onAnnotationPlace)
    val density=LocalDensity.current
    val snapPixels=with(density) {6.dp.toPx()}
    val palette=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current
    val guideColor=palette.primary.copy(alpha=.8f)
    val displayed=draft ?: document
    val viewport=wireViewport(size,document.format,density.density,showDevice,toolbarVisible)
    Box(Modifier.fillMaxSize().onSizeChanged {size=it}) {
    Canvas(Modifier.fillMaxSize().testTag("wireframe-canvas")
        .pointerInput(enabled,document.format,size,showDevice,toolbarVisible,annotationMode,placingAnnotation,showAnnotations) {
            if(enabled) awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val original=current
                val scale=viewport.scale
                if(scale<=0) return@awaitEachGesture
                val point=viewport.document(down.position)
                if(annotationMode) {
                    if(!viewport.sheet.contains(down.position)) return@awaitEachGesture
                    if(placingAnnotation) {down.consume();annotationPlace(point);return@awaitEachGesture}
                    val annotation=if(showAnnotations) original.annotations.asReversed().firstOrNull {a->
                        val p=viewport.screen(a.position(original));(p-down.position).getDistance()<=with(density) {20.dp.toPx()}
                    } else null
                    if(annotation!=null) {
                        down.consume();var delta=Offset.Zero;var next=original
                        try {
                            val completed=drag(down.id) {event->
                                delta+=event.positionChange();event.consume()
                                val moved=annotation.at(original,annotation.position(original)+Offset(delta.x/(scale*viewport.width),delta.y/(scale*viewport.height)))
                                next=original.copy(annotations=original.annotations.map {if(it.id==annotation.id) moved else it});draft=next
                            }
                            if(completed) {if(next!=original) commit(next);annotationSelect(annotation.id)}
                        } finally {draft=null}
                    } else annotationSelect(null)
                    return@awaitEachGesture
                }
                val chosen=original.elements.firstOrNull {it.id==selection}
                val tolerance=chosen?.let {minOf(with(density) {14.dp.toPx()},it.w*viewport.width*scale*.28f,it.h*viewport.height*scale*.28f)} ?: 0f
                val handle=chosen?.let {wireHandleAt(it,point,viewport.width*scale,viewport.height*scale,tolerance)}
                val resizing=handle!=null
                if(!viewport.sheet.contains(down.position) && !resizing) return@awaitEachGesture
                val hit=if(resizing) chosen else wireHit(original,point.x,point.y)
                active=hit?.id
                if(hit!=null) {
                    var delta=Offset.Zero
                    var next=original
                    try {
                        val completed=drag(down.id) {event->
                            delta+=event.positionChange();event.consume()
                            val moved=if(handle!=null) wireResizeEdge(hit,delta.x/(scale*viewport.width),delta.y/(scale*viewport.height),handle,original.format) else wireTransform(hit,delta.x/(scale*viewport.width),delta.y/(scale*viewport.height),false)
                            val result=if(!resizing) wireSmartAlign(original,moved,snapPixels/(scale*viewport.width),snapPixels/(scale*viewport.height),magnet) else wireResizeAlign(original,moved,handle!!,snapPixels/(scale*viewport.width),snapPixels/(scale*viewport.height),magnet)
                            alignment=result
                            val element=result?.element ?: moved
                            next=original.copy(elements=original.elements.map {if(it.id==hit.id) element else it});draft=next
                        }
                        if(completed) {if(next!=original) commit(next);select(hit.id)}
                    } finally {draft=null;active=null;alignment=null}
                } else {select(null);active=null}
            }
        }) {
        drawRect(palette.background)
        if(viewport.scale>0) {
            if(showDevice) wireDevice(viewport,document.format,palette)
            drawContext.canvas.nativeCanvas.apply {save();translate(viewport.origin.x,viewport.origin.y);scale(viewport.scale,viewport.scale);renderer.draw(this,displayed);restore()}
            wireComposition(viewport,composition,Color(document.background).compositeOver(palette.background))
            if(showMargins) wireMargins(viewport,document.format,guideColor)
            if(showGuides) alignment?.let {a->
                a.vertical?.let {x->drawLine(guideColor,viewport.screen(Offset(x,0f)),viewport.screen(Offset(x,1f)),strokeWidth=1.dp.toPx())}
                a.horizontal?.let {y->drawLine(guideColor,viewport.screen(Offset(0f,y)),viewport.screen(Offset(1f,y)),strokeWidth=1.dp.toPx())}
                wireMeasurements(viewport,a.distances,guideColor,palette.taskPanel)
            }
            if(showAnnotations) displayed.annotations.forEachIndexed {index,a->
                val p=viewport.screen(a.position(displayed))
                drawCircle(if(a.id==selectedAnnotation) palette.primary else Color(0xff315cbb),11.dp.toPx(),p)
                drawContext.canvas.nativeCanvas.drawText("${index+1}",p.x,p.y+4.dp.toPx(),android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.WHITE;textAlign=android.graphics.Paint.Align.CENTER;textSize=12.dp.toPx();typeface=android.graphics.Typeface.DEFAULT_BOLD})
            }
            if(!annotationMode) displayed.elements.firstOrNull {it.id==(active ?: selected)}?.let {e->
                drawRect(palette.primary,viewport.screen(Offset(e.x,e.y)),androidx.compose.ui.geometry.Size(e.w*viewport.width*viewport.scale,e.h*viewport.height*viewport.scale),style=Stroke(2.dp.toPx()))
                listOf(Offset(e.x+e.w,e.y+e.h),Offset(e.x,e.y+e.h/2),Offset(e.x+e.w/2,e.y),Offset(e.x+e.w,e.y+e.h/2),Offset(e.x+e.w/2,e.y+e.h)).forEach {p->drawCircle(palette.primary,5.dp.toPx(),viewport.screen(p))}
            }
        }
    }
    if(toolbarVisible) WireVisualToolbar(viewport,showGuides,showMargins,showDevice,magnetic,enabled,composition,onCompositionChange,onVisualToggle)
    }
}
