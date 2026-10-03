package com.r0ybt.arachn0de.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal data class ReleaseAsset(val name: String, val downloadUrl: String, val size: Long)
internal data class GitHubRelease(
    val tag: String, val version: SemanticVersion, val title: String,
    val notes: String, val prerelease: Boolean, val assets: List<ReleaseAsset>,
    val apk: ReleaseAsset, val checksum: ReleaseAsset?,
)
internal data class ReleasePage(val releases: List<GitHubRelease>, val count: Int)

internal object ReleaseParser {
    const val REPOSITORY = "bastideveloper1/Arachn0de"
    fun parse(json: String): ReleasePage {
        val array = JSONArray(json)
        require(array.length() <= 100) { "Invalid release page" }
        val releases = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            candidate(item)
        }
        return ReleasePage(releases, array.length())
    }

    private fun candidate(item: JSONObject): GitHubRelease? {
        if (item.opt("draft") != false || item.opt("prerelease") !is Boolean) return null
        val tag = item.opt("tag_name") as? String ?: return null
        if (!tag.startsWith("v")) return null
        val version = SemanticVersion.parse(tag) ?: return null
        val rawAssets = item.optJSONArray("assets") ?: return null
        if (rawAssets.length() > 100) return null
        val assets = (0 until rawAssets.length()).mapNotNull { index ->
            val asset = rawAssets.optJSONObject(index) ?: return@mapNotNull null
            val name = asset.opt("name") as? String ?: return@mapNotNull null
            val url = asset.opt("browser_download_url") as? String ?: return@mapNotNull null
            val size = asset.optLong("size", -1)
            if (name.length > 250 || '/' in name || size <= 0 || asset.opt("state") != "uploaded") return@mapNotNull null
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host != "github.com" || uri.port != -1 || uri.userInfo != null ||
                uri.query != null || uri.fragment != null || uri.path != "/$REPOSITORY/releases/download/$tag/$name") return@mapNotNull null
            ReleaseAsset(name, url, size)
        }
        val apk = assets.singleOrNull { it.name == "Arachn0de-$tag.apk" } ?: return null
        val title = (item.opt("name") as? String)?.takeIf { it.isNotBlank() } ?: tag
        val notes = item.opt("body") as? String ?: ""
        if (title.length > 500 || notes.length > 200_000) return null
        return GitHubRelease(tag, version, title, notes, item.getBoolean("prerelease"), assets.toList(), apk,
            assets.singleOrNull { it.name == "SHA256SUMS.txt" })
    }
}
