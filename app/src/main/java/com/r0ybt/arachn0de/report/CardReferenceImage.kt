package com.r0ybt.arachn0de.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.r0ybt.arachn0de.security.SecureFiles
import java.io.File

/** Export samples by displayed width, never by portrait height; originals stay untouched. */
internal fun decodeCardReference(file:File):Bitmap? {
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    SecureFiles.decoded(file,bounds)
    require(bounds.outWidth>0 && bounds.outHeight>0) {"Imagen de referencia ilegible."}
    val orientation=SecureFiles.orientation(file)
    val visualWidth=if(orientation in 5..8)bounds.outHeight else bounds.outWidth
    var sample=1
    while(visualWidth/(sample*2)>=976)sample*=2
    require((bounds.outWidth.toLong()/sample)*(bounds.outHeight.toLong()/sample)<=12_000_000L) {
        "La imagen de referencia supera el tamaño seguro; no se exportará como miniatura."
    }
    val source=SecureFiles.decoded(file,BitmapFactory.Options().apply {inSampleSize=sample}) ?: return null
    val matrix=Matrix().apply {
        when(orientation) {
            2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
            7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
        }
    }
    if(matrix.isIdentity)return source
    return try {Bitmap.createBitmap(source,0,0,source.width,source.height,matrix,true)} finally {source.recycle()}
}
