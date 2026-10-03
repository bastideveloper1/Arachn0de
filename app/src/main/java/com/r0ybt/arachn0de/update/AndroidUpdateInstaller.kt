package com.r0ybt.arachn0de.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateFileProvider : FileProvider()

/** Builds only user-visible Android requests; caller revalidates locally before using this boundary. */
internal class AndroidUpdateInstaller(private val context: Context) {
    fun needsSourcePermission(): Boolean = Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()
    fun sourceSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT < 26) throw IllegalStateException("Source settings require Android 8+")
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    }
    @Suppress("DEPRECATION")
    fun installIntent(update: VerifiedUpdate): Intent {
        val file = update.file
        require(file.canonicalFile.parentFile == File(context.cacheDir, "updates/verified").canonicalFile)
        require(file.isFile && file.length() == update.release.apk.size)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Actualización de Arachn0de", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        // Select only a platform/system installer; never a third-party handler.
        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY)
        val handler = handlers.firstOrNull() ?: throw IllegalStateException("System installer unavailable")
        intent.component = ComponentName(handler.activityInfo.packageName, handler.activityInfo.name)
        return intent
    }
}
