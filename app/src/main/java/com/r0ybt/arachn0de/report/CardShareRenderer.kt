package com.r0ybt.arachn0de.report

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min

internal data class CardSharePicture(val label:String,val compact:Boolean=false,val load:()->Bitmap?)
internal data class CardShareField(val label:String,val value:String)
internal data class CardShareData(val title:String,val information:String,val pictures:List<CardSharePicture> = emptyList(),
    val project:String?=null,val status:String?=null,val fields:List<CardShareField> = emptyList())
internal data class CardShareColors(val background:Int,val text:Int,val accent:Int)

/** One complete reference sheet, shared by individual and descendant exports. */
internal object CardShareRenderer {
    const val WIDTH=1080
    const val MAX_HEIGHT=8192
    const val COMPACT_PICTURE_SIZE=160
    private const val LEFT=52f
    private const val INNER=976
    fun render(data:CardShareData,colors:CardShareColors,checkCancelled:()->Unit={}):Bitmap {
        val muted=Color.argb(255,(Color.red(colors.text)*.75f).toInt(),(Color.green(colors.text)*.75f).toInt(),(Color.blue(colors.text)*.75f).toInt())
        val outline=Color.argb(110,100,120,130)
        fun text(value:String,size:Float=30f,color:Int=colors.text,bold:Boolean=false,width:Int=INNER)=StaticLayout.Builder.obtain(value,0,value.length,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT},width)
            .setIncludePad(false).setLineSpacing(8f,1f).build()
        val title=text(data.title,50f,bold=true)
        val body=text(data.information,32f,width=INNER-64)
        val rows=data.fields.chunked(4).map {fields->
            val width=(INNER-24*(fields.size-1))/fields.size
            fields.map {field->text(field.label,25f,muted,width=width-44) to text(field.value,29f,width=width-44)}
        }
        val project=data.project?.let {text("Proyecto: $it",30f,width=if(data.status!=null)550 else INNER-48)}
        val status=data.status?.let {text("Estado: $it",29f,width=300)}
        val headerHeight=if(project!=null || status!=null)maxOf(project?.height ?: 0,status?.height ?: 0)+40 else 0
        val pictures=data.pictures.map {picture->
            checkCancelled()
            val bitmap=picture.load() ?: error("No se pudo leer una imagen asociada.")
            try {
                // Reference images use the full content width, with no portrait-height cap or crop.
                val scale=if(picture.compact)min(COMPACT_PICTURE_SIZE.toFloat()/bitmap.width,COMPACT_PICTURE_SIZE.toFloat()/bitmap.height) else INNER.toFloat()/bitmap.width
                Picture(picture,text(picture.label,26f,muted,width=if(picture.compact)INNER-184 else INNER),bitmap.width*scale,bitmap.height*scale)
            } finally {bitmap.recycle()}
        }
        val rowHeights=rows.map {row->row.maxOf {it.first.height+12+it.second.height}+32}
        val normal=pictures.filterNot {it.source.compact};val compact=pictures.filter {it.source.compact}
        var height=48L+title.height+32+headerHeight+(if(headerHeight>0)28 else 0)+rowHeights.sum()+
            (if(data.information.isNotBlank())88+body.height else 0)+(if(normal.isNotEmpty())96 else 0)+
            normal.sumOf {kotlin.math.ceil(it.height.toDouble()).toLong()+20+it.label.height+32}+
            compact.sumOf {maxOf(COMPACT_PICTURE_SIZE,it.label.height)+20}+52
        if(height>MAX_HEIGHT && normal.isNotEmpty()) {
            val pictureHeight=normal.sumOf {kotlin.math.ceil(it.height.toDouble()).toLong()}
            val fixedHeight=height-pictureHeight
            val available=MAX_HEIGHT-fixedHeight
            require(available>=normal.size) {"El texto y las etiquetas de la ficha superan el alto máximo de 8.192 píxeles."}
            val scale=((available-normal.size).toDouble()/normal.sumOf {it.height.toDouble()}).coerceAtMost(1.0).toFloat()
            normal.forEach {picture->picture.width*=scale;picture.height=maxOf(1f,picture.height*scale)}
            height=fixedHeight+normal.sumOf {kotlin.math.ceil(it.height.toDouble()).toLong()}
        }
        require(height<=MAX_HEIGHT) {"El texto y las etiquetas de la ficha superan el alto máximo de 8.192 píxeles."}
        val result=Bitmap.createBitmap(WIDTH,height.toInt().coerceAtLeast(200),Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(result);canvas.drawColor(colors.background)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color=outline;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
            canvas.drawRoundRect(RectF(18f,18f,WIDTH-18f,height-18f),20f,20f,paint)
            var y=48f
            fun draw(layout:StaticLayout,x:Float=LEFT) {canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore()}
            fun line() {paint.color=outline;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;canvas.drawLine(LEFT,y,WIDTH-LEFT,y,paint)}
            fun icon(label:String,x:Float,top:Float) {
                paint.color=muted;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f
                when(label) {
                    "Asignada"->{canvas.drawCircle(x+14,top+9,7f,paint);canvas.drawRoundRect(RectF(x+2,top+21,x+26,top+34),5f,5f,paint)}
                    "Vencimiento"->{canvas.drawCircle(x+15,top+17,14f,paint);canvas.drawLine(x+15,top+17,x+15,top+6,paint);canvas.drawLine(x+15,top+17,x+23,top+22,paint)}
                    else->{canvas.drawRoundRect(RectF(x+2,top+2,x+28,top+34),4f,4f,paint);canvas.drawLine(x+7,top+12,x+23,top+12,paint);canvas.drawLine(x+7,top+20,x+23,top+20,paint)}
                }
            }
            draw(title);y+=title.height+32
            if(headerHeight>0) {
                paint.style=Paint.Style.STROKE;paint.color=outline;paint.strokeWidth=2f
                project?.let {canvas.drawRoundRect(RectF(LEFT,y,if(status!=null)660f else WIDTH-LEFT,y+headerHeight),14f,14f,paint);val saved=y;y+=(headerHeight-it.height)/2;draw(it,LEFT+24);y=saved}
                status?.let {paint.style=Paint.Style.FILL;paint.color=Color.argb(90,130,145,160);canvas.drawRoundRect(RectF(690f,y,WIDTH-LEFT,y+headerHeight),headerHeight/2f,headerHeight/2f,paint);val saved=y;y+=(headerHeight-it.height)/2;draw(it,714f);y=saved}
                y+=headerHeight+28
            }
            rows.forEachIndexed {index,row->
                val width=(INNER-24*(row.size-1))/row.size
                row.forEachIndexed {column,(label,value)->
                    val x=LEFT+column*(width+24);icon(data.fields[index*4+column].label,x,y)
                    draw(label,x+44);val saved=y;y+=label.height+12;draw(value,x+44);y=saved
                    if(column>0) {paint.color=outline;paint.style=Paint.Style.STROKE;canvas.drawLine(x-12,y,x-12,y+rowHeights[index]-16,paint)}
                };y+=rowHeights[index]
            }
            if(data.information.isNotBlank()) {
                line();y+=28;icon("Descripción",LEFT,y);draw(text("Descripción",28f,muted),LEFT+64);y+=60;draw(body,LEFT+64);y+=body.height
            }
            if(normal.isNotEmpty()) {y+=24;line();y+=28;icon("Imágenes",LEFT,y);draw(text("Imágenes  ·  ${normal.size}",30f,bold=true),LEFT+64);y+=44}
            (normal+compact).forEach {picture->
                checkCancelled()
                val bitmap=picture.source.load() ?: error("La imagen ya no está disponible.")
                try {
                    if(picture.source.compact) {
                        y+=20;val rowHeight=maxOf(COMPACT_PICTURE_SIZE,picture.label.height).toFloat()
                        val x=LEFT+(COMPACT_PICTURE_SIZE-picture.width)/2;val top=y+(rowHeight-picture.height)/2
                        canvas.drawBitmap(bitmap,null,RectF(x,top,x+picture.width,top+picture.height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        val saved=y;y+=(rowHeight-picture.label.height)/2;draw(picture.label,LEFT+184);y=saved+rowHeight
                    } else {
                        val x=LEFT+(INNER-picture.width)/2
                        canvas.drawBitmap(bitmap,null,RectF(x,y,x+picture.width,y+picture.height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        paint.color=outline;paint.style=Paint.Style.STROKE;canvas.drawRect(x,y,x+picture.width,y+picture.height,paint)
                        y+=picture.height+20;draw(picture.label);y+=picture.label.height+32
                    }
                } finally {bitmap.recycle()}
            }
            checkCancelled();return result
        } catch(failure:Throwable) {result.recycle();throw failure}
    }
    private data class Picture(val source:CardSharePicture,val label:StaticLayout,var width:Float,var height:Float)
}
