package com.r0ybt.arachn0de.sharing

import com.r0ybt.arachn0de.security.*

import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import java.io.File
import java.util.UUID

/** UI/module regression fixture only. Never packaged into the APK or used as crypto evidence.
 * Uses the historical plain Room/file fixture while explicitly supplying an authenticated session.
 * Security-specific tests use real encrypted file roots and separate locked applications.
 */
class SharingTestApplication:Arachn0deApplication() {
    override fun onCreate() {
        super.onCreate()
        val id=UUID.randomUUID()
        val access=SecureFiles.Session(id,File(filesDir,"security-v1/stores/$id").apply {mkdirs()},VaultCrypto.randomKey())
        SecureFiles.register(access)
        val context=object:VaultContext(this,access) {
            override fun getFilesDir():File=this@SharingTestApplication.filesDir
            override fun getCacheDir():File=this@SharingTestApplication.cacheDir
        }
        val db=Arachn0deDatabase.create(this);context.database=db
        SecureFiles.activeProfile=id
        security.current.value=VaultSession(access,context,db,true)
    }
    // Each UI test locks asynchronously while pumping the main looper. Blocking
    // on lock() here deadlocks its Dispatchers.Main window protection in Robolectric.
}
