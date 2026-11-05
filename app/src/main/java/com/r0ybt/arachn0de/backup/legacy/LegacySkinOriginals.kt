package com.r0ybt.arachn0de.backup.legacy

import com.r0ybt.arachn0de.ui.theme.AppearancePreferences

import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*

/** Immutable source, independently of the small historical PNG used by theme previews. */
data class ThemeOriginal(val name:String,val sha256:String,val byteSize:Long,val width:Int,val height:Int,val mime:String,val orientation:Int=1)
internal const val THEME_ORIGINAL_DIRECTORY="theme-originals"
internal const val THEME_ORIGINAL_JOURNAL="theme-original-restore-journal"
internal const val THEME_ORIGINAL_LIMIT=20*1024*1024
internal fun themeOriginalMime(mime:String?)=mime!=null&&mime.length<=100&&mime.matches(Regex("image/[a-z0-9.+-]+"))
internal fun validateThemeOriginal(bytes:ByteArray) {
    require(bytes.size in 1..THEME_ORIGINAL_LIMIT) {"La imagen supera 20 MiB."}
    val b=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,b)
    require(themeOriginalMime(b.outMimeType)&&b.outWidth>0&&b.outHeight>0&&b.outWidth.toLong()*b.outHeight<=100_000_000) {"Imagen no válida o demasiado grande."}
    var sample=1;while(maxOf(b.outWidth,b.outHeight)/sample>256)sample*=2
    val decoded=try {BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample})}
        catch(e:OutOfMemoryError) {throw IllegalArgumentException("No hay memoria suficiente para comprobar esta imagen.",e)}
    requireNotNull(decoded) {"La imagen está incompleta o no se puede decodificar."};decoded.recycle()
}
internal fun themeOriginalOrientation(bytes:ByteArray):Int =
    if(com.r0ybt.arachn0de.photos.PublicWebp.matches(bytes) || com.r0ybt.arachn0de.photos.PublicPng.matches(bytes)) com.r0ybt.arachn0de.photos.PrivatePhoto.sourceOrientation(bytes)
    else runCatching {ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1).let {if(it in 1..8)it else 1}
internal fun verifyThemeOriginal(original:ThemeOriginal,bytes:ByteArray) {
    validateThemeOriginal(bytes)
    require(bytes.size.toLong()==original.byteSize&&CustomThemeCodec.hash(bytes)==original.sha256) {"Original ausente o dañado."}
    val b=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,b)
    val orientation=themeOriginalOrientation(bytes)
    require(b.outWidth==original.width&&b.outHeight==original.height&&b.outMimeType==original.mime&&(orientation==original.orientation || original.orientation==1 && (com.r0ybt.arachn0de.photos.PublicWebp.matches(bytes) || com.r0ybt.arachn0de.photos.PublicPng.matches(bytes)))) {"Metadatos del original incompatibles."}
}
internal fun themeOriginalReferences(rows:List<PrivatePreferenceEntity>):Map<String,ThemeOriginal> {
    val row=rows.firstOrNull {it.name==AppearancePreferences.FILE} ?: return emptyMap()
    val values=EncryptedPreferences.decode(row.payload)
    return originalReferences(CustomThemeCodec.library(values[CustomThemeCodec.LIBRARY] as? String))
}
internal fun originalReferences(themes:List<CustomTheme>):Map<String,ThemeOriginal> {
    val values=themes.flatMap {it.images.values.mapNotNull {image->image.original}}
    require(values.groupBy {it.name}.values.all {it.distinct().size==1}) {"Metadatos de original incompatibles."}
    return values.associateBy {it.name}
}
internal fun remapThemeOriginals(row:PrivatePreferenceEntity,names:Map<String,String>):PrivatePreferenceEntity {
    if(row.name!=AppearancePreferences.FILE||names.isEmpty())return row
    val values=EncryptedPreferences.decode(row.payload).toMutableMap()
    if(CustomThemeCodec.LIBRARY !in values)return row
    val themes=CustomThemeCodec.library(values[CustomThemeCodec.LIBRARY] as String)
    values[CustomThemeCodec.LIBRARY]=CustomThemeCodec.library(themes.map {t->t.copy(images=t.images.mapValues {(_,i)->i.copy(original=i.original?.let {it.copy(name=names.getValue(it.name))})})})
    return row.copy(payload=EncryptedPreferences.encode(values))
}
