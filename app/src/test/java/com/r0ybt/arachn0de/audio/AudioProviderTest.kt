package com.r0ybt.arachn0de.audio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.BackupFixture
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Arachn0deApplication::class,sdk=[28])
class AudioProviderTest {
    private val app get()=ApplicationProvider.getApplicationContext<Arachn0deApplication>()
    private suspend fun open(primary:Boolean):VaultSession {
        val id=UUID.randomUUID();val root=File(app.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val access=SecureFiles.Session(id,root,key);key.fill(0);SecureFiles.register(access)
        val context=VaultContext(app,access).apply {this.primary=primary}
        val db=Room.databaseBuilder(app,Arachn0deDatabase::class.java,context.getDatabasePath("arachn0de.db").absolutePath).build()
        context.database=db
        val session=VaultSession(access,context,db,primary)
        SecureFiles.activeProfile=id;app.security.current.value=session
        withTimeout(5000) {while(!session.audio.canStartRecording) delay(10)}
        return session
    }
    // Robolectric's pipe is a regular file and can report EOF before its writer runs.
    private suspend fun readPipe(fd:ParcelFileDescriptor,size:Int):ByteArray=ParcelFileDescriptor.AutoCloseInputStream(fd).use {input->
        val output=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
        withTimeout(5000) {while(output.size()<size) {val n=input.read(b);if(n<0) delay(10) else output.write(b,0,n)}}
        output.toByteArray()
    }
    @After fun close()=runBlocking {app.security.lock()}
    @Test fun mixedDescriptionClipboardAndCardImageExportKeepAudioDistinctFromPictures()=runBlocking<Unit> {
        val main=open(true);val project=main.projects.createProject("Contexto");val node=main.nodes.createNode(project.id,null,"Título","Texto anterior")
        val bytes=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
        val recordings=AudioDraftStore(main.context,main.database,BackupFixture::syncDirectory)
        val id=recordings.begin(node.id);recordings.append(id,0,bytes);val audio=recordings.finish(id)!!
        val repository=AttachmentRepository(main.database,AttachmentStore(main.context,BackupFixture::syncDirectory))
        val source=File(app.cacheDir,"card-audio-original.png").apply {writeBytes(BackupFixture.png())}
        val image=repository.import(Uri.fromFile(source),"mixed")
        val text="Texto anterior\n**"+com.r0ybt.arachn0de.domain.model.AttachmentReferences.audioToken(id)+"**\n"+com.r0ybt.arachn0de.domain.model.AttachmentReferences.token(image.id)
        repository.associateNode(node.id,image.id,text)
        val parts=com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(text)
        assertTrue(parts.first {it.imageId==id}.audio);assertFalse(parts.first {it.imageId==image.id}.audio)
        assertTrue(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Bold in parts.first {it.imageId==id}.formats)
        val clipboard=com.r0ybt.arachn0de.ui.state.descriptionClipboardText(text,mapOf(id to audio.originalName,image.id to image.originalName))
        assertTrue(clipboard.contains("[Audio: ${audio.originalName}]"));assertTrue(clipboard.contains("[Imagen: ${image.originalName}]"))
        val card=com.r0ybt.arachn0de.report.CardShareSource.snapshot(app,main.context,node.id)
        assertEquals(1,card.pictures.size);assertNotNull(card.pictures.single().load());assertTrue(card.information.contains("Audio: ${audio.originalName}"));assertTrue(card.information.contains("Texto anterior"))
        source.delete()
    }
    @Test fun pausingPlaybackResumesAutomaticProtectionWithoutRequiringBarClosure()=runBlocking<Unit> {
        val main=open(true)
        main.context.getSharedPreferences(ScreenCapturePreference.NAME,0).edit().putLong(AutoLockPreference.KEY,0L).commit()
        main.audio.state.value=AudioState(fileId="playback",durationMs=1000,positionMs=200)
        assertTrue(main.audio.active)
        app.security.background();delay(40);assertSame(main,app.security.current.value)
        main.audio.pause()
        withTimeout(5000) {while(app.security.current.value!=null) delay(10)}
        assertNull(app.security.current.value)
        assertFalse(main.audio.state.value.visible)
    }
    @Test fun grantedUriStreamsOriginalEncryptedAudioAndImageOnlyForItsActiveVault()=runBlocking<Unit> {
        val main=open(true);val project=main.projects.createProject("Contexto");val node=main.nodes.createNode(project.id,null,"Audio")
        val tone=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
        val recordings=AudioDraftStore(main.context,main.database,BackupFixture::syncDirectory)
        val id=recordings.begin(node.id);recordings.append(id,0,tone);val audio=recordings.finish(id)!!
        val repository=AttachmentRepository(main.database,AttachmentStore(main.context,BackupFixture::syncDirectory))
        val image=File(main.context.cacheDir,"original.png");SecureFiles.write(image,BackupFixture.png())
        // Picker sources are outside the vault in this fixture; import preserves the original bytes.
        val outside=File(app.cacheDir,"audio-export-image.png").apply {writeBytes(BackupFixture.png())}
        val picture=repository.import(Uri.fromFile(outside),"image");repository.associateNode(node.id,picture.id)
        val provider=AttachmentExportProvider().apply {attachInfo(app,ProviderInfo().apply {authority="${app.packageName}.attachments";exported=false;grantUriPermissions=true})}
        fun uri(row:AttachmentFileEntity)=Uri.parse("content://${app.packageName}.attachments/${main.access.id}/${row.id}")
        assertEquals("audio/aac",provider.getType(uri(audio)));assertEquals("image/png",provider.getType(uri(picture)))
        assertArrayEquals(tone,readPipe(provider.openFile(uri(audio),"r"),tone.size))
        assertArrayEquals(BackupFixture.png(),readPipe(provider.openFile(uri(picture),"r"),BackupFixture.png().size))
        assertFalse(repository.localFile(audio).readBytes().contentEquals(tone))
        assertTrue(runCatching {provider.openFile(uri(audio),"w")}.isFailure)
        app.security.lock()
        assertTrue(runCatching {provider.getType(uri(audio))}.isFailure)
        val secondary=open(false)
        assertTrue(secondary.database.attachmentDao().files().isEmpty())
        assertTrue(runCatching {provider.openFile(uri(audio),"r")}.isFailure)
        assertFalse(secondary.audio.state.value.visible)
        outside.delete()
    }
}
