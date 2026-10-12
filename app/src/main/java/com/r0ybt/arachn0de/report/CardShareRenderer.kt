package com.r0ybt.arachn0de.report

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min

internal data class CardSharePicture(val label:String,val compact:Boolean=false,val load:()->Bitmap?)
internal data class CardShareData(val title:String,val information:String,val pictures:List<CardSharePicture> = emptyList())
internal data class CardShareColors(val background:Int,val text:Int,val accent:Int)

/** Derived PNG only: complete wrapping and proportionate images; no source writes. */
internal object CardShareRenderer {
    const val WIDTH=1080
    const val MAX_HEIGHT=8192
    const val COMPACT_PICTURE_SIZE=160
    fun render(data:CardShareData,colors:CardShareColors,checkCancelled:()->Unit={}):Bitmap {
        fun text(value:String,size:Float,color:Int,bold:Boolean=false,width:Int=WIDTH-64)=StaticLayout.Builder.obtain(value,0,value.length,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT},width)
            .setIncludePad(false).setLineSpacing(8f,1f).build()
        val title=text(data.title,48f,colors.accent,true)
        val body=text(data.information,32f,colors.text)
        val pictures=data.pictures.map {picture->
            checkCancelled()
            val bitmap=picture.load() ?: error("No se pudo leer una fotografía asociada.")
            try {
                val scale=if(picture.compact) min(COMPACT_PICTURE_SIZE.toFloat()/bitmap.width,COMPACT_PICTURE_SIZE.toFloat()/bitmap.height) else min((WIDTH-64f)/bitmap.width,720f/bitmap.height)
                Triple(picture,text(picture.label,28f,colors.text,width=if(picture.compact) WIDTH-64-COMPACT_PICTURE_SIZE-24 else WIDTH-64),Size(bitmap.width*scale,bitmap.height*scale))
            } finally {bitmap.recycle()}
        }
        val height=(64+title.height+24+body.height+pictures.sumOf {if(it.first.compact) 16+maxOf(COMPACT_PICTURE_SIZE,it.second.height) else 24+it.second.height+12+kotlin.math.ceil(it.third.height).toInt()}).coerceAtLeast(200)
        require(height<=MAX_HEIGHT) {"La tarjeta supera el tamaño seguro para una sola imagen. No se generó un PNG incompleto."}
        val result=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(result);canvas.drawColor(colors.background)
            var y=32f
            fun draw(layout:StaticLayout) {canvas.save();canvas.translate(32f,y);layout.draw(canvas);canvas.restore();y+=layout.height}
            draw(title);y+=24;draw(body)
            pictures.forEach {(picture,label,size)->
                checkCancelled()
                y+=if(picture.compact) 16 else 24
                if(!picture.compact) {draw(label);y+=12}
                val rowHeight=if(picture.compact) maxOf(COMPACT_PICTURE_SIZE,label.height).toFloat() else size.height
                val bitmap=picture.load() ?: error("La fotografía ya no está disponible.")
                try {
                    val x=if(picture.compact) 32f+(COMPACT_PICTURE_SIZE-size.width)/2 else (WIDTH-size.width)/2
                    val top=if(picture.compact) y+(rowHeight-size.height)/2 else y
                    canvas.drawBitmap(bitmap,null,RectF(x,top,x+size.width,top+size.height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } finally {bitmap.recycle()}
                if(picture.compact) {
                    canvas.save();canvas.translate(32f+COMPACT_PICTURE_SIZE+24,y+(rowHeight-label.height)/2)
                    label.draw(canvas);canvas.restore()
                }
                y+=rowHeight
            }
            checkCancelled();return result
        } catch(failure:Throwable) {result.recycle();throw failure}
    }
    private data class Size(val width:Float,val height:Float)
}
