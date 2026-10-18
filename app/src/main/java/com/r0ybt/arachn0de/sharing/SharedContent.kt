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
            val rawSubject=intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty().ifBlank {intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty()}
            val clips=intent.clipData
            val rawText=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().ifBlank {
                (0 until (clips?.itemCount ?: 0)).mapNotNull {clips?.getItemAt(it)?.text?.toString()}.distinct().joinToString("\n")
            }
            val bytes=Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(rawSubject+rawText)).remaining()
            val subject=cleanMetadata(rawSubject)
            val text=cleanMetadata(rawText)
            require(bytes<=TEXT_BYTES) {"El texto compartido supera 64 KiB."}
            require(subject.isNotBlank() || text.isNotBlank()) {"Solo se admiten textos y enlaces; no imágenes ni audios."}
            require(!(rawText.isBlank() && intent.type?.startsWith("text/")!=true && subject.matches(Regex("""(?i)[^/\\]+\.(?:png|jpe?g|gif|webp|heic|mp3|m4a|wav|ogg|aac|opus)""")))) {"Solo se admiten textos y enlaces; no imágenes ni audios."}
            val source=subject.trim().ifBlank {text.lineSequence().firstOrNull {it.isNotBlank()}?.trim().orEmpty()}
            val url=link(text) ?: link(subject)
            val title=if(url!=null) sharedTitle(subject,text,url) else shortTitle(source)
            val retainSubject=TitleLimits.count(subject)>TitleLimits.NODE || link(subject)!=null || (text.isBlank() && subject!=title)
            val existingUrls=web.findAll(text).map {it.value}.toSet()
            val extraSubject=web.replace(subject) {if(it.value in existingUrls) "" else it.value}.trim()
            val body=if(retainSubject && extraSubject.isNotBlank() && subject !in text) extraSubject+(if(text.isBlank()) "" else "\n\n"+text) else text
            // No stream or clip URI is read: new external captures are textual notes.
            return SharedContent(title,body,null,linkNote=true)
        }
        internal fun cleanMetadata(text:String):String=Regex(
            """(?im)^[ \t]*\[(?:Imagen|Audio):[ \t]*[^\]\r\n]+\.(?:png|jpe?g|gif|webp|heic|mp3|m4a|wav|ogg|aac|opus)\][ \t]*(?:\r?\n|$)"""
        ).replace(text,"")
    }
}
