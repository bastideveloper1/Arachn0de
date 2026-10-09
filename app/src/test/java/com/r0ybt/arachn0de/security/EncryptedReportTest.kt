package com.r0ybt.arachn0de.security

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.report.ObligationReportFiles
import com.r0ybt.arachn0de.report.ReportFileProvider
import com.r0ybt.arachn0de.report.ReportFixture
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class)
class EncryptedReportTest {
    @Test fun reportCacheIsEncryptedAndProviderCannotReuseUriAfterSessionChange()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val id=UUID.randomUUID();val root=File(app.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val context=VaultContext(app,access);SecureFiles.activeProfile=id
        try {
            val files=ObligationReportFiles(context);val report=files.generate(ReportFixture.data())
            val plain=SecureFiles.read(report)
            assertTrue(plain.copyOf(8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
            assertFalse(report.readBytes().contentEquals(plain))
            val intent=files.shareIntent(report)
            val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(listOf("v1",id.toString(),report.name),uri.pathSegments)
            val provider=ReportFileProvider()
            provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="${app.packageName}.reports";exported=false;grantUriPermissions=true})
            assertEquals("image/png",provider.getType(uri))
            provider.query(uri,null,null,null,null).use {assertTrue(it.moveToFirst());assertEquals(plain.size.toLong(),it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE)))}
            SecureFiles.activeProfile=UUID.randomUUID()
            assertTrue(runCatching {provider.getType(uri)}.isFailure)
            assertTrue(runCatching {files.shareIntent(report)}.isFailure)
            SecureFiles.revoke(access)
            assertTrue(runCatching {SecureFiles.read(report)}.isFailure)
        } finally {SecureFiles.activeProfile=null;SecureFiles.revoke(access);root.deleteRecursively()}
    }
}
