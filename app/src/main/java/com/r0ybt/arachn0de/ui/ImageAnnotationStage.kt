package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.annotations.*
import kotlin.math.max

internal enum class AnnotationMode(val label: String) { DRAW("Dibujar"), SELECT("Seleccionar"), PAN("Desplazar") }

@Composable internal fun ImageAnnotationStage(image: Bitmap, marks: List<ImageMark>, selected: String?,
    mode: AnnotationMode, tool: MarkTool, color: Int, width: Float, resize: Boolean, enabled: Boolean,
    onSelect: (String?) -> Unit, onCommit: (List<ImageMark>) -> Unit, modifier: Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf<List<ImageMark>?>(null) }
    var precisionPoint by remember {mutableStateOf<Offset?>(null)}
    DisposableEffect(image,mode,tool,color,width,resize,enabled) {
        onDispose {precisionPoint=null;preview=null}
    }
    val current by rememberUpdatedState(marks)
    val select by rememberUpdatedState(onSelect)
    val commit by rememberUpdatedState(onCommit)
    // Zoom and pan aren't pointerInput keys: a pinch must not cancel its own gesture.
    val gesture = Modifier.pointerInput(image, mode, tool, color, width, resize, enabled) {
        if (!enabled) return@pointerInput
        fun viewport() = annotationViewport(size.width.toFloat(), size.height.toFloat(), image.width, image.height, zoom, pan.x, pan.y)
        fun hit(p: MarkPoint): String? = current.asReversed().firstOrNull { m ->
            val padX = 24.dp.toPx()/viewport().width
            val padY = 24.dp.toPx()/viewport().height
            p.x in m.points.minOf { it.x }-padX..m.points.maxOf { it.x }+padX &&
                p.y in m.points.minOf { it.y }-padY..m.points.maxOf { it.y }+padY
        }?.id
        if (mode == AnnotationMode.PAN) detectTransformGestures { _, delta, scale, _ ->
            val z = (zoom*scale).coerceIn(1f,5f)
            val base = annotationViewport(size.width.toFloat(), size.height.toFloat(), image.width, image.height, z, 0f, 0f)
            val bx = max(0f, (base.width-size.width)/2)
            val by = max(0f, (base.height-size.height)/2)
            zoom=z
            pan=Offset((pan.x+delta.x).coerceIn(-bx,bx), (pan.y+delta.y).coerceIn(-by,by))
        }
        else if (mode == AnnotationMode.DRAW && tool == MarkTool.MARKER) detectTapGestures { at ->
            val view=viewport()
            if (view.contains(at.x,at.y)) {
                val mark=ImageMark(tool=tool,points=listOf(view.point(at.x,at.y)),color=color,width=width)
                commit(current+mark);select(mark.id)
            }
        }
        else {
            var start=MarkPoint(0f,0f)
            var baseline=emptyList<ImageMark>()
            var active:ImageMark?=null
            detectDragGestures(onDragStart={ at ->
                val view=viewport();baseline=current;start=view.point(at.x,at.y)
                active=if(!view.contains(at.x,at.y)) null
                    else if(mode==AnnotationMode.DRAW) ImageMark(tool=tool,points=listOf(start,start),color=color,width=width)
                    else baseline.firstOrNull {it.id==hit(start)}
                select(active?.id)
                precisionPoint=at.takeIf {mode==AnnotationMode.DRAW && active!=null}
                preview=if(mode==AnnotationMode.DRAW && active!=null) baseline+active!! else baseline
            }, onDragCancel={preview=null;active=null;precisionPoint=null}, onDragEnd={preview?.let(commit);preview=null;active=null;precisionPoint=null}) {change,_->
                val mark=active ?: return@detectDragGestures
                change.consume()
                val p=viewport().point(change.position.x,change.position.y)
                if(mode==AnnotationMode.DRAW) {
                    val view=viewport()
                    precisionPoint=Offset(view.left+p.x*view.width,view.top+p.y*view.height)
                }
                val next=if(mode==AnnotationMode.SELECT) {
                    if(resize && mark.tool!=MarkTool.MARKER) mark.copy(points=mark.points.dropLast(1)+p)
                    else mark.moved(p.x-start.x,p.y-start.y)
                } else if(tool==MarkTool.PEN) {
                    val old=preview?.lastOrNull() ?: mark
                    old.copy(points=(old.points+p).take(4096))
                } else mark.copy(points=listOf(start,p))
                preview=if(mode==AnnotationMode.DRAW) baseline+next else baseline.map {if(it.id==next.id) next else it}
            }
        }
    }
    // Selection taps don't start a drawing or change its geometry.
    val taps = if(mode==AnnotationMode.SELECT) Modifier.pointerInput(marks,zoom,pan) {
        detectTapGestures { at ->
            val view=annotationViewport(size.width.toFloat(),size.height.toFloat(),image.width,image.height,zoom,pan.x,pan.y)
            val p=view.point(at.x,at.y)
            select(if(!view.contains(at.x,at.y)) null else current.asReversed().firstOrNull {m->
                val px=24.dp.toPx()/view.width;val py=24.dp.toPx()/view.height
                p.x in m.points.minOf {it.x}-px..m.points.maxOf {it.x}+px && p.y in m.points.minOf {it.y}-py..m.points.maxOf {it.y}+py
            }?.id)
        }
    } else Modifier
    Canvas(modifier.clipToBounds().then(gesture).then(taps).testTag("image-annotation-stage").semantics {
        stateDescription=if(precisionPoint!=null && mode==AnnotationMode.DRAW && enabled)"Referencia de precisión visible" else "Referencia de precisión oculta"
    }) {
        val view=annotationViewport(size.width,size.height,image.width,image.height,zoom,pan.x,pan.y)
        val canvas=drawContext.canvas.nativeCanvas
        fun scene()=canvas.apply {
            save();translate(view.left,view.top);scale(view.width/image.width,view.height/image.height)
            clipRect(0,0,image.width,image.height);drawBitmap(image,0f,0f,null)
            drawImageMarks(this,image.width,image.height,preview ?: marks,selected.takeIf {mode==AnnotationMode.SELECT});restore()
        }
        scene()
        precisionPoint?.takeIf {mode==AnnotationMode.DRAW && enabled}?.let {point->
            val side=96.dp.toPx();val half=side/2;val margin=4.dp.toPx()
            if(size.width>=side+margin*2 && size.height>=side+margin*2) {
                val cx=point.x.coerceIn(half+margin,size.width-half-margin)
                val preferred=if(point.y>=side+32.dp.toPx())point.y-80.dp.toPx() else point.y+80.dp.toPx()
                val cy=preferred.coerceIn(half+margin,size.height-half-margin)
                val rect=android.graphics.RectF(cx-half,cy-half,cx+half,cy+half)
                val path=android.graphics.Path().apply {addRoundRect(rect,8.dp.toPx(),8.dp.toPx(),android.graphics.Path.Direction.CW)}
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                canvas.save();canvas.clipPath(path)
                canvas.drawColor(android.graphics.Color.DKGRAY)
                canvas.translate(cx-point.x*2,cy-point.y*2);canvas.scale(2f,2f)
                scene();canvas.restore()
                paint.style=android.graphics.Paint.Style.STROKE;paint.color=android.graphics.Color.WHITE;paint.strokeWidth=1.dp.toPx()
                canvas.drawRoundRect(rect,8.dp.toPx(),8.dp.toPx(),paint)
                for((tint,stroke) in listOf(android.graphics.Color.BLACK to 3.dp.toPx(),android.graphics.Color.WHITE to 1.dp.toPx())) {
                    paint.color=tint;paint.strokeWidth=stroke
                    canvas.drawLine(cx-6.dp.toPx(),cy,cx+6.dp.toPx(),cy,paint)
                    canvas.drawLine(cx,cy-6.dp.toPx(),cx,cy+6.dp.toPx(),paint)
                }
            }
        }
    }
}
