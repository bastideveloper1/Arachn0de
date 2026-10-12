package com.r0ybt.arachn0de.gantt

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import java.time.LocalDate
import kotlin.math.*

internal data class GanttComment(val number:Int,val caption:String,val text:String,val continuation:Boolean=false)
internal data class GanttPage(val lanes:List<GanttLane>,val ticks:List<GanttTick>,val vertical:Boolean=false,val index:Int=1,val count:Int=1,
    val columnStart:Int=0,val rowStart:Int=0,val rowCount:Int=1,val comments:List<GanttComment> = emptyList(),val headingHeight:Int=64) {
    val logicalWidth get()=if(comments.isNotEmpty()) 720 else maxOf(240,ceil(GanttCompactModel.LEFT+ticks.size*GanttCompactModel.CELL+4).toInt())
    val logicalHeight get()=if(comments.isNotEmpty()) headingHeight+comments.sumOf {GanttRenderer.commentHeight(it,logicalWidth-24)}+12 else headingHeight+GanttCompactModel.HEADER.toInt()+rowCount*GanttCompactModel.ROW.toInt()+12
    val width get()=logicalWidth*2
    val height get()=logicalHeight*2
}
/** Shared compact geometry. Every PNG is bounded to 4M pixels; no viewport capture. */
internal object GanttRenderer {
    private fun layout(value:String,size:Float,width:Int,color:Int=GanttPalette.TEXT)=StaticLayout.Builder.obtain(value,0,value.length,
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color},width.coerceAtLeast(1)).setIncludePad(false).setLineSpacing(2f,1f).build()
    internal fun commentHeight(note:GanttComment,width:Int)=28+layout(note.text,13f,width-28).height
    fun pages(doc:GanttDocument,vertical:Boolean=false):List<GanttPage> {
        val model=GanttCompactModel(doc)
        val columns=model.ticks.indices.chunked(40)
        val rows=(0 until model.rowCount).toList().chunked(36)
        require(columns.size.toLong()*rows.size<=512) {"El gráfico supera 512 imágenes. Reduce el período o filtra el Sprint."}
        val raw=columns.flatMap {c->rows.map {r->
            val width=maxOf(240,ceil(GanttCompactModel.LEFT+c.size*GanttCompactModel.CELL+4).toInt())
            GanttPage(r.mapNotNull {model.rows.getOrNull(it)?.let {item->GanttLane(item.id,doc.storyLabels[item.id].orEmpty(),listOf(item))}},c.map {model.ticks[it]},
                columnStart=c.first(),rowStart=r.first(),rowCount=r.size,headingHeight=layout(doc.heading,14f,width-12).height+42)
        }}.toMutableList()
        var group=mutableListOf<GanttComment>();var used=0
        fun finish() {if(group.isNotEmpty()) {raw+=GanttPage(emptyList(),emptyList(),comments=group.toList(),headingHeight=48);group=mutableListOf();used=0}}
        model.markers.forEach {marker->
            val full=layout(marker.annotation.text,13f,668)
            // Android line boundaries preserve Unicode and the entire stored comment.
            var line=0
            while(line<full.lineCount) {
                val last=minOf(line+30,full.lineCount)
                val body=marker.annotation.text.substring(full.getLineStart(line),full.getLineEnd(last-1))
                val caption=ganttDate(LocalDate.ofEpochDay(marker.annotation.epochDay))+marker.story?.let {" · ${doc.storyLabels[it]}"}.orEmpty()
                val note=GanttComment(marker.number,caption,body,line>0)
                val height=commentHeight(note,696)
                if(used+height>700) finish()
                group+=note;used+=height;line=last
            }
        };finish()
        require(raw.size<=512) {"La exportación supera 512 imágenes. Reduce el período."}
        return raw.mapIndexed {i,p->p.copy(index=i+1,count=raw.size)}.also {pages->require(pages.all {it.width.toLong()*it.height<=4_000_000}) {"El encabezado supera el tamaño seguro para un PNG."}}
    }
    private fun drawLayout(canvas:Canvas,value:String,size:Float,width:Int,x:Float,y:Float,color:Int=GanttPalette.TEXT) {
        canvas.save();canvas.translate(x,y);layout(value,size,width,color).draw(canvas);canvas.restore()
    }
    fun drawChart(canvas:Canvas,model:GanttCompactModel,columnStart:Int=0,columnCount:Int=model.ticks.size,rowStart:Int=0,rowCount:Int=model.rowCount,drawAnnotations:Boolean=true) {
        val left=GanttCompactModel.LEFT;val cell=GanttCompactModel.CELL;val row=GanttCompactModel.ROW;val top=GanttCompactModel.HEADER
        val width=left+columnCount*cell;val height=top+rowCount*row
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value:String,x:Float,y:Float,size:Float,color:Int=GanttPalette.TEXT) {paint.color=color;paint.style=Paint.Style.FILL;paint.textSize=size;canvas.drawText(value,x,y,paint)}
        paint.color=GanttPalette.BACKGROUND;canvas.drawRect(0f,0f,width,height,paint)
        text(model.month(columnStart),3f,15f,9f,GanttPalette.SECONDARY)
        repeat(columnCount) {i->
            val tick=model.ticks[columnStart+i];val x=left+(i+.5f)*cell
            paint.color=GanttPalette.DAY;canvas.drawCircle(x,top/2,8f,paint)
            paint.color=GanttPalette.TEXT;paint.textSize=9f;val day=tick.start.dayOfMonth.toString();canvas.drawText(day,x-paint.measureText(day)/2,top/2+3f,paint)
            paint.color=GanttPalette.BORDER;paint.strokeWidth=.35f;canvas.drawLine(left+i*cell,top,left+i*cell,height,paint)
        }
        repeat(rowCount) {i->
            val index=rowStart+i;val y=top+(i+.5f)*row
            if(index<model.rows.size) {
                text(model.label(index),4f,y+3.5f,10f)
                val item=model.rows[index]
                if(item.start!=null || item.end!=null) {
                    val start=item.start ?: item.end!!;val end=item.end ?: item.start!!
                    val a=model.coordinate(start);val b=model.coordinate(end.plusDays(1))
                    val x=left+maxOf(0f,a-columnStart)*cell;val right=left+minOf(columnCount.toFloat(),b-columnStart)*cell
                    if(right>x) {
                        paint.color=GanttPalette.PLAN;canvas.drawRect(x,y-4,right,y+4,paint)
                        val fill=left+(a+(b-a)*item.progress.coerceIn(0f,1f)-columnStart)*cell
                        if(fill>x) {paint.color=GanttPalette.PROGRESS;canvas.drawRect(x,y-4,minOf(right,fill),y+4,paint)}
                    }
                }
            }
        }
        if(drawAnnotations) model.markers.forEach {marker->
            val x=marker.x-columnStart*cell;val y=marker.y-rowStart*row
            if(x>=left && x<width && y>=top && y<height) {
                paint.color=GanttPalette.NOTE;canvas.drawCircle(x,y,8f,paint)
                paint.color=Color.WHITE;paint.textSize=9f;val number=marker.number.toString();canvas.drawText(number,x-paint.measureText(number)/2,y+3f,paint)
            }
        }
        paint.color=GanttPalette.BORDER;paint.style=Paint.Style.STROKE;paint.strokeWidth=.6f
        canvas.drawRect(.3f,.3f,width-.3f,height-.3f,paint);paint.style=Paint.Style.FILL
    }
    fun draw(canvas:Canvas,doc:GanttDocument,page:GanttPage,annotations:Boolean=true,dark:Boolean=true) {
        canvas.drawColor(GanttPalette.BACKGROUND)
        if(page.comments.isNotEmpty()) {
            drawLayout(canvas,"ARACHN0DE — ANOTACIONES",16f,page.logicalWidth-24,12f,12f)
            var y=page.headingHeight.toFloat()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            page.comments.forEach {note->
                paint.color=GanttPalette.NOTE;canvas.drawCircle(20f,y+9,8f,paint)
                paint.color=Color.WHITE;paint.textSize=9f;val n=note.number.toString();canvas.drawText(n,20f-paint.measureText(n)/2,y+12,paint)
                drawLayout(canvas,note.caption+(if(note.continuation) " · continuación" else ""),11f,650,36f,y+2,GanttPalette.SECONDARY)
                drawLayout(canvas,note.text,13f,668,36f,y+24)
                y+=commentHeight(note,page.logicalWidth-24)
            }
            paint.color=GanttPalette.BORDER;paint.style=Paint.Style.STROKE;paint.strokeWidth=.6f;canvas.drawRect(6f,6f,page.logicalWidth-6f,page.logicalHeight-6f,paint)
        } else {
            drawLayout(canvas,doc.heading,14f,page.logicalWidth-12,6f,5f)
            val titleHeight=layout(doc.heading,14f,page.logicalWidth-12).height
            drawLayout(canvas,"${doc.sprintProgress?.roundToInt()?.toString() ?: "—"}% · ${ganttDate(doc.first)} → ${ganttDate(doc.last)}",10f,page.logicalWidth-12,6f,titleHeight+9f,GanttPalette.SECONDARY)
            canvas.save();canvas.translate(2f,page.headingHeight.toFloat());drawChart(canvas,GanttCompactModel(doc),page.columnStart,page.ticks.size,page.rowStart,page.rowCount,annotations);canvas.restore()
            drawLayout(canvas,"ARACHN0DE · ${page.index}/${page.count}",8f,page.logicalWidth-12,6f,page.logicalHeight-10f,GanttPalette.SECONDARY)
        }
    }
    fun png(doc:GanttDocument,page:GanttPage,annotations:Boolean,output:OutputStream) {
        require(page.width.toLong()*page.height<=4_000_000)
        val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
        try {val canvas=Canvas(bitmap);canvas.scale(2f,2f);draw(canvas,doc,page,annotations);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) {"No se pudo escribir PNG."}} finally {bitmap.recycle()}
    }
}
