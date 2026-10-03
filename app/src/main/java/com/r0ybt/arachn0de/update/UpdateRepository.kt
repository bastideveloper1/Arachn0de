package com.r0ybt.arachn0de.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal fun interface ReleaseSource { suspend fun releases(): List<GitHubRelease> }

internal class GitHubReleaseSource : ReleaseSource {
    override suspend fun releases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GitHubRelease>()
        // Construct pages ourselves: never follow externally supplied pagination URLs or redirects.
        for (page in 1..10) {
            coroutineContext.ensureActive()
            val connection = URL("https://api.github.com/repos/${ReleaseParser.REPOSITORY}/releases?per_page=100&page=$page")
                .openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Arachn0de-update-check")
                if (connection.responseCode != 200) throw IOException("GitHub unavailable")
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (bytes.size() + count > 2 * 1024 * 1024) throw IOException("Release response too large")
                        bytes.write(buffer, 0, count)
                    }
                }
                val parsed = ReleaseParser.parse(bytes.toString("UTF-8"))
                result += parsed.releases
                if (parsed.count < 100) return@withContext result.toList()
            } finally { connection.disconnect() }
        }
        throw IOException("Release listing exceeds check limit")
    }
}

internal class UpdateRepository(private val source: ReleaseSource = GitHubReleaseSource()) {
    suspend fun newerRelease(installedName: String): GitHubRelease? {
        val installed = requireNotNull(SemanticVersion.parse(installedName)) { "Invalid installed version" }
        return source.releases().filter { it.version > installed }
            .maxWithOrNull(compareBy<GitHubRelease> { it.version }.thenBy { !it.prerelease }.thenBy { it.tag })
    }
}
