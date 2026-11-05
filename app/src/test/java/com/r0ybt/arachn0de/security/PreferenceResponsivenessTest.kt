package com.r0ybt.arachn0de.security

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.backup.legacy.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PreferenceResponsivenessTest {
    private fun session():SecureFiles.Session {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val id=UUID.randomUUID()
        return SecureFiles.Session(id,File(base.filesDir,"prefs-$id").apply {mkdirs()},ByteArray(32) {7})
    }
    @Test fun initialNamespacesArePreparedOffMainAndRestorationRefreshesExistingHandles()=runBlocking<Unit> {
        val base=ApplicationProvider.getApplicationContext<Context>();val access=session()
        val queries=java.util.Collections.synchronizedList(mutableListOf<String>())
        val db=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java)
            .setQueryCallback({sql,_->queries.add(sql)},Executor {it.run()}).build()
        val context=VaultContext(base,access).apply {database=db}
        try {
            val name=AppearancePreferences.FILE
            db.privatePreferenceDao().save(PrivatePreferenceEntity(name,EncryptedPreferences.encode(mapOf("text_size" to "Large"))))
            withContext(Dispatchers.IO) {context.preparePreferences()}
            val storage=context.getSharedPreferences(name,0)
            val prefs=withContext(Dispatchers.IO) {AppearancePreferences(storage,access)}
            queries.clear()
            repeat(5) {
                assertEquals("Large",context.getSharedPreferences(name,0).getString("text_size",null))
                assertEquals(AppearanceTextSize.Large,prefs.settings.textSize)
                context.getSharedPreferences(ScreenCapturePreference.NAME,0).all
                context.getSharedPreferences(com.r0ybt.arachn0de.calendar.CalendarSpecialPreferences.NAME,0).all
                context.getSharedPreferences(com.r0ybt.arachn0de.ui.CalendarPresentation.NAME,0).all
            }
            assertTrue(synchronized(queries) {queries.none {it.contains("private_preferences") && it.startsWith("SELECT",true)}})
            withContext(Dispatchers.IO+NonCancellable) {prefs.setTextSize(AppearanceTextSize.Medium)}
            assertEquals("Medium",EncryptedPreferences.decode(db.privatePreferenceDao().get(name)!!.payload)["text_size"])
            db.privatePreferenceDao().save(PrivatePreferenceEntity(name,EncryptedPreferences.encode(mapOf("text_size" to "Small"))))
            withContext(Dispatchers.IO) {context.reloadPreferences();prefs.reload()}
            assertSame(storage,context.getSharedPreferences(name,0));assertEquals(AppearanceTextSize.Small,prefs.settings.textSize)
            access.beginClosing();context.clearMemory()
            assertEquals(AppearanceSettings(),prefs.settings)
            assertTrue(runCatching {storage.all}.isFailure)
        } finally {context.clearMemory();db.close();access.close();access.root.deleteRecursively()}
    }
    @Test fun warmNamespaceRemainsReadableDuringSlowReload()=runBlocking<Unit> {
        val base=ApplicationProvider.getApplicationContext<Context>();val access=session()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        var pause=false
        val db=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java)
            .setQueryCallback({sql,_->if(pause && sql.startsWith("SELECT name,payload FROM private_preferences")) {
                entered.countDown();check(release.await(10,TimeUnit.SECONDS))
            }},Executor {it.run()}).build()
        val context=VaultContext(base,access).apply {database=db}
        try {
            context.preparePreferences()
            pause=true
            val reload=pool.submit {context.reloadPreferences()}
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            assertNotNull(pool.submit<android.content.SharedPreferences> {context.getSharedPreferences(ScreenCapturePreference.NAME,0)}.get(1,TimeUnit.SECONDS))
            pause=false;release.countDown();reload.get(5,TimeUnit.SECONDS)
        } finally {pause=false;release.countDown();pool.shutdownNow();context.clearMemory();db.close();access.close();access.root.deleteRecursively()}
    }
    @Test fun slowDurableCommitDoesNotBlockReadsOrExposeUnconfirmedValues() {
        val access=session();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        var stored=mapOf<String,Any>("key" to "old")
        val prefs=EncryptedPreferences(access,{stored},{next->entered.countDown();check(release.await(10,TimeUnit.SECONDS));stored=next})
        try {
            val write=pool.submit<Boolean> {prefs.edit().putString("key","new").commit()}
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(write.isDone)
            assertEquals("old",pool.submit<String?> {prefs.getString("key",null)}.get(1,TimeUnit.SECONDS))
            release.countDown();assertTrue(write.get(5,TimeUnit.SECONDS));assertEquals("new",stored["key"])
            assertEquals("new",prefs.getString("key",null))
            val failure=EncryptedPreferences(access,{stored},{throw java.io.IOException("injected")})
            assertFalse(failure.edit().putString("key","lost").commit());assertEquals("new",failure.getString("key",null))
        } finally {release.countDown();pool.shutdownNow();access.close();access.root.deleteRecursively()}
    }
    @Test fun revokedSessionRejectsLateCommitPublicationAndThemeReload() {
        val access=session();val entered=CountDownLatch(1);val release=CountDownLatch(1);val pool=Executors.newSingleThreadExecutor()
        val theme=AppearanceTheme.NightBlue
        val prefs=EncryptedPreferences(access,{mapOf("theme" to theme.name)},{entered.countDown();check(release.await(10,TimeUnit.SECONDS))})
        val appearance=AppearancePreferences(prefs,access)
        assertEquals(theme,appearance.settings.theme)
        try {
            val write=pool.submit<Boolean> {prefs.edit().putString("theme","NightBlue").commit()}
            assertTrue(entered.await(5,TimeUnit.SECONDS));access.beginClosing();prefs.clearMemory()
            release.countDown();assertFalse(write.get(5,TimeUnit.SECONDS))
            appearance.reload();assertEquals(AppearanceSettings(),appearance.settings)
            assertTrue(runCatching {prefs.all}.isFailure)
        } finally {release.countDown();pool.shutdownNow();access.close();access.root.deleteRecursively()}
    }
}
