package com.r0ybt.arachn0de.photos

import com.r0ybt.arachn0de.backup.BackupData
import com.r0ybt.arachn0de.security.SecureFiles
import android.graphics.BitmapFactory

internal fun originalAvatars(data:BackupData):Boolean {
    fun large(input:java.io.InputStream):Boolean=input.use {
        val b=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeStream(it,null,b);b.outWidth>2048 || b.outHeight>2048
    }
    return data.avatars.any {(name,bytes)->name.endsWith(".jpg") || large(bytes.inputStream())} ||
        data.imageFiles.any {it.directory=="avatars" && (it.name.endsWith(".jpg") || large(SecureFiles.input(data.imageContents.getValue(it.key))))}
}
