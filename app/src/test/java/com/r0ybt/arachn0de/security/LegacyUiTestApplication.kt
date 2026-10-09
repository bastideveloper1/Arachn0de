package com.r0ybt.arachn0de.security

import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import java.io.File
import java.util.UUID

/** UI/module regression fixture only. Never packaged into the APK or used as crypto evidence.
 * Uses the historical plain Room/file fixture while explicitly supplying an authenticated session.
 * Security-specific tests use real encrypted file roots and separate locked applications.
 */
class LegacyUiTestApplication:Arachn0deApplication() {
    override fun onCreate() {
        super.onCreate()
        val id=UUID.randomUUID()
        val access=SecureFiles.Session(id,File(filesDir,"security-v1/stores/$id").apply {mkdirs()},VaultCrypto.randomKey())
        SecureFiles.register(access)
        val context=object:VaultContext(this,access) {
            override fun getFilesDir():File=this@LegacyUiTestApplication.filesDir
            override fun getCacheDir():File=this@LegacyUiTestApplication.cacheDir
        }
        val db=Arachn0deDatabase.create(this);context.database=db
        SecureFiles.activeProfile=id
        security.current.value=VaultSession(access,context,db,true)
    }
    override fun onTerminate() {
        kotlinx.coroutines.runBlocking { security.lock() }
        super.onTerminate()
    }
}
