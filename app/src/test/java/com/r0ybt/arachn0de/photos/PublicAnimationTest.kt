package com.r0ybt.arachn0de.photos

import android.app.Application
import android.graphics.*
import com.r0ybt.arachn0de.media.ContentGif
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublicAnimationTest {
    private fun fixture(name:String)=File("src/androidTest/assets/public-images/$name").readBytes()
    private fun chunks(b:ByteArray,png:Boolean):List<Pair<String,ByteArray>> {
        val list=mutableListOf<Pair<String,ByteArray>>();var at=if(png)8 else 12
        while(at+8<=b.size) {
            val n=if(png)java.nio.ByteBuffer.wrap(b,at,4).int else java.nio.ByteBuffer.wrap(b,at+4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val type=String(b,at+if(png)4 else 0,4,Charsets.US_ASCII);val start=at+8
            list+=type to b.copyOfRange(start,start+n);at+=n+if(png)12 else 8+(n and 1)
        };return list
    }
    private fun assertNoPrivate(b:ByteArray) {assertFalse(b.toString(Charsets.ISO_8859_1).contains("A1_PRIVATE"))}
    @Test fun webpStaticAndAllAnimatedControlsAndPixelPayloadsAreByteExact() {
        for(name in listOf("static.webp","animated.webp")) {
            val raw=fixture(name);val original=raw.copyOf();val result=PublicImages.file(raw)
            assertEquals("image/webp",result.mime);assertEquals(48,result.width);assertEquals(32,result.height)
            assertEquals(name.startsWith("animated"),result.animated);assertEquals(if(result.animated)3 else 1,result.frames)
            val before=chunks(raw,false).filter {it.first in setOf("ANIM","ANMF","ALPH","VP8 ","VP8L")}
            val after=chunks(result.bytes,false).filter {it.first in setOf("ANIM","ANMF","ALPH","VP8 ","VP8L")}
            assertEquals(before.map {it.first},after.map {it.first});before.zip(after).forEach {assertArrayEquals(it.first.second,it.second.second)}
            assertNoPrivate(result.bytes);assertFalse(chunks(result.bytes,false).any {it.first in setOf("EXIF","XMP ","ICCP")})
            assertArrayEquals(original,raw);assertArrayEquals(result.bytes,PublicImages.prepare(result.bytes))
            assertTrue(runCatching {PublicImages.prepare(raw.copyOf(raw.size-1))}.isFailure)
        }
    }
    @Test fun apngKeepsFramesDurationsBlendDisposalAndTransparencyWithAndWithoutDefaultFrame() {
        for(name in listOf("animated.png","animated-default.png")) {
            val raw=com.r0ybt.arachn0de.annotations.EditableImagePng.write(fixture(name),fixture("icc.png"),emptyList());val before=raw.copyOf();val result=PublicImages.file(raw)
            assertEquals("image/png",result.mime);assertTrue(result.animated);assertEquals(if(name.contains("default"))2 else 3,result.frames)
            val a=chunks(raw,true).filter {it.first in setOf("acTL","fcTL","fdAT","IDAT","IHDR","PLTE","tRNS")}
            val b=chunks(result.bytes,true).filter {it.first in setOf("acTL","fcTL","fdAT","IDAT","IHDR","PLTE","tRNS")}
            assertEquals(a.map {it.first},b.map {it.first});a.zip(b).forEach {assertArrayEquals(it.first.second,it.second.second)}
            assertNoPrivate(result.bytes);assertArrayEquals(before,raw)
            assertNotNull(com.r0ybt.arachn0de.annotations.EditableImagePng.read(raw));assertNull(com.r0ybt.arachn0de.annotations.EditableImagePng.read(result.bytes))
            val corrupt=result.bytes.copyOf();corrupt[corrupt.size/2]=(corrupt[corrupt.size/2].toInt() xor 1).toByte()
            assertTrue(runCatching {PublicImages.prepare(corrupt)}.isFailure)
        }
    }
    @Test fun gifPreservesConventionalAndPlainTextGraphicExtensionsAndDropsComments() {
        for(name in listOf("ordinary.gif","text.gif")) {
            val raw=fixture(name);val out=PublicImages.prepare(raw)
            assertEquals(ContentGif.inspect(raw),ContentGif.inspect(out));assertNoPrivate(out)
            if(name=="text.gif")assertTrue(out.toString(Charsets.ISO_8859_1).contains("TEXT"))
            assertArrayEquals(out,PublicImages.prepare(out));assertTrue(runCatching {PublicImages.prepare(raw.copyOf(raw.size-1))}.isFailure)
        }
    }
    @Test fun srgbIccCopiesAndDirectDocumentRenderingDoNotRetainProfileDescriptionsOrExif() {
        for(name in listOf("icc.png","icc.jpg")) {
            val raw=fixture(name);val file=PublicImages.file(raw);assertNoPrivate(file.bytes)
            val bitmap=PublicImages.render(raw);assertEquals(48,bitmap.width);assertEquals(32,bitmap.height);bitmap.recycle()
            assertEquals(if(name.endsWith("png"))"image/png" else "image/jpeg",file.mime)
        }
        val icc=java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_LINEAR_RGB).data
        val gray=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(128,128,128))}
        val encoded=ByteArrayOutputStream();gray.compress(Bitmap.CompressFormat.PNG,100,encoded);gray.recycle()
        val base=PublicImages.prepare(encoded.toByteArray())
        val compressed=ByteArrayOutputStream().apply {write("profile".toByteArray());write(0);write(0);DeflaterOutputStream(this).use {it.write(icc)}}.toByteArray()
        val extra=ByteArrayOutputStream();PublicPng.writeChunk(extra,"iCCP",compressed)
        val sourceOut=ByteArrayOutputStream();sourceOut.write(base,0,33);sourceOut.write(extra.toByteArray())
        chunks(base,true).filter {it.first !in setOf("IHDR","sRGB","gAMA","cHRM")}.forEach {PublicPng.writeChunk(sourceOut,it.first,it.second)}
        val source=sourceOut.toByteArray()
        val rendered=PublicImages.render(source,32);assertTrue(maxOf(rendered.width,rendered.height)<=32)
        assertEquals(188.0,Color.red(rendered.getPixel(10,10)).toDouble(),2.0);rendered.recycle()
        // Robolectric produces different PNG/color results for this profile. Numeric
        // file conversion is asserted on Android in PublicAnimationInstrumentedTest.
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(source,0,source.size,bounds)
        File("/tmp/arachnode-a1-tests/icc-robolectric-bounds.txt").writeText(bounds.outColorSpace.toString())
        val converted=PublicImages.file(source);assertEquals("image/png",converted.mime)
        val pixels=BitmapFactory.decodeByteArray(converted.bytes,0,converted.bytes.size)
        try {assertEquals(48,pixels.width);assertEquals(32,pixels.height);assertEquals(255,Color.alpha(pixels.getPixel(10,10)))} finally {pixels.recycle()}
    }
    @Test fun webpAndPngOrientationSurvivesWithoutPrivateExifAndLegacyDisplayCanStayUnrotated() {
        for(name in listOf("oriented.webp","oriented.png")) {
            val raw=fixture(name);assertEquals(6,PrivatePhoto.sourceOrientation(raw))
            val public=PublicImages.file(raw);assertEquals(6,public.orientation);assertNoPrivate(public.bytes)
            assertEquals(6,PrivatePhoto.sourceOrientation(public.bytes))
            val image=PublicImages.render(raw);assertEquals(32,image.width);assertEquals(48,image.height);image.recycle()
            val legacy=PublicImages.file(raw,1);assertEquals(1,PrivatePhoto.sourceOrientation(legacy.bytes))
            assertEquals(48,legacy.width);assertEquals(32,legacy.height)
        }
    }
    @Test @Config(sdk=[24]) @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun minimumSdkKeepsStructuralAnimationsAndFailsControlledForIcc() {
        for(name in listOf("static.webp","animated.webp","animated.png","animated-default.png","ordinary.gif","text.gif")) {
            val raw=fixture(name);val public=PublicImages.file(raw);assertNoPrivate(public.bytes)
            assertEquals(48,public.width);assertEquals(32,public.height)
        }
        for(name in listOf("icc.png","icc.jpg"))assertTrue(runCatching {PublicImages.file(fixture(name))}.isFailure)
    }
    @Test fun largePngValidationAndDocumentDecodeAreBoundedWithoutTheOld16Or100MpConversion() {
        val width=5000;val height=4000
        val compressed=ByteArrayOutputStream();DeflaterOutputStream(compressed).use {out->
            val row=ByteArray(width*4+1);for(x in 0 until width) {row[x*4+1]=90;row[x*4+4]=127};repeat(height) {out.write(row)}
        }
        val source=ByteArrayOutputStream();source.write(byteArrayOf(-119,80,78,71,13,10,26,10))
        val header=ByteArrayOutputStream();DataOutputStream(header).apply {writeInt(width);writeInt(height);write(byteArrayOf(8,6,0,0,0))}
        PublicPng.writeChunk(source,"IHDR",header.toByteArray());PublicPng.writeChunk(source,"IDAT",compressed.toByteArray());PublicPng.writeChunk(source,"IEND",byteArrayOf())
        val bytes=source.toByteArray();val before=System.nanoTime();val result=PublicImages.file(bytes)
        assertEquals(width,result.width);assertEquals(height,result.height)
        val bitmap=PublicImages.render(bytes,1024);assertTrue(bitmap.width<=1024 && bitmap.height<=1024);bitmap.recycle()
        File("/tmp/arachnode-a1-large-image.txt").writeText("pixels=${width.toLong()*height}; encoded=${bytes.size}; elapsedMs=${(System.nanoTime()-before)/1_000_000}")
    }
}
