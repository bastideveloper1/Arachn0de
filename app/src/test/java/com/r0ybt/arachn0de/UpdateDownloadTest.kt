package com.r0ybt.arachn0de

import android.content.Context
import android.content.Intent
import android.content.pm.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.update.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class UpdateDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = "synthetic APK for deterministic transport tests".toByteArray()
    private val hex get() = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun release(checksumText: String = "$hex  Arachn0de-v0.3.0.apk\n", checksum: Boolean = true): GitHubRelease {
        fun asset(name: String, size: Long) = ReleaseAsset(name, "https://github.com/bastideveloper1/Arachn0de/releases/download/v0.3.0/$name", size)
        val apk = asset("Arachn0de-v0.3.0.apk", bytes.size.toLong())
        val sums = if (checksum) asset("SHA256SUMS.txt", checksumText.toByteArray().size.toLong()) else null
        return GitHubRelease("v0.3.0", SemanticVersion.parse("0.3.0")!!, "Beta", "Notes", true, listOfNotNull(apk, sums), apk, sums)
    }
    private fun store(root: File, text: String = "$hex  Arachn0de-v0.3.0.apk\n", apk: ByteArray = bytes,
        validator: ApkValidator = ApkValidator { _, _ -> }) = UpdateDownloadStore(root, AssetFetcher { asset, output ->
        output.write(if (asset.name == "SHA256SUMS.txt") text.toByteArray() else apk)
    }, validator)
    private suspend fun rejected(root: File, block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(File(root, "verified").listFiles().orEmpty().isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
    @Test fun checksumAcceptsTextBinaryUppercaseAndMultipleEntries() {
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
        assertArrayEquals(expected, UpdateChecksum.expected("${hex.uppercase()} *Arachn0de-v0.3.0.apk\r\n", release().apk.name))
        assertArrayEquals(expected, UpdateChecksum.expected("$hex  other.txt\n$hex  ${release().apk.name}\n", release().apk.name))
    }
    @Test fun checksumRejectsMissingDuplicateMalformedAndUnsafeEntries() {
        for (text in listOf("", "$hex  other.apk", "invalid  ${release().apk.name}", "$hex  ${release().apk.name}\n$hex  ${release().apk.name}", "$hex  ../bad.apk", "$hex  ${release().apk.name}\nbad line")) {
            assertThrows(IllegalArgumentException::class.java) { UpdateChecksum.expected(text, release().apk.name) }
        }
    }
    @Test fun correctHashPublishesOnlyAfterIdentityAndRecoversLocally() = runBlocking {
        val root = temp.newFolder()
        var checked = false; var verifying = false
        val service = store(root, validator = ApkValidator { file, _ ->
            assertTrue(file.name.endsWith(".part")); assertFalse(File(root, "verified").listFiles().orEmpty().isNotEmpty())
            checked = true
        })
        val update = service.download(release()) { verifying = true }
        assertTrue(checked && verifying); assertArrayEquals(bytes, update.file.readBytes())
        assertTrue(File(root, "current.json").isFile)
        val recovered = store(root).recover()!!
        assertEquals(update.file, recovered.file); assertEquals(update.release.tag, recovered.release.tag)
    }
    @Test fun wrongHashMissingHashAndMissingEntryNeverPublish() = runBlocking {
        for (text in listOf("0".repeat(64)+"  ${release().apk.name}\n", "$hex  other.apk\n", "malformed")) {
            val root = temp.newFolder()
            rejected(root) { store(root, text).download(release(text)) {} }
        }
        val root = temp.newFolder()
        rejected(root) { store(root).download(release(checksum = false)) {} }
    }
    @Test fun emptyPartialOversizedAndFailedDownloadsAreDeleted() = runBlocking {
        for (apk in listOf(byteArrayOf(), bytes.copyOf(3), bytes + 1)) {
            val root = temp.newFolder()
            rejected(root) { store(root, apk = apk).download(release()) {} }
        }
        val root = temp.newFolder()
        val service = UpdateDownloadStore(root, AssetFetcher { _, out -> out.write(1); throw IOException("offline") }, ApkValidator { _, _ -> })
        rejected(root) { service.download(release()) {} }
    }
    @Test fun cancellationDuringDownloadLeavesNoPartial() = runBlocking {
        val root = temp.newFolder()
        val service = UpdateDownloadStore(root, AssetFetcher { _, _ -> throw CancellationException() }, ApkValidator { _, _ -> })
        rejected(root) { service.download(release()) {} }
    }
    @Test fun identityFailureAndLaterTamperingInvalidateApk() = runBlocking {
        val root = temp.newFolder()
        rejected(root) { store(root, validator = ApkValidator { _, _ -> throw IllegalArgumentException() }).download(release()) {} }
        val service = store(root)
        val update = service.download(release()) {}
        update.file.writeBytes(bytes.reversedArray())
        try { service.revalidate(update); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(update.file.exists()); assertFalse(File(root, "current.json").exists())
    }
    @Test fun cleanupRemovesStaleAndPartialFilesOnlyInUpdateDirectory() = runBlocking {
        val root = temp.newFolder()
        val outside = File(root.parentFile, "user-data.txt").apply { writeText("keep") }
        val old = File(File(root, "verified").apply { mkdirs() }, "old.apk").apply { writeText("old"); setLastModified(1) }
        val part = File(root, "failed.part").apply { writeText("partial") }
        val service = store(root)
        assertNull(service.recover()); assertFalse(old.exists()); assertFalse(part.exists()); assertTrue(outside.exists())
        val update = service.download(release()) {}
        update.file.setLastModified(1)
        assertNull(service.recover()); assertFalse(update.file.exists())
    }
    @Test fun identityPolicyRequiresPackageVersionNonDebugAndExactSignerSet() {
        val installed = ApkIdentity("com.r0ybt.arachn0de", "0.2.0", 2, setOf("certificate"), false, 24)
        val good = installed.copy(versionName = "0.3.0", versionCode = 3)
        assertTrue(ApkIdentityPolicy.accepts(installed, good, release(), 28))
        for (bad in listOf(good.copy(packageName = "other"), good.copy(versionCode = 2), good.copy(versionName = "0.4.0"),
            good.copy(signers = emptySet()), good.copy(signers = setOf("other")), good.copy(signers = setOf("certificate", "extra")), good.copy(debuggable = true), good.copy(minSdk = 29))) {
            assertFalse(ApkIdentityPolicy.accepts(installed, bad, release(), 28))
        }
    }
    @Test fun redirectPolicyRejectsHttpForeignHostsPortsAndCredentials() {
        val original = URI(release().apk.downloadUrl)
        assertTrue(OfficialAssetUrl.allowedRedirect(URI("https://release-assets.githubusercontent.com/path?sig=temporary"), original))
        for (url in listOf("http://release-assets.githubusercontent.com/file", "https://evil.example/file", "https://github.com/other/repo/file",
            "https://release-assets.githubusercontent.com.evil.example/file", "https://user@release-assets.githubusercontent.com/file", "https://release-assets.githubusercontent.com:444/file")) {
            assertFalse(OfficialAssetUrl.allowedRedirect(URI(url), original))
        }
    }
    @Test fun stateRequiresVerificationBeforeInstallAndAllowsRetry() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val root = temp.newFolder()
        val ready = VerifiedUpdate(release(), File(root, "test.apk"), byteArrayOf())
        val canFinish = CompletableDeferred<Unit>()
        var fail = true; var installs = 0; var requests = 0
        val downloads = object : UpdateDownloads {
            override suspend fun download(release: GitHubRelease, verifying: suspend () -> Unit): VerifiedUpdate {
                requests++
                if (fail) throw IOException()
                verifying(); canFinish.await(); return ready
            }
            override suspend fun revalidate(update: VerifiedUpdate) { }
            override suspend fun recover(): VerifiedUpdate? = null
        }
        val actions = UpdateActions(UpdateRepository(ReleaseSource { listOf(release()) }), scope, "0.2.0", downloads)
        actions.check(); actions.prepareInstall { installs++ }; assertEquals(0, installs)
        actions.download(); assertEquals(UpdateState.Error, actions.state)
        fail = false; actions.download(); assertTrue(actions.state is UpdateState.Verifying)
        actions.download(); actions.prepareInstall { installs++ }; assertEquals(0, installs); assertEquals(2, requests)
        canFinish.complete(Unit); assertTrue(actions.state is UpdateState.Ready)
        actions.prepareInstall { installs++ }; assertEquals(1, installs); assertTrue(actions.awaitingAndroid)
        actions.prepareInstall { installs++ }; assertEquals(1, installs)
        actions.androidReturned(cancelled = true); assertFalse(actions.awaitingAndroid); assertTrue(actions.state is UpdateState.Ready)
        scope.cancel()
    }
    @Suppress("DEPRECATION")
    @Test fun androidValidatorReadsPublicSignatureApisAndRejectsDifferentSigner() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pm = shadowOf(context.packageManager)
        fun info(version: String, code: Int, signer: String): PackageInfo = PackageInfo().apply {
            packageName = context.packageName; versionName = version; versionCode = code
            applicationInfo = ApplicationInfo().apply { packageName = context.packageName; minSdkVersion = 24 }
            val certificates = arrayOf(Signature(signer.toByteArray()))
            signatures = certificates
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                signingInfo = SigningInfo().also { shadowOf(it).setSignatures(certificates) }
            }
        }
        pm.installPackage(info("0.2.0", 2, "same public certificate"))
        val file = temp.newFile("candidate.part")
        pm.setPackageArchiveInfo(file.absolutePath, info("0.3.0", 3, "same public certificate"))
        val validator = AndroidApkValidator(context)
        validator.validate(file, release())
        pm.setPackageArchiveInfo(file.absolutePath, info("0.3.0", 3, "other public certificate"))
        assertThrows(IllegalArgumentException::class.java) { validator.validate(file, release()) }
        assertThrows(IllegalArgumentException::class.java) { validator.validate(temp.newFile("unreadable.apk"), release()) }
    }
    @Suppress("DEPRECATION")
    @Test fun installerUsesOnlyVerifiedContentUriAndReadGrantToSystemHandler() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(File(context.cacheDir, "updates/verified").apply { mkdirs() }, "fixture.apk").apply { writeBytes(bytes) }
        val update = VerifiedUpdate(release(), file, byteArrayOf())
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val probe = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
        val handler = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                name = "SystemInstaller"; packageName = "android"
                applicationInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM; packageName = "android" }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(probe, handler)
        val installer = AndroidUpdateInstaller(context)
        val intent = installer.installIntent(update)
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action); assertEquals(uri, intent.data)
        assertEquals("content", intent.data!!.scheme); assertEquals("android", intent.component!!.packageName)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))
        context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(bytes, it.readBytes()) }
        for (name in listOf("updates/current.json", "updates/staging.part", "obligation-reports/report.png")) {
            val outside = File(context.cacheDir, name).apply { parentFile!!.mkdirs(); writeText("private") }
            assertThrows(IllegalArgumentException::class.java) { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", outside) }
        }
        file.delete()
    }
    @Test fun unknownSourcePermissionUsesOfficialSettingsAndRequiresAnotherUserAction() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pm = shadowOf(context.packageManager)
        val installer = AndroidUpdateInstaller(context)
        pm.setCanRequestPackageInstalls(false)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertTrue(installer.needsSourcePermission())
            val intent = installer.sourceSettingsIntent()
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
            assertEquals("package:${context.packageName}", intent.data.toString())
        } else assertFalse(installer.needsSourcePermission())
        pm.setCanRequestPackageInstalls(true)
        assertFalse(installer.needsSourcePermission())
    }

}
