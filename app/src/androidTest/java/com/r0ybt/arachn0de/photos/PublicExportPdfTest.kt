package com.r0ybt.arachn0de.photos

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.annotations.*
import com.r0ybt.arachn0de.document.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Synthetic real-PdfDocument artifact check; requires an Android device/emulator. */
@RunWith(AndroidJUnit4::class)
class PublicExportPdfTest {
    @Test fun exportedPdfContainsVisiblePixelsWithoutTheEditableOriginal() {
        fun png(color:Int):ByteArray {
            val b=Bitmap.createBitmap(48,24,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
            return try {markedPng(b,emptyList())} finally {b.recycle()}
        }
        val original=png(Color.RED);val visible=png(Color.BLACK)
        val private=EditableImagePng.write(visible,original,emptyList());val before=private.copyOf()
        val image=reportImage(private)
        val content=ReportContent(listOf(ReportSection("Synthetic",0,"",emptyList(),listOf(ReportPart.Image("i","image")))),mapOf("i" to image))
        val options=ReportOptions("Synthetic",index=false);val plan=ReportLayout.measure(content,options)
        val output=ByteArrayOutputStream();writeReportPdf(content,plan,output)
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val file=File(context.cacheDir,"public-pdf-test-${UUID.randomUUID()}.pdf")
        try {
            val bytes=output.toByteArray();assertTrue(bytes.toString(Charsets.ISO_8859_1).startsWith("%PDF-"))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("arNd"))
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->PdfRenderer(fd).use {pdf->
                pdf.openPage(0).use {page->
                    val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val r=plan.pages.first().items.filterIsInstance<ReportDraw.Image>().single().rect
                        assertEquals(Color.BLACK,bitmap.getPixel(r.centerX().toInt(),r.centerY().toInt()))
                        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)assertNotEquals(Color.RED,bitmap.getPixel(x,y))
                    } finally {bitmap.recycle()}
                }
            }}
            assertArrayEquals(before,private);assertArrayEquals(original,EditableImagePng.read(private)!!.original)
        } finally {content.release();file.delete()}
    }
}
