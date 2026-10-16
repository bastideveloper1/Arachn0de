package com.r0ybt.arachn0de.sharing

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.r0ybt.arachn0de.domain.model.TitleLimits
import java.nio.CharBuffer

internal const val SHARE_ENABLED=true

/** Validated input only: no persistence, session assignment, URI reads or automatic navigation. */
internal data class SharedContent(val title:String,val description:String,val image:Uri?,val linkNote:Boolean=false) {
    companion object {
        const val TEXT_BYTES=64*1024
        private val web=Regex("https?://[^\\s<>\"]+",RegexOption.IGNORE_CASE)
        fun link(text:String):String? = web.find(text)?.value?.let {raw->
            var url=raw
            while(url.endsWith(")") && url.count {it==')'}>url.count {it=='('}) url=url.dropLast(1)
            url.takeIf {runCatching {val u=Uri.parse(it);u.scheme?.lowercase() in setOf("http","https") && !u.host.isNullOrBlank()}.getOrDefault(false)}
        }
        fun isLink(text:String)=link(text)==text && text.isNotBlank()
        private fun shortTitle(text:String)=text.substring(0,text.offsetByCodePoints(0,minOf(TitleLimits.NODE,text.codePointCount(0,text.length))))
        private fun sharedTitle(subject:String,text:String,url:String):String {
            fun clean(value:String)=web.replace(value,"").trim().trim('-', '|', ':', '—', '(', ')').trim()
            val explicit=clean(subject).takeIf {it.isNotBlank() && it.lines().size==1}
            val lines=text.trim().lines().filter {it.isNotBlank()}
            val mixed=when {
                lines.size==1->clean(lines.single())
                lines.size==2 && lines.last().trim()==url->clean(lines.first())
                else->""
            }
            return shortTitle(explicit ?: mixed.ifBlank {"Enlace compartido"})
        }
        fun read(intent:Intent):SharedContent {
            require(intent.action==Intent.ACTION_SEND) {"Solo se admite compartir un elemento."}
            val mime=intent.type?.lowercase(java.util.Locale.ROOT)
            require(mime in setOf("text/plain","image/png","image/jpeg")) {"Formato no compatible. Comparte texto, PNG o JPEG."}
            val originalSubject=intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
            val extraTitle=if(mime=="text/plain") intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty() else ""
            val subject=originalSubject.ifBlank {extraTitle}
            val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val bytes=Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(originalSubject+extraTitle+text)).remaining()
            require(bytes<=TEXT_BYTES) {"El texto compartido supera 64 KiB."}
            val stream=IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)
            val clips=intent.clipData
            require(clips==null || clips.itemCount<=1) {"Solo se admite una imagen por envío."}
            val clipUri=clips?.takeIf {it.itemCount==1}?.getItemAt(0)?.uri
            require(stream==null || clipUri==null || stream==clipUri) {"El envío contiene imágenes diferentes."}
            val image=stream ?: clipUri
            if(mime=="text/plain") require(image==null) {"El envío de texto contiene un adjunto no admitido."}
            else require(image!=null && image.scheme=="content") {"La imagen compartida no está disponible mediante una URI segura."}
            require(subject.isNotBlank() || text.isNotBlank() || image!=null) {"El envío está vacío."}
            if(mime=="text/plain") {
                val url=link(text) ?: link(subject)
                if(url!=null) return SharedContent(sharedTitle(subject,text,url),url,null,linkNote=true)
            }
            val source=subject.trim().takeIf {it.isNotEmpty()} ?: text.lineSequence().firstOrNull {it.isNotBlank()}?.trim()
                ?: "Imagen compartida"
            val title=source.substring(0,source.offsetByCodePoints(0,minOf(TitleLimits.NODE,source.codePointCount(0,source.length))))
            val retainSubject=TitleLimits.count(subject)>TitleLimits.NODE || (text.isEmpty() && subject.contains(Regex("https?://",RegexOption.IGNORE_CASE)))
            val description=if(retainSubject && subject !in text) subject+(if(text.isEmpty()) "" else "\n\n"+text) else text
            return SharedContent(title,description,image)
        }
    }
}
