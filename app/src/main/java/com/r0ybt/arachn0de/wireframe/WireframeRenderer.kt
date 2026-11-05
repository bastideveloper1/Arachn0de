package com.r0ybt.arachn0de.wireframe

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import androidx.core.content.ContextCompat
import com.r0ybt.arachn0de.R
import java.io.OutputStream

internal val WireIcons=mapOf("apple" to R.drawable.lucide_apple,"arrow_left" to R.drawable.lucide_arrow_left,"arrow_left_right" to R.drawable.lucide_arrow_left_right,"bell" to R.drawable.lucide_bell,"bookmark" to R.drawable.lucide_bookmark,"calendar_clock" to R.drawable.lucide_calendar_clock,"calendar_days" to R.drawable.lucide_calendar_days,"check" to R.drawable.lucide_check,"chevron_down" to R.drawable.lucide_chevron_down,"chevron_right" to R.drawable.lucide_chevron_right,"clock" to R.drawable.lucide_clock,"code_xml" to R.drawable.lucide_code_xml,"copy" to R.drawable.lucide_copy,"database" to R.drawable.lucide_database,"ellipsis_vertical" to R.drawable.lucide_ellipsis_vertical,"flag" to R.drawable.lucide_flag,"folder_input" to R.drawable.lucide_folder_input,"hourglass" to R.drawable.lucide_hourglass,"image" to R.drawable.lucide_image,"info" to R.drawable.lucide_info,"layers" to R.drawable.lucide_layers,"list_checks" to R.drawable.lucide_list_checks,"list_filter" to R.drawable.lucide_list_filter,"lock" to R.drawable.lucide_lock,"menu" to R.drawable.lucide_menu,"navigation" to R.drawable.lucide_navigation,"network" to R.drawable.lucide_network,"palette" to R.drawable.lucide_palette,"paperclip" to R.drawable.lucide_paperclip,"pencil" to R.drawable.lucide_pencil,"plus" to R.drawable.lucide_plus,"receipt" to R.drawable.lucide_receipt,"repeat" to R.drawable.lucide_repeat,"search" to R.drawable.lucide_search,"settings" to R.drawable.lucide_settings,"share_2" to R.drawable.lucide_share_2,"shield" to R.drawable.lucide_shield,"star" to R.drawable.lucide_star,"star_selected" to R.drawable.lucide_star_selected,"sticky_note" to R.drawable.lucide_sticky_note,"sticky_note_marked" to R.drawable.lucide_sticky_note_marked,"tag" to R.drawable.lucide_tag,"trash" to R.drawable.lucide_trash,"user" to R.drawable.lucide_user,"users" to R.drawable.lucide_users,"x" to R.drawable.lucide_x)

