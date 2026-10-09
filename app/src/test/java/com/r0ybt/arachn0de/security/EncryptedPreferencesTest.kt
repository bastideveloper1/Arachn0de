package com.r0ybt.arachn0de.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.r0ybt.arachn0de.backup.BackupFixture

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class EncryptedPreferencesTest {
    @Test fun typesRestartIsolationAndFailedCommitRetainConfirmedSettings() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val root=File(context.filesDir,"test-vault-${UUID.randomUUID()}").apply { mkdir() }
        val id=UUID.randomUUID();val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,root,key)
        SecureFiles.register(session)
        try {
            val file=File(root,"prefs.enc")
            val prefs=EncryptedPreferences(file,session,BackupFixture::syncDirectory)
            assertTrue(prefs.edit().putString("private","ñ").putBoolean("enabled",true).putInt("int",1).putLong("long",2L).putFloat("float",1.5f).putStringSet("set",mutableSetOf("a","b")).commit())
            val reopened=EncryptedPreferences(file,session,BackupFixture::syncDirectory)
            assertEquals(prefs.all,reopened.all)
            assertFalse(String(file.readBytes(),Charsets.ISO_8859_1).contains("private"))
            val failing=EncryptedPreferences(file,session) { throw java.io.IOException("injected sync") }
            assertFalse(failing.edit().putString("private","changed").commit())
            assertEquals("ñ",failing.getString("private",null))
            assertEquals("ñ",EncryptedPreferences(file,session,BackupFixture::syncDirectory).getString("private",null))
            assertTrue(reopened.edit().remove("set").commit());assertFalse(reopened.contains("set"))
            SecureFiles.revoke(session)
            try { reopened.all;fail("Revoked preference handles must fail closed") } catch(_:IllegalStateException) {}
        } finally { SecureFiles.revoke(session);root.deleteRecursively();key.fill(0) }
    }
}
