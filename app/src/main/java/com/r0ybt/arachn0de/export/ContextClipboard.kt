package com.r0ybt.arachn0de.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Standard plain-text clipboard delivery, with no file or networking dependency. */
class ContextClipboard(context: Context) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    fun copy(text: String) {
        require(text.isNotBlank())
        require(text.length <= NodeExportSnapshot.MAX_TEXT_CHARS)
        val clip=ClipData.newPlainText("Contexto de Arachn0de",text)
        clip.description.extras=android.os.PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
        clipboard.setPrimaryClip(clip)
    }
}
