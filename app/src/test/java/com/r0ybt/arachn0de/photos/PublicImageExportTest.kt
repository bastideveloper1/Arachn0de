package com.r0ybt.arachn0de.photos

import android.app.Application
import android.graphics.*
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.annotations.*
import com.r0ybt.arachn0de.audio.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.document.*
import com.r0ybt.arachn0de.export.*
import com.r0ybt.arachn0de.media.ContentGif
import com.r0ybt.arachn0de.report.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.*
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublicImageExportTest {
    private val secret="GPS-XMP-PRIVATE-THUMBNAIL-IDENTIFIER"
    private fun chunk(type:String,payload:ByteArray):ByteArray=ByteArrayOutputStream().apply {
        val d=DataOutputStream(this);val t=type.toByteArray();d.writeInt(payload.size);d.write(t);d.write(payload)
        d.writeInt(CRC32().apply {update(t);update(payload)}.value.toInt())
    }.toByteArray()
    private fun metadata(png:ByteArray)=png.copyOfRange(0,33)+
        chunk("tEXt",("GPS\u0000"+secret).toByteArray())+chunk("iTXt",("XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000"+secret).toByteArray())+
        chunk("vpAg",secret.toByteArray())+png.copyOfRange(33,png.size)
    private fun image(color:Int=Color.RED,w:Int=48,h:Int=24):ByteArray {
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(color)
        return try {markedPng(b,emptyList())} finally {b.recycle()}
    }
    private fun editable():Pair<ByteArray,ByteArray> {
        val original=metadata(image(Color.RED))
        // The visible result covers the original completely; private source remains editable.
        val visible=image(Color.BLACK)
        return EditableImagePng.write(visible,original,emptyList()) to visible
    }
    private fun chunks(bytes:ByteArray):List<String> {
        val d=DataInputStream(bytes.inputStream());d.skipBytes(8)
        return buildList {while(d.available()>0) {val n=d.readInt();add(String(ByteArray(4).also(d::readFully)));d.skipBytes(n+4)}}
    }
    private fun clean(bytes:ByteArray) {
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(secret))
        assertTrue(chunks(bytes).all {it in setOf("IHDR","PLTE","IDAT","tRNS","IEND","sRGB","gAMA","cHRM","sBIT")})
        assertNull(EditableImagePng.read(bytes))
    }
    private fun unzip(bytes:ByteArray)=linkedMapOf<String,ByteArray>().apply {
        ZipInputStream(bytes.inputStream()).use {z->while(true) {val e=z.nextEntry ?: break;put(e.name,z.readBytes())}}
    }
    @Test fun editableAndOpaquePngBlocksAreRemovedWithoutChangingVisiblePixelsOrPrivateBytes() {
        val (private,visible)=editable();val before=private.copyOf()
        val output=PublicImages.prepare(metadata(private));clean(output)
        assertArrayEquals(before,private)
        assertNotNull(EditableImagePng.read(private))
        assertArrayEquals(PrivatePhoto.clean(visible).bytes,output)
        val b=BitmapFactory.decodeByteArray(output,0,output.size)
        assertEquals(48,b.width);assertEquals(24,b.height);assertEquals(Color.BLACK,b.getPixel(20,10));b.recycle()
        assertArrayEquals(output,PublicImages.prepare(output)) // No unnecessary recompression.
    }
    @Test fun transparencyAndDimensionsArePreservedAndCorruptionFailsClosed() {
        val input=metadata(image(0x40804020));val out=PublicImages.prepare(input);clean(out)
        val a=BitmapFactory.decodeByteArray(input,0,input.size);val b=BitmapFactory.decodeByteArray(out,0,out.size)
        assertEquals(a.getPixel(5,5),b.getPixel(5,5));assertEquals(a.width,b.width);assertEquals(a.height,b.height)
        assertEquals(0x40,Color.alpha(b.getPixel(5,5)));a.recycle();b.recycle()
        assertTrue(runCatching {PublicImages.prepare(input.copyOf(input.size/2))}.isFailure)
        val corrupt=input.copyOf();corrupt[43]=(corrupt[43].toInt() xor 1).toByte()
        assertTrue(runCatching {PublicImages.prepare(corrupt)}.isFailure)
        assertTrue(runCatching {PublicImages.prepare("not an image".toByteArray())}.isFailure)
    }
    @Test
    @Config(sdk=[24])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun metadataPolicyAlsoWorksOnMinimumSdkWithoutColorSpaceApis() {
        val source=metadata(image(Color.BLACK))
        val before=source.copyOf();val out=PublicImages.prepare(source)
        clean(out);assertArrayEquals(before,source)
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(out,0,out.size,bounds)
        assertEquals(48,bounds.outWidth);assertEquals(24,bounds.outHeight)
    }
    @Test fun jpegDropsExifGpsXmpThumbnailCommentsAndRetainsVisualOrientation() {
        val b=Bitmap.createBitmap(48,24,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        val raw=ByteArrayOutputStream().also {b.compress(Bitmap.CompressFormat.JPEG,95,it)}.toByteArray();b.recycle()
        fun segment(marker:Int,value:ByteArray)=byteArrayOf(-1,marker.toByte(),((value.size+2) shr 8).toByte(),(value.size+2).toByte())+value
        val exif=byteArrayOf(69,120,105,102,0,0,77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,6,0,0,0,0,0,0)+secret.toByteArray()
        val source=raw.copyOfRange(0,2)+segment(225,exif)+segment(225,("http://ns.adobe.com/xap/1.0/\u0000"+secret).toByteArray())+
            segment(254,secret.toByteArray())+raw.copyOfRange(2,raw.size)+secret.toByteArray()
        val before=source.copyOf();val out=PublicImages.prepare(source)
        assertArrayEquals(before,source);assertFalse(out.toString(Charsets.ISO_8859_1).contains(secret))
        val visual=PrivatePhoto.orient(BitmapFactory.decodeByteArray(out,0,out.size),PrivatePhoto.sourceOrientation(out))
        assertEquals(24,visual.width);assertEquals(48,visual.height);visual.recycle()
        assertArrayEquals(out,PublicImages.prepare(out))
        assertTrue(runCatching {PublicImages.prepare(source.copyOf(source.size/2))}.isFailure)
    }
    @Test fun gifKeepsFramesPalettesTimingTransparencyAndLoopingWithoutOpaqueExtensions() {
        val raw=javaClass.getResourceAsStream("/content/two-frames.gif")!!.use {it.readBytes()}
        val comment=byteArrayOf(0x21,0xfe.toByte(),secret.length.toByte())+secret.toByteArray()+byteArrayOf(0)
        val source=raw.copyOfRange(0,raw.size-1)+comment+byteArrayOf(0x3b)
        val output=PublicImages.prepare(source)
        assertEquals(ContentGif.inspect(raw),ContentGif.inspect(output))
        assertArrayEquals(PublicImages.prepare(raw),output)
        assertFalse(output.toString(Charsets.ISO_8859_1).contains(secret))
        assertArrayEquals(output,PublicImages.prepare(output));assertTrue(runCatching {PublicImages.prepare(source.copyOf(source.size-1))}.isFailure)
        val text=raw.copyOfRange(0,raw.size-1)+byteArrayOf(0x21,1,0,0x3b)
        assertTrue(runCatching {PublicImages.prepare(text)}.isFailure)
    }
    @Test fun individualAndBothZipFormatsSanitizeFinalBytesAndPublicManifestButBackupKeepsOriginal()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val access=SecureFiles.Session(UUID.randomUUID(),File(context.filesDir,"public-${UUID.randomUUID()}").apply {mkdirs()},VaultCrypto.randomKey())
        SecureFiles.register(access);val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        val store=AttachmentStore(vault,BackupFixture::syncDirectory);val repo=AttachmentRepository(db,store)
        val (private,_)=editable();val source=File(context.cacheDir,"public-source.png").apply {writeBytes(private)}
        try {
            db.projectDao().insert(ProjectEntity("p","Public test","",0,1,1))
            db.nodeDao().insert(NodeEntity("n","p",null,"Note","",false,0,1,1))
            val row=repo.import(Uri.fromFile(source),"draft");repo.associateNode("n",row.id)
            val individual=publicAttachmentBytes(repo,row);clean(individual)
            val avatars=AvatarStore(vault,syncDirectory=BackupFixture::syncDirectory)
            val avatarName="${UUID.randomUUID()}.png"
            val avatarFile=File(File(vault.filesDir,"avatars").apply {mkdirs()},avatarName)
            SecureFiles.write(avatarFile,private)
            val avatarOut=ByteArrayOutputStream();val avatarUri=Uri.parse("content://synthetic/avatar.png")
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(avatarUri,avatarOut)
            avatars.export(avatarName,avatarUri);clean(avatarOut.toByteArray());assertArrayEquals(individual,avatarOut.toByteArray())
            assertArrayEquals(private,SecureFiles.read(avatarFile))
            val memories=PersonMemoryRepository(db,vault,repo,PersonRepository(db,avatars))
            val memoryImage=PersonMemoryImageEntity("i","m",row.id,null,0)
            val memoryState=PersonMemoryState(emptyList(),listOf(memoryImage),listOf(row),emptyList())
            val memoryOut=ByteArrayOutputStream();val memoryUri=Uri.parse("content://synthetic/memory.png")
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(memoryUri,memoryOut)
            memories.export(memoryImage,memoryState,memoryUri);clean(memoryOut.toByteArray());assertArrayEquals(individual,memoryOut.toByteArray())
            val data=BackupData("test",1,emptyList(),listOf(db.nodeDao().getById("n")!!),emptyList(),emptyList(),emptyMap(),
                attachmentFiles=listOf(row),nodeAttachments=listOf(NodeAttachmentEntity("n",row.id)),attachmentContents=mapOf(row.storageName to store.file(row.storageName)))
            val export=ElementExport(data,listOf("n" to 0),"p","n",false)
            for(json in listOf(false,true)) {
                val out=ByteArrayOutputStream();export.write(out,json,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory);val entries=unzip(out.toByteArray())
                val bytes=entries.getValue("files/attachments/${row.id}.png");clean(bytes);assertArrayEquals(individual,bytes)
                assertFalse(out.toByteArray().toString(Charsets.ISO_8859_1).contains(secret))
                if(json) {
                    val record=JSONObject(String(entries.getValue("element.json"))).getJSONObject("records").getJSONArray("attachmentFiles").getJSONObject(0)
                    assertEquals(bytes.size.toLong(),record.getLong("byteSize"));assertEquals(imageHash(bytes),record.getString("sha256"))
                }
                val artifact=File("/tmp/arachnode-phase-a-artifacts").apply {mkdirs()};File(artifact,if(json)"element-json.zip" else "element-markdown.zip").writeBytes(out.toByteArray())
            }
            assertArrayEquals(private,SecureFiles.read(store.file(row.storageName)));assertEquals(row,db.attachmentDao().get(row.id))
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val snapshot=backup.snapshot();val archive=ByteArrayOutputStream();BackupContainer.writeBackup(snapshot,archive)
            val inspect=File(vault.cacheDir,"roundtrip").apply {mkdirs()};val decoded=BackupContainer.readBackup(archive.toByteArray().inputStream(),inspect)
            assertArrayEquals(private,SecureFiles.read(decoded.attachmentContents.getValue(row.storageName)))
            val corrupt=private.copyOf(private.size/2);SecureFiles.write(store.file(row.storageName),corrupt)
            val rejected=ByteArrayOutputStream();assertTrue(runCatching {export.write(rejected,true,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)}.isFailure);assertEquals(0,rejected.size())
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(access);access.root.deleteRecursively();source.delete()}
    }
    @Test fun docxGeneratedCardAndPdfLayoutContainOnlyTheVisibleRepresentation()=runBlocking<Unit> {
        val (private,_)=editable();val image=reportImage(private);clean(image.bytes)
        val content=ReportContent(listOf(ReportSection("Public",0,"",emptyList(),listOf(ReportPart.Image("i","image")))),mapOf("i" to image))
        val options=ReportOptions("Public",index=false)
        val word=ByteArrayOutputStream();ReportDocx.write(content,options,word)
        val media=unzip(word.toByteArray()).filterKeys {it.startsWith("word/media/")}.values
        assertEquals(1,media.size);media.forEach {clean(it);val b=BitmapFactory.decodeByteArray(it,0,it.size);assertEquals(Color.BLACK,b.getPixel(20,10));b.recycle()}
        // Robolectric 4.17 does not implement PdfDocument's native writer. Exercise the
        // actual PDF drawing plan here; PublicExportPdfTest verifies a real PDF on Android.
        val plan=ReportLayout.measure(content,options)
        val rendered=Bitmap.createBitmap(ReportLayout.WIDTH,ReportLayout.HEIGHT,Bitmap.Config.ARGB_8888)
        ReportLayout.draw(Canvas(rendered),plan.pages.first(),content,1,plan.pages.size)
        val rect=plan.pages.first().items.filterIsInstance<ReportDraw.Image>().single().rect
        assertEquals(Color.BLACK,rendered.getPixel(rect.centerX().toInt(),rect.centerY().toInt()))
        val pdfPixels=ByteArrayOutputStream();rendered.compress(Bitmap.CompressFormat.PNG,100,pdfPixels);rendered.recycle()
        val context=ApplicationProvider.getApplicationContext<Application>()
        val cards=CardShareFiles(context,sync=BackupFixture::syncDirectory)
        val card=cards.generate({CardShareData("Public","",listOf(CardSharePicture("image") {BitmapFactory.decodeByteArray(private,0,private.size)}))},CardShareColors(Color.BLACK,Color.WHITE,Color.MAGENTA))
        val cardOutput=ByteArrayOutputStream();val uri=Uri.parse("content://synthetic/card.png")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(uri,cardOutput);cards.save(card,uri);clean(cardOutput.toByteArray())
        val artifact=File("/tmp/arachnode-phase-a-artifacts").apply {mkdirs()}
        File(artifact,"report.docx").writeBytes(word.toByteArray());File(artifact,"report-pdf-render.png").writeBytes(pdfPixels.toByteArray());File(artifact,"card.png").writeBytes(cardOutput.toByteArray())
        cards.discard(card);content.release()
    }
}
