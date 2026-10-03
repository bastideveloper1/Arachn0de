package com.r0ybt.arachn0de.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal class UpdateRejected(message: String) : IllegalArgumentException(message)

internal object UpdateChecksum {
    fun expected(text: String, filename: String): ByteArray {
        val linePattern = Regex("^([a-fA-F0-9]{64}) [ *]([A-Za-z0-9][A-Za-z0-9._+-]{0,249})$")
        val entries = text.lineSequence().filter { it.isNotBlank() }.map { line ->
            val match = requireNotNull(linePattern.matchEntire(line)) { "Invalid checksum file" }
            match.groupValues[2] to match.groupValues[1]
        }.toList()
        val hash = entries.singleOrNull { it.first == filename }?.second
            ?: throw IllegalArgumentException("Missing or duplicate APK checksum")
        return hash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    fun matches(expected: ByteArray, actual: ByteArray) = expected.size == 32 && actual.size == 32 && MessageDigest.isEqual(expected, actual)
}

internal data class ApkIdentity(val packageName: String, val versionName: String?, val versionCode: Long,
    val signers: Set<String>, val debuggable: Boolean, val minSdk: Int)
internal object ApkIdentityPolicy {
    fun accepts(installed: ApkIdentity, candidate: ApkIdentity, release: GitHubRelease, sdk: Int): Boolean {
        val base = SemanticVersion.parse(installed.versionName.orEmpty()) ?: return false
        return candidate.packageName == installed.packageName && candidate.versionCode > installed.versionCode &&
            candidate.versionName == release.tag.removePrefix("v") &&
            release.version > base &&
            !candidate.debuggable && candidate.minSdk <= sdk && candidate.signers.isNotEmpty() &&
            candidate.signers == installed.signers
    }
}
internal fun interface ApkValidator { fun validate(file: File, release: GitHubRelease) }
internal class AndroidApkValidator(private val context: Context) : ApkValidator {
    @Suppress("DEPRECATION")
    override fun validate(file: File, release: GitHubRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateRejected("El archivo descargado no es un APK válido que Android pueda inspeccionar.")
        if (!ApkIdentityPolicy.accepts(identity(installed), identity(candidate), release, Build.VERSION.SDK_INT))
            throw UpdateRejected("El APK no coincide con el paquete, versión o firma de esta instalación, o no es compatible con este Android.")
    }
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val signers = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        }.toSet()
        val app = requireNotNull(info.applicationInfo)
        return ApkIdentity(info.packageName, info.versionName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), signers,
            app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, app.minSdkVersion)
    }
}
