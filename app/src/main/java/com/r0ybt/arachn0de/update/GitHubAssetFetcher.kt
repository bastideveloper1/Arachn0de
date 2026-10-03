package com.r0ybt.arachn0de.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal object OfficialAssetUrl {
    fun valid(asset: ReleaseAsset, tag: String): Boolean {
        val uri = runCatching { URI(asset.downloadUrl) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path == "/${ReleaseParser.REPOSITORY}/releases/download/$tag/${asset.name}"
    }
    fun allowedRedirect(uri: URI, original: URI): Boolean = uri.scheme == "https" && uri.port == -1 &&
        uri.userInfo == null && uri.fragment == null && when (uri.host) {
            "github.com" -> uri == original
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
            else -> false
        }
}
internal fun interface AssetFetcher { suspend fun fetch(asset: ReleaseAsset, output: OutputStream) }
internal class GitHubAssetFetcher : AssetFetcher {
    override suspend fun fetch(asset: ReleaseAsset, output: OutputStream) {
        val original = URI(asset.downloadUrl)
        var next = original
        for (redirect in 0..5) {
            currentCoroutineContext().ensureActive()
            require(OfficialAssetUrl.allowedRedirect(next, original)) { "Untrusted redirect" }
            val connection = next.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "Arachn0de-update-download")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    next = next.resolve(connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
                    continue
                }
                if (status != 200) throw IOException("Asset unavailable")
                val length = connection.contentLengthLong
                if (length >= 0 && length != asset.size) throw IOException("Unexpected asset size")
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
                return
            } finally { connection.disconnect() }
        }
        throw IOException("Too many asset redirects")
    }
}
