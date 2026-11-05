package com.r0ybt.arachn0de.document

import android.graphics.*
import android.text.TextPaint
import com.r0ybt.arachn0de.domain.model.DescriptionParser.Format
import kotlin.math.*

internal sealed interface ReportDraw {
    data class Text(val value:String,val x:Float,val baseline:Float,val size:Float,val formats:Set<Format> = emptySet(),val color:Int=Color.BLACK):ReportDraw
    data class Image(val id:String,val rect:RectF):ReportDraw
}
internal data class ReportPage(val items:List<ReportDraw>)
internal data class ReportPlan(val pages:List<ReportPage>,val sectionPages:List<Int>)
internal object ReportLayout {
    const val WIDTH=595;const val HEIGHT=842;const val MARGIN=44f;const val RIGHT=WIDTH-MARGIN;const val BOTTOM=HEIGHT-48f
    fun paint(size:Float,formats:Set<Format> = emptySet(),color:Int=Color.BLACK)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=if(Format.Title in formats)size*1.2f else size;this.color=color;typeface=Typeface.create(if(Format.Code in formats)"monospace" else "sans-serif",when {(Format.Bold in formats || Format.Title in formats)&&Format.Italic in formats->Typeface.BOLD_ITALIC;(Format.Bold in formats || Format.Title in formats)->Typeface.BOLD;Format.Italic in formats->Typeface.ITALIC;else->Typeface.NORMAL});isUnderlineText=Format.Underline in formats
    }
    /** Fixed-width page number column makes pagination converge without guessing TOC page counts. */
    fun measure(content:ReportContent,options:ReportOptions,check:()->Unit={}):ReportPlan {
        options.validate();var references=List(content.sections.size) {1}
        repeat(8) {
            val plan=Composer(content,options,references,check).build()
            if(plan.sectionPages==references)return plan
            references=plan.sectionPages
        }
        error("No se pudo estabilizar la paginación del índice.")
    }
    private class Composer(val content:ReportContent,val options:ReportOptions,val references:List<Int>,val check:()->Unit) {
        val pages=mutableListOf<MutableList<ReportDraw>>(mutableListOf())
        val sectionPages=mutableListOf<Int>();var y=MARGIN;var floating:RectF?=null;var floatLeft=false;var imageCount=0
        val page get()=pages.last()
        fun next() {check();require(pages.size<500) {"Máximo 500 páginas. Exporta una Capa más pequeña."};pages+=mutableListOf<ReportDraw>();y=MARGIN;floating=null}
        fun clear() {floating?.let {y=max(y,it.bottom+6)};floating=null}
        fun room(height:Float) {if(y+height>BOTTOM)next()}
        fun block(text:String,size:Float=11f,bold:Boolean=false,color:Int=Color.BLACK,indent:Float=0f,right:Float=RIGHT) {
            if(text.isBlank())return
            clear();val flags=if(bold)setOf(Format.Bold) else emptySet();val p=paint(size,flags,color)
            var remaining=text;val step=size*1.35f
            while(remaining.isNotEmpty()) {check();room(step);val width=(right-MARGIN-indent).coerceAtLeast(40f)
                var n=p.breakText(remaining,true,width,null).coerceAtLeast(1)
                if(n<remaining.length) {val space=remaining.lastIndexOf(' ',n-1);if(space>0)n=space+1}
                if(n<remaining.length&&remaining[n-1].isHighSurrogate()&&remaining[n].isLowSurrogate())n--
                if(n==0)n=min(2,remaining.length)
                val newline=remaining.indexOf('\n');if(newline in 0 until n)n=newline+1
                page+=ReportDraw.Text(remaining.take(n).trimEnd(),MARGIN+indent,y+size,size,flags,color)
                remaining=remaining.drop(n);y+=step
            }
        }
        fun image(part:ReportPart.Image) {
            clear();val asset=content.images.getValue(part.id)
            val full=options.images==ReportImageLayout.FULL
            val ratio=asset.width.toFloat()/asset.height
            var w=if(full)RIGHT-MARGIN else (RIGHT-MARGIN)*.30f
            var h=w/ratio
            val maxHeight=if(full)BOTTOM-MARGIN-12f else 300f
            if(h>maxHeight) {h=maxHeight;w=h*ratio}
            room(h+6)
            val left=options.images==ReportImageLayout.LEFT || (options.images==ReportImageLayout.AUTO&&imageCount%2==1)
            val x=if(full||left)MARGIN else RIGHT-w
            val rect=RectF(x,y,x+w,y+h);page+=ReportDraw.Image(part.id,rect);imageCount++
            if(full)y=rect.bottom+6 else {floating=rect;floatLeft=left}
        }
        fun flow(parts:List<ReportPart>) {
            var x=0f;var lineStarted=false;var lineHasText=false
            fun startLine() {
                room(15f)
                val f=floating
                if(f!=null&&y>=f.bottom)floating=null
                x=if(floating!=null&&floatLeft)floating!!.right+10 else MARGIN
                lineStarted=true;lineHasText=false
            }
            fun newline() {y+=15f;lineStarted=false;lineHasText=false}
            parts.forEach {part->check();when(part) {
                is ReportPart.Image->{if(lineHasText)newline();image(part);lineStarted=false}
                is ReportPart.Text->{val p=paint(11f,part.formats)
                    Regex("\\r\\n|\\n|[^\\S\\r\\n]+|[^\\s]+").findAll(part.text).forEach {match->
                        var token=match.value
                        if(token.contains('\n')) {if(!lineStarted)startLine();newline()}
                        else {
                            val whitespace=token.isBlank()
                            while(token.isNotEmpty()) {
                                if(!lineStarted)startLine()
                                if(whitespace&&!lineHasText && Format.Code !in part.formats)break
                                val right=if(floating!=null&&!floatLeft)floating!!.left-10 else RIGHT
                                val available=right-x
                                val width=p.measureText(token)
                                if(width<=available) {page+=ReportDraw.Text(token,x,y+11,11f,part.formats);x+=width;lineHasText=true;break}
                                if(lineHasText) {newline();if(whitespace)break;continue}
                                var n=p.breakText(token,true,available.coerceAtLeast(1f),null).coerceAtLeast(1)
                                if(n<token.length&&token[n-1].isHighSurrogate()&&token[n].isLowSurrogate())n--
                                if(n==0)n=min(2,token.length)
                                page+=ReportDraw.Text(token.take(n),x,y+11,11f,part.formats);token=token.drop(n);lineHasText=true;newline()
                            }
                        }
                    }
                }
            }}
            if(lineHasText)newline();clear();y+=4
        }
        fun build():ReportPlan {
            block(options.title,24f,true);if(options.subtitle.isNotBlank()) {y+=4;block(options.subtitle,13f)}
            y+=6;block("Fecha del informe: ${options.date}",9f,color=Color.DKGRAY);y+=12
            if(options.cover)next()
            if(options.index&&content.sections.size>1) {
                block("Índice",16f,true);y+=6
                content.sections.drop(1).forEachIndexed {index,s->
                    room(15f);val indent=(s.depth-1).coerceAtMost(8)*9f
                    val top=y;val pageBefore=pages.lastIndex
                    block("${s.number} ${s.title}",10f,indent=indent,right=RIGHT-38)
                    // Number is placed on the first row of the entry, including wrapped long titles.
                    pages[pageBefore]+=ReportDraw.Text(references[index+1].toString(),RIGHT-26,top+10,10f)
                }
                y+=12
            }
            content.sections.forEachIndexed {i,s->
                check();clear()
                if(i>0) {y+=8;room(48f);sectionPages+=pages.size;block("${s.number} ${s.title}",if(s.depth==1)16f else 13f,true);y+=4}
                else sectionPages+=pages.size
                s.metadata.forEach {block(it,9f,color=Color.DKGRAY)}
                if(s.metadata.isNotEmpty())y+=4
                flow(s.parts)
            }
            return ReportPlan(pages.map {ReportPage(it.toList())},sectionPages)
        }
    }
    fun draw(canvas:Canvas,page:ReportPage,content:ReportContent,number:Int,total:Int,check:()->Unit={}) {
        canvas.drawColor(Color.WHITE)
        page.items.forEach {check();when(it) {
            is ReportDraw.Text->{val p=paint(it.size,it.formats,it.color)
                if(Format.Highlight in it.formats || Format.Code in it.formats)canvas.drawRect(it.x,it.baseline+p.ascent(),it.x+p.measureText(it.value),it.baseline+p.descent(),Paint().apply {color=if(Format.Highlight in it.formats)Color.YELLOW else Color.rgb(232,236,240)})
                canvas.drawText(it.value,it.x,it.baseline,p)
            }
            is ReportDraw.Image->{val bytes=content.images.getValue(it.id).bytes;val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size));try {canvas.drawBitmap(bitmap,null,it.rect,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))} finally {bitmap.recycle()}}
        }}
        val p=paint(9f,color=Color.DKGRAY);val footer="Página $number de $total";canvas.drawText(footer,(WIDTH-p.measureText(footer))/2,HEIGHT-24f,p)
    }
}
