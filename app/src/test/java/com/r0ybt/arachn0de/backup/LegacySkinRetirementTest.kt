package com.r0ybt.arachn0de.backup

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.legacy.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Real encrypted files with plain Room substituted for SQLCipher's native JVM backend. */
@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class LegacySkinRetirementTest {
    private class Store:AutoCloseable {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.filesDir,"legacy-skin-${UUID.randomUUID()}").apply {mkdirs()}
        val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
        val context=VaultContext(base,owner)
        val db=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java).build()
        val backup:BackupRepository
        init {SecureFiles.register(owner);SecureFiles.activeProfile=owner.id;context.database=db;backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))}
        override fun close() {context.clearMemory();db.close();SecureFiles.revoke(owner);root.deleteRecursively()}
    }
    @Test fun appearanceIgnoresSkinsAndPersistsOfficialThemes() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val prefs=context.getSharedPreferences("retired-skin",0)
        prefs.edit().clear().putString("custom_themes","not JSON: must never be decoded by appearance")
            .putString("custom_theme","retired").putString("theme","GreenBash").putString("text_size","Large").commit()
        val appearance=AppearancePreferences(prefs)
        assertEquals(AppearanceTheme.Arachn0de,appearance.settings.theme)
        assertEquals(AppearanceTextSize.Large,appearance.settings.textSize)
        assertFalse(appearance.settings.bashTheme)
        selectableAppearanceThemes.forEach {theme->
            appearance.setTheme(theme)
            assertEquals(theme,AppearancePreferences(prefs).settings.theme)
            assertEquals(theme.palette,appearance.settings.palette)
        }
        assertTrue(prefs.getString("custom_themes",null)!!.startsWith("not JSON"))
    }

    @Test fun legacySchemasRestoreWithOfficialFallback()=runBlocking<Unit> {
        Store().use {store->
            val png=BackupFixture.png();val preview=com.r0ybt.arachn0de.visual.canvasImage(png)
            val skins=listOf(CustomTheme(name="Schema 1"),CustomTheme(name="Schema 2",images=mapOf("task" to ThemeImage(preview,CustomThemeCodec.hash(android.util.Base64.decode(preview,2))))))
            for(skin in listOf<CustomTheme?>(null)+skins) {
                val values=mutableMapOf<String,Any?>("theme" to AppearanceTheme.GreenBash.name,"text_size" to "Large")
                if(skin!=null) {values[CustomThemeCodec.LIBRARY]=CustomThemeCodec.library(listOf(skin));values[CustomThemeCodec.ACTIVE]=skin.id}
                val data=BackupFixture.complete().copy(privatePreferences=listOf(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(values))))
                val decoded=BackupJson.decode(BackupJson.encode(data))
                store.backup.restore(decoded)
                assertEquals(data.nodes.sortedBy {it.id},store.db.backupDao().nodes().sortedBy {it.id})
                store.context.reloadPreferences()
                val appearance=AppearancePreferences(store.context.getSharedPreferences(AppearancePreferences.FILE,0))
                assertEquals(if(skin==null)AppearanceTheme.GreenBash else AppearanceTheme.Arachn0de,appearance.settings.theme)
                assertEquals(AppearanceTextSize.Large,appearance.settings.textSize)
                assertEquals(data.privatePreferences,store.backup.snapshot().privatePreferences)
            }
        }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun originalsAndAttachmentsSurvivePortableRestore()=runBlocking<Unit> {
        Store().use {source->Store().use {target->
            val png=BackupFixture.png();val name="${UUID.randomUUID()}.img"
            val original=ThemeOriginal(name,CustomThemeCodec.hash(png),png.size.toLong(),32,32,"image/png")
            val originalFile=File(source.context.filesDir,"theme-originals/$name").apply {parentFile!!.mkdirs()}
            SecureFiles.write(originalFile,png)
            val preview=com.r0ybt.arachn0de.visual.canvasImage(png)
            val image=ThemeImage(preview,CustomThemeCodec.hash(android.util.Base64.decode(preview,2)),original=original)
            val skin=CustomTheme(name="Historical",base=AppearanceTheme.GreenBash,images=mapOf("core" to image,"task" to image))
            source.db.privatePreferenceDao().save(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf(
                "theme" to "GreenBash",CustomThemeCodec.LIBRARY to CustomThemeCodec.library(listOf(skin)),CustomThemeCodec.ACTIVE to skin.id))))
            val project=ProjectRepository(source.db.projectDao(),source.db).createProject("Normal core")
            val node=NodeRepository(source.db).createNode(project.id,null,"Normal task","Normal text")
            val input=File(source.base.cacheDir,"normal-${UUID.randomUUID()}.png").apply {writeBytes(png)}
            val attachments=AttachmentRepository(source.db,AttachmentStore(source.context,BackupFixture::syncDirectory))
            val row=attachments.import(Uri.fromFile(input),"normal-photo")
            attachments.associateNode(node.id,row.id,"Normal text "+AttachmentReferences.token(row.id))
            val archive=source.backup.create("synthetic-legacy-backup".toCharArray())
            val inspected=target.backup.inspect(SecureFiles.read(archive).inputStream(),"synthetic-legacy-backup".toCharArray())
            target.backup.restore(inspected)
            val beforeCorruption=target.backup.snapshot()
            val incoming=inspected.imageContents.getValue("theme-originals/$name")
            SecureFiles.write(incoming,png.copyOf(png.size-1))
            assertTrue(runCatching {target.backup.restore(inspected)}.isFailure)
            assertEquals(beforeCorruption.nodes,target.backup.snapshot().nodes)
            assertEquals(beforeCorruption.privatePreferences,target.backup.snapshot().privatePreferences)
            incoming.delete()
            assertTrue(runCatching {target.backup.restore(inspected)}.isFailure)
            target.backup.discard(inspected)
            val restored=target.backup.snapshot()
            assertEquals(source.db.nodeDao().getById(node.id),target.db.nodeDao().getById(node.id))
            val restoredFile=target.db.attachmentDao().get(row.id)!!
            assertArrayEquals(png,SecureFiles.read(AttachmentStore(target.context,BackupFixture::syncDirectory).file(restoredFile.storageName)))
            val resources=themeOriginalReferences(restored.privatePreferences)
            assertEquals(1,resources.size)
            assertFalse(resources.containsKey(name))
            val restoredOriginal=File(target.context.filesDir,"theme-originals/${resources.keys.single()}")
            assertArrayEquals(png,SecureFiles.read(restoredOriginal));assertArrayEquals(png,SecureFiles.read(originalFile))
            assertFalse(restoredOriginal.readBytes().contentEquals(png))
            assertEquals(AppearanceTheme.Arachn0de,AppearancePreferences(target.context.getSharedPreferences(AppearancePreferences.FILE,0)).settings.theme)
            assertTrue(restored.privatePreferences.single {it.name==AppearancePreferences.FILE}.payload.contains("Historical"))
            source.backup.discardPending(archive.name);input.delete()
        }}
    }

    @Test fun invalidLegacyRestoreKeepsExistingData()=runBlocking<Unit> {
        Store().use {store->
            val baseline=BackupFixture.complete();store.backup.restore(baseline)
            val before=store.backup.snapshot()
            val bad=PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf(CustomThemeCodec.LIBRARY to "[{bad]")))
            assertTrue(runCatching {store.backup.restore(before.copy(privatePreferences=listOf(bad)))}.isFailure)
            assertEquals(before.nodes,store.backup.snapshot().nodes)
            store.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_skin_restore BEFORE INSERT ON private_preferences BEGIN SELECT RAISE(ABORT,'injected'); END")
            val skin=CustomTheme(name="Legacy")
            val pref=PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf(CustomThemeCodec.LIBRARY to CustomThemeCodec.library(listOf(skin)),CustomThemeCodec.ACTIVE to skin.id)))
            assertTrue(runCatching {store.backup.restore(before.copy(privatePreferences=listOf(pref)))}.isFailure)
            assertEquals(before.nodes,store.backup.snapshot().nodes);assertEquals(before.privatePreferences,store.backup.snapshot().privatePreferences)
        }
    }
}
