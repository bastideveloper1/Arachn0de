package com.r0ybt.arachn0de.ui

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.ui.text.*

/** Offline recognition; only explicit HTTP(S) links can open an external browser. */
internal fun AnnotatedString.Builder.appendWebLinks(text:String,color:androidx.compose.ui.graphics.Color,onOpen:(String)->Unit) {
    val spans=SpannableString(text);Linkify.addLinks(spans,Linkify.WEB_URLS)
    var at=0
    spans.getSpans(0,spans.length,URLSpan::class.java).sortedBy {spans.getSpanStart(it)}.forEach {span->
        val start=spans.getSpanStart(span);val end=spans.getSpanEnd(span)
        val raw=text.substring(start,end)
        val uri=Uri.parse(raw)
        if(uri.scheme?.lowercase() !in setOf("http","https") || uri.host.isNullOrBlank()) return@forEach
        append(text.substring(at,start))
        withLink(LinkAnnotation.Clickable(raw,TextLinkStyles(style=SpanStyle(color=color)),linkInteractionListener={onOpen(raw)})) {append(raw)}
        at=end
    }
    append(text.substring(at))
}
internal fun openDescriptionWebLink(context:android.content.Context,url:String) {
    val uri=Uri.parse(url)
    if(uri.scheme?.lowercase() !in setOf("http","https") || uri.host.isNullOrBlank()) return
    try {context.startActivity(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE).apply {if(context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})}
    catch(_:android.content.ActivityNotFoundException) {android.widget.Toast.makeText(context,"No hay un navegador disponible.",android.widget.Toast.LENGTH_SHORT).show()}
    catch(_:SecurityException) {android.widget.Toast.makeText(context,"No se pudo abrir el enlace.",android.widget.Toast.LENGTH_SHORT).show()}
}