/** Native rendering is shared by the canvas, thumbnails and full-resolution PNG. Editor aids live outside it. */
internal class WireframeRenderer(private val context:Context,assets:Map<String,String>):java.io.Closeable {
    private val images=assets.mapValues {(_,raw)->val bytes=Base64.decode(raw,Base64.NO_WRAP);requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))}.toMutableMap()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas:Canvas,document:WireDocument) {
        val width=document.format.width.toFloat();val height=document.format.height.toFloat()
        canvas.save();canvas.clipRect(0f,0f,width,height);canvas.drawColor(document.background.toInt())
        document.elements.forEach {e->
            val left=e.x*width;val top=e.y*height;val w=e.w*width;val h=e.h*height
            canvas.saveLayerAlpha(left,top,left+w,top+h,(e.opacity*255).toInt())
            canvas.translate(left,top);canvas.clipRect(0f,0f,w,h)
            if(e.template!=null && !e.template.startsWith("shape_") && e.kind!=WireKind.IMAGE) {drawTemplate(canvas,e,w,h,width,height);canvas.restore();return@forEach}
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=e.fill.toInt();paint.strokeWidth=e.border
            val round=when(e.kind) {WireKind.ROUND_RECT,WireKind.ROUND_BUTTON,WireKind.CARD,WireKind.DIALOG->e.radius;else->0f}
            fun shape() {when(e.kind) {
                WireKind.CIRCLE,WireKind.CIRCLE_BUTTON->canvas.drawCircle(w/2,h/2,(minOf(w,h)/2-e.border/2).coerceAtLeast(0f),paint)
                WireKind.LINE,WireKind.SEPARATOR->canvas.drawLine(0f,h/2,w,h/2,paint)
                else->canvas.drawRoundRect(e.border/2,e.border/2,w-e.border/2,h-e.border/2,round,round,paint)
            }}
            if(!e.kind.textOnly && e.kind !in setOf(WireKind.ICON,WireKind.IMAGE)) {shape();if(e.border>0) {paint.style=Paint.Style.STROKE;paint.color=e.stroke.toInt();shape()};paint.style=Paint.Style.FILL}
            if(e.kind==WireKind.IMAGE) images[e.asset]?.let {bitmap->
                val factor=if(e.imageFit=="CROP") maxOf(w/bitmap.width,h/bitmap.height) else minOf(w/bitmap.width,h/bitmap.height)
                val iw=bitmap.width*factor;val ih=bitmap.height*factor
                paint.color=Color.WHITE;paint.isFilterBitmap=true;canvas.drawBitmap(bitmap,null,RectF((w-iw)/2,(h-ih)/2,(w+iw)/2,(h+ih)/2),paint)
            }
            if(e.kind==WireKind.PLACEHOLDER) {paint.color=e.stroke.toInt();paint.strokeWidth=maxOf(1f,e.border);canvas.drawLine(0f,0f,w,h,paint);canvas.drawLine(0f,h,w,0f,paint)}
            val decoration=e.kind in setOf(WireKind.CHECKBOX,WireKind.SWITCH,WireKind.SELECT)
            if(decoration) {
                paint.color=e.ink.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
                val side=minOf(h*.5f,48f)
                when(e.kind) {
                    WireKind.CHECKBOX->{canvas.drawRect(12f,(h-side)/2,12+side,(h+side)/2,paint);if(e.checked) {canvas.drawLine(15f,h/2,12+side/2,h/2+side/3,paint);canvas.drawLine(12+side/2,h/2+side/3,8+side,h/2-side/3,paint)}}
                    WireKind.SWITCH->{canvas.drawRoundRect(12f,(h-side)/2,12+side*1.6f,(h+side)/2,side/2,side/2,paint);paint.style=Paint.Style.FILL;canvas.drawCircle(12+if(e.checked) side*1.2f else side*.4f,h/2,side*.32f,paint)}
                    else->{canvas.drawLine(w-36,h/2-6,w-24,h/2+6,paint);canvas.drawLine(w-24,h/2+6,w-12,h/2-6,paint)}
                };paint.style=Paint.Style.FILL
            }
            if(e.kind.iconCapable) {
                val contentW=e.contentW?.times(width) ?: w;val contentH=e.contentH?.times(height) ?: h
                val side=if(e.kind==WireKind.ICON) minOf(contentW,contentH) else minOf(contentH*.6f,64f)
                val x=if(e.kind in setOf(WireKind.ICON,WireKind.ICON_BUTTON)) (w-side)/2 else 12f
                ContextCompat.getDrawable(context,WireIcons.getValue(e.icons[0] ?: e.icon))?.mutate()?.let {icon->icon.setTint(e.ink.toInt());icon.setBounds(x.toInt(),((h-side)/2).toInt(),(x+side).toInt(),((h+side)/2).toInt());icon.draw(canvas)}
            }
            if(e.kind.textCapable) {
                val pad=if(e.kind.textOnly) 0f else 12f
                val start=if(e.kind in setOf(WireKind.CHECKBOX,WireKind.SWITCH,WireKind.TEXT_ICON_BUTTON,WireKind.SEARCH)) minOf(h*.7f,80f) else pad
                val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=e.ink.toInt();textSize=e.font;isUnderlineText=e.underline;typeface=Typeface.create(e.family,(if(e.bold) Typeface.BOLD else 0) or (if(e.italic) Typeface.ITALIC else 0))}
                val layout=StaticLayout.Builder.obtain(e.text,0,e.text.length,textPaint,(w-start-pad).toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(when(e.align) {"LEFT"->Layout.Alignment.ALIGN_NORMAL;"RIGHT"->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_CENTER}).build()
                canvas.save();canvas.translate(start,if(e.kind.textOnly || e.kind==WireKind.MULTILINE) pad else ((h-layout.height)/2).coerceAtLeast(pad));layout.draw(canvas);canvas.restore()
            }
            canvas.restore()
        };canvas.restore()
    }
    private fun drawTemplate(c:Canvas,e:WireElement,w:Float,h:Float,documentW:Float,documentH:Float) {
        val id=e.template!!
        val unit=e.contentUnit ?: minOf(w/400f,h/112f).coerceIn(.35f,1.5f)
        val layoutW=e.contentW?.times(documentW) ?: w
        var iconSlot=0
        val pad=16f*unit
        fun rect(x:Float,y:Float,r:Float,b:Float,color:Long=e.fill,radius:Float=0f,stroke:Boolean=false) {
            paint.shader=null;paint.style=if(stroke) Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=2.5f*unit;paint.color=color.toInt()
            c.drawRoundRect(x,y,r,b,radius,radius,paint);paint.style=Paint.Style.FILL
        }
        fun line(x:Float,y:Float,r:Float,b:Float,color:Long=e.ink) {paint.color=color.toInt();paint.strokeWidth=3f*unit;c.drawLine(x,y,r,b,paint)}
        fun circle(x:Float,y:Float,r:Float,color:Long=e.ink) {paint.style=Paint.Style.FILL;paint.color=color.toInt();c.drawCircle(x,y,r.coerceAtLeast(0f),paint)}
        fun icon(default:String,x:Float,y:Float,side:Float=32f*unit) {
            val key=e.icons[iconSlot++] ?: default
            if(key in setOf("eye","calendar","clock","warning","info")) {
                c.save();c.translate(x,y);c.scale(side/32f,side/32f)
                paint.color=e.ink.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
                when(key) {
                    "eye"->{c.drawOval(1f,7f,31f,25f,paint);c.drawCircle(16f,16f,5f,paint)}
                    "calendar"->{c.drawRoundRect(3f,5f,29f,29f,3f,3f,paint);c.drawLine(3f,12f,29f,12f,paint);c.drawLine(10f,1f,10f,9f,paint);c.drawLine(22f,1f,22f,9f,paint);c.drawLine(9f,19f,23f,19f,paint);c.drawLine(9f,24f,19f,24f,paint)}
                    "clock"->{c.drawCircle(16f,16f,14f,paint);c.drawLine(16f,6f,16f,16f,paint);c.drawLine(16f,16f,23f,20f,paint)}
                    "warning"->{val path=Path().apply {moveTo(16f,2f);lineTo(31f,29f);lineTo(1f,29f);close()};c.drawPath(path,paint);c.drawLine(16f,11f,16f,20f,paint);c.drawPoint(16f,25f,paint)}
                    else->{c.drawCircle(16f,16f,14f,paint);c.drawLine(16f,14f,16f,25f,paint);c.drawPoint(16f,8f,paint)}
                }
                paint.style=Paint.Style.FILL;c.restore();return
            }
            ContextCompat.getDrawable(context,WireIcons.getValue(key))?.mutate()?.let {d->d.setTint(e.ink.toInt());d.setBounds(x.toInt(),y.toInt(),(x+side).toInt(),(y+side).toInt());d.draw(c)}
        }
        fun text(value:String=e.text,x:Float=pad,y:Float=pad,width:Float=w-2*pad,size:Float=e.font*unit,center:Boolean=false) {
            val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=e.ink.toInt();textSize=size;typeface=Typeface.create(e.family,if(e.bold) Typeface.BOLD else Typeface.NORMAL)}
            val layout=StaticLayout.Builder.obtain(value,0,value.length,tp,width.toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(if(center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).build()
            c.save();c.translate(x,y);layout.draw(c);c.restore()
        }
        val category=WireTemplates.items.first {it.id==id}.category
        when(category) {
            "Botones"->{
                if(id in setOf("circle_button","fab")) {if(id=="fab") circle(w/2,h/2,minOf(w,h)/2,0xff999999);circle(w/2,h/2-(if(id=="fab") 3*unit else 0f),minOf(w,h)/2-(if(id=="fab") 5*unit else 0f),e.fill);icon("plus",w/2-20*unit,h/2-20*unit,40*unit)}
                else {val radius=when(id) {"pill"->h/2;"rounded","text_icon"->20*unit;else->0f};rect(2f,2f,w-2,h-2,e.fill,radius);if(id=="outline") rect(2f,2f,w-2,h-2,e.ink,radius,true)
                    if(id=="text_icon") icon("plus",pad,h/2-16*unit)
                    text(x=if(id=="text_icon") 52*unit else pad,y=(h-e.font*unit)/2,width=w-if(id=="text_icon") 68*unit else 2*pad,center=true)}
            }
            "Entradas"->{
                val top=if(id=="labeled") 40*unit else 0f
                if(id=="filled") rect(0f,top,w,h,e.fill,12*unit)
                if(id in setOf("bordered","password","select","multiline","search","leading")) rect(2f,top+2,w-2,h-2,e.ink,12*unit,true)
                else line(0f,h-3,w,h-3)
                if(id=="labeled") text("Etiqueta",y=0f,size=24*unit)
                val leading=id in setOf("search","leading")
                if(leading) icon(if(id=="search") "search" else "user",pad,(h+top)/2-16*unit)
                text(x=if(leading) 60*unit else pad,y=if(id=="multiline") pad else (h+top-e.font*unit)/2,width=w-(if(leading) 120 else 70)*unit)
                if(id=="password") icon("eye",w-48*unit,h/2-16*unit)
                if(id=="select") {line(w-42*unit,h/2-5*unit,w-32*unit,h/2+5*unit);line(w-32*unit,h/2+5*unit,w-22*unit,h/2-5*unit)}
            }
            "Navbar"->{rect(0f,0f,w,h);when(id) {
                "side","dropdown"->{if(id=="side") {icon("user",pad,pad,56*unit);text("Mi espacio",y=90*unit)};val start=if(id=="side") 170*unit else 10f;e.text.split('\n').take(6).forEachIndexed {i,t->icon(if(i==2) "settings" else "menu",pad,start+i*70*unit);text(t,x=60*unit,y=start+i*70*unit)}}
                "bottom","bottom_labels","tabs"->{e.text.split('\n').take(3).forEachIndexed {i,t->val x=layoutW*i/3; if(id=="tabs") text(t,x=x,y=h*.3f,width=w/3,center=true) else {icon(listOf("menu","search","user")[i],x+layoutW/6-16*unit,h*.2f);if(id=="bottom_labels") text(t,x=x,y=h*.6f,width=layoutW/3,size=24*unit,center=true)}};line(0f,h-4,w/3,h-4)}
                "toolbar"->{listOf("plus","image","trash","settings").forEachIndexed {i,k->icon(k,layoutW*(i+.5f)/4-16*unit,h/2-16*unit)}}
                "desktop"->{text(e.text.substringBefore('\n'),y=h/3,width=w*.4f);text(e.text.substringAfter('\n',""),x=w*.4f,y=h/3,width=w*.6f)}
                else->{if(id!="top") icon(if(id=="back") "arrow_left" else "menu",pad,h/2-16*unit);text(x=if(id=="top") pad else 64*unit,y=h/3)}}}
            "Selección"->{when {
                id.startsWith("check")-> {rect(pad,h/2-20*unit,pad+40*unit,h/2+20*unit,e.ink,4*unit,true);if(id.endsWith("on")) icon("check",pad,h/2-20*unit,40*unit);text(x=80*unit,y=h/3)}
                id.startsWith("radio")-> {circle(32*unit,h/2,22*unit);circle(32*unit,h/2,17*unit,e.fill);if(id.endsWith("on")) circle(32*unit,h/2,11*unit);text(x=80*unit,y=h/3)}
                id.startsWith("switch")-> {rect(pad,h/2-20*unit,96*unit,h/2+20*unit,if(id.endsWith("on")) e.ink else e.fill,24*unit);circle((if(id.endsWith("on")) 74 else 38)*unit,h/2,16*unit,if(id.endsWith("on")) e.fill else e.ink);text(x=120*unit,y=h/3)}
                id=="slider"->{rect(pad,h/2-4*unit,w-pad,h/2+4*unit);rect(pad,h/2-4*unit,w*.55f,h/2+4*unit,e.ink);circle(w*.55f,h/2,14*unit)}
                id=="segmented"->{rect(2f,2f,w-2,h-2,e.ink,16*unit,true);rect(4f,4f,w/3,h-4,e.fill,16*unit);text(y=h/3,center=true)}
                id=="counter"->{rect(2f,2f,w-2,h-2,e.ink,12*unit,true);text("−",width=w/3,y=h/3,center=true);text(x=w/3,width=w/3,y=h/3,center=true);icon("plus",w*.83f-16*unit,h/2-16*unit)}
                else->{rect(2f,2f,w-2,h-2,e.ink,12*unit,true);text(y=h/3);icon(if(id=="date") "calendar" else "clock",w-50*unit,h/2-16*unit)}
            }}
            "Ventanas y estados"->{when(id) {
                "progress"->{rect(0f,0f,w,h,e.fill,h/2);rect(0f,0f,w*.6f,h,e.ink,h/2)}
                "spinner"->{paint.color=e.ink.toInt();paint.strokeWidth=8*unit;paint.style=Paint.Style.STROKE;c.drawArc(8f,8f,w-8,h-8,-90f,270f,false,paint);paint.style=Paint.Style.FILL}
                "context"->{rect(0f,0f,w,h,e.fill,12*unit);e.text.split('\n').take(5).forEachIndexed {i,t->text(t,y=pad+i*h/3)}}
                "empty"->{icon("image",w/2-36*unit,pad,72*unit);text(y=h*.6f,center=true)}
                "snackbar","success","warning"->{rect(0f,0f,w,h,e.fill,12*unit);icon(if(id=="success") "check" else if(id=="warning") "warning" else "menu",pad,h/2-16*unit);text(x=64*unit,y=h/3)}
                else->{rect(0f,0f,w,h,e.fill,24*unit);if(id=="sheet") rect(w*.4f,12*unit,w*.6f,18*unit,e.ink,3*unit);if(id=="error" || id=="info") icon(if(id=="error") "warning" else "info",pad,pad);text(x=if(id in setOf("error","info")) 60*unit else pad,y=50*unit);line(pad,h-85*unit,w-pad,h-85*unit);text("Cancelar       Aceptar",y=h-60*unit,center=true)}
            }}
            "Contenedores"->{if(id=="separator") line(0f,h/2,w,h/2) else {rect(0f,0f,w,h,e.fill,if(id=="panel" || id=="section") 0f else minOf(h/2,20*unit));when(id) {
                "card_image"->{rect(pad,pad,w-pad,h*.55f,e.ink,8*unit,true);icon("image",w/2-24*unit,h*.25f,48*unit);text(y=h*.62f)}
                "card"->{line(pad,h*.7f,w*.7f,h*.7f);line(pad,h*.85f,w*.45f,h*.85f)}
                "list_icon"->{icon("user",pad,h/2-20*unit,40*unit);text(x=76*unit,y=h/3)}
                "section"->{text(y=pad);line(pad,70*unit,w-pad,70*unit)}
                else->text(y=if(id in setOf("chip","tag","badge","list")) h/3 else pad,center=id in setOf("chip","tag","badge"))
            }}}
            else->when(id) {
                "icon"->icon(e.icon,0f,0f,minOf(w,h))
                "avatar"->{circle(w/2,h/2,minOf(w,h)/2,e.fill);icon("user",w*.2f,h*.2f,minOf(w,h)*.6f)}
                "placeholder"->{rect(0f,0f,w,h);line(0f,0f,w,h);line(w,0f,0f,h);icon("image",w/2-24*unit,h/2-24*unit,48*unit)}
                "divider"->line(0f,h/2,w,h/2)
                "pages"->repeat(3) {circle(w*(it+1)/4,h/2,8*unit,if(it==0) e.ink else e.fill)}
                else->text(x=0f,y=0f,width=w)
            }
        }
    }
    fun bitmap(document:WireDocument,width:Int=document.format.width,signature:Boolean=false,markers:Boolean=false):Bitmap {
        val scale=width.toFloat()/document.format.width
        val height=(document.format.height*scale).toInt();val margin=if(signature) (48*scale).toInt().coerceAtLeast(1) else 0
        val bitmap=Bitmap.createBitmap(width,height+margin,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.save();canvas.scale(scale,scale);draw(canvas,document);if(markers) wireDrawMarkers(canvas,document);canvas.restore()
        if(signature) {paint.color=Color.LTGRAY;canvas.drawRect(0f,height.toFloat(),width.toFloat(),(height+margin).toFloat(),paint);paint.color=Color.DKGRAY;paint.textSize=24*scale;canvas.drawText("Arachn0de · Wireframe",16*scale,height+32*scale,paint)}
        return bitmap
    }
    fun png(document:WireDocument,signature:Boolean,output:OutputStream,markers:Boolean=false) {val bitmap=bitmap(document,signature=signature,markers=markers);try {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) {"No se pudo exportar el PNG."}} finally {bitmap.recycle()}}
    override fun close() {images.clear()}
}
