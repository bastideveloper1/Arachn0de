package com.r0ybt.arachn0de.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class VerifiedUpdate(val release: GitHubRelease, val file: File, val hash: ByteArray)
internal interface UpdateDownloads {
    suspend fun download(release: GitHubRelease, verifying: suspend () -> Unit): VerifiedUpdate
    suspend fun revalidate(update: VerifiedUpdate)
    suspend fun recover(): VerifiedUpdate?
}

/** Only cache/updates; never sees user data. Shared lock also serializes Activity recreation. */
internal class UpdateDownloadStore(private val root: File, private val fetcher: AssetFetcher, private val validator: ApkValidator,
    private val now: () -> Long = System::currentTimeMillis) : UpdateDownloads {
    companion object {
        const val MAX_APK_BYTES = 256L * 1024 * 1024
        const val RETENTION = 24L * 60 * 60 * 1000
        private val lock = Mutex()
    }
    private val verifiedDir get() = File(root, "verified")
    private val receipt get() = File(root, "current.json")

    override suspend fun download(release: GitHubRelease, verifying: suspend () -> Unit): VerifiedUpdate = withContext(Dispatchers.IO) {
        lock.withLock {
            cleanup()
            validateAssets(release)
            if (!verifiedDir.isDirectory && !verifiedDir.mkdirs()) throw IOException("Update cache unavailable")
            val file = File(verifiedDir, "${UUID.randomUUID()}.apk")
            val part = File(root, "${file.name}.part")
            var published = false
            try {
                val checksum = requireNotNull(release.checksum) { "Missing SHA256SUMS.txt" }
                val text = ByteArrayOutputStream()
                exactDownload(checksum, text)
                val expected = try { UpdateChecksum.expected(text.toString("UTF-8"), release.apk.name) }
                    catch (_: IllegalArgumentException) { throw UpdateRejected("SHA256SUMS.txt no es válido o no contiene una entrada única para este APK.") }
                part.outputStream().use { exactDownload(release.apk, it) }
                verifying()
                val actual = hash(part)
                if (!UpdateChecksum.matches(expected, actual)) throw UpdateRejected("El SHA-256 del APK no coincide. La descarga se ha descartado; no se permite instalarla.")
                validator.validate(part, release)
                currentCoroutineContext().ensureActive()
                if (!part.renameTo(file)) throw IOException("Cannot publish verified APK")
                published = true
                val update = VerifiedUpdate(release, file, expected)
                writeReceipt(update)
                currentCoroutineContext().ensureActive()
                update
            } catch (failure: Throwable) {
                part.delete(); file.delete()
                if (published) receipt.delete()
                throw failure
            }
        }
    }
    override suspend fun revalidate(update: VerifiedUpdate) = withContext(Dispatchers.IO) {
        lock.withLock {
            try { validateLocal(update) } catch (failure: Throwable) {
                // Cancellation is not evidence that a previously verified APK is unsafe.
                if (failure !is kotlinx.coroutines.CancellationException) { discard(update.file); receipt.delete() }
                throw failure
            }
        }
    }
    override suspend fun recover(): VerifiedUpdate? = withContext(Dispatchers.IO) {
        lock.withLock {
            cleanup()
            if (!receipt.isFile) return@withLock null
            var file: File? = null
            try {
                require(receipt.length() in 1..(2 * 1024 * 1024))
                val json = JSONObject(receipt.readText())
                val name = json.getString("file")
                require(name.matches(Regex("[a-f0-9-]{36}\\.apk")))
                file = File(verifiedDir, name)
                val release = ReleaseParser.parse(JSONArray().put(json.getJSONObject("release")).toString()).releases.single()
                val hashText = json.getString("sha256")
                val expected = UpdateChecksum.expected("$hashText  ${release.apk.name}", release.apk.name)
                val update = VerifiedUpdate(release, file, expected)
                validateLocal(update)
                update
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { file?.let(::discard); receipt.delete(); null }
        }
    }
    private fun validateAssets(release: GitHubRelease) {
        require(release.tag.startsWith('v') && SemanticVersion.parse(release.tag)?.compareTo(release.version) == 0)
        require(release.apk.name == "Arachn0de-${release.tag}.apk" && release.apk.size in 1..MAX_APK_BYTES)
        val checksum = release.checksum ?: throw UpdateRejected("Esta Release no incluye SHA256SUMS.txt. Busca actualizaciones de nuevo cuando esté disponible.")
        require(checksum.name == "SHA256SUMS.txt" && checksum.size in 1..(64 * 1024))
        require(release.assets.count { it.name == release.apk.name } == 1 && release.apk in release.assets)
        require(release.assets.count { it.name == checksum.name } == 1 && checksum in release.assets)
        require(OfficialAssetUrl.valid(release.apk, release.tag) && OfficialAssetUrl.valid(checksum, release.tag))
    }
    private suspend fun exactDownload(asset: ReleaseAsset, output: OutputStream) {
        var count = 0L
        val limited = object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (count + length > asset.size) throw IOException("Asset exceeds declared size")
                output.write(bytes, offset, length); count += length
            }
        }
        fetcher.fetch(asset, limited)
        currentCoroutineContext().ensureActive()
        if (count != asset.size || count == 0L) throw IOException("Incomplete asset")
        output.flush()
    }
    private suspend fun hash(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
    private suspend fun validateLocal(update: VerifiedUpdate) {
        validateAssets(update.release)
        require(update.file.canonicalFile.parentFile == verifiedDir.canonicalFile)
        require(update.file.isFile && update.file.length() == update.release.apk.size)
        require(now() - update.file.lastModified() in 0..RETENTION) { "Update expired" }
        require(UpdateChecksum.matches(update.hash, hash(update.file))) { "Cached APK changed" }
        validator.validate(update.file, update.release)
    }
    private fun discard(file: File) {
        if (file.absoluteFile.parentFile == verifiedDir.absoluteFile) file.delete()
    }
    private fun cleanup() {
        root.mkdirs()
        root.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach { it.delete() }
        verifiedDir.listFiles()?.filter { it.isFile && now() - it.lastModified() !in 0..RETENTION }?.forEach { it.delete() }
        if (receipt.exists() && now() - receipt.lastModified() !in 0..RETENTION) receipt.delete()
    }
    private fun writeReceipt(update: VerifiedUpdate) {
        val release = update.release
        val assets = JSONArray()
        release.assets.forEach { assets.put(JSONObject().put("name", it.name).put("size", it.size).put("state", "uploaded").put("browser_download_url", it.downloadUrl)) }
        val item = JSONObject().put("tag_name", release.tag).put("draft", false).put("prerelease", release.prerelease)
            .put("name", release.title).put("body", release.notes).put("assets", assets)
        val value = JSONObject().put("file", update.file.name).put("release", item)
            .put("sha256", update.hash.joinToString("") { "%02x".format(it.toInt() and 255) })
        val part = File(root, "receipt.part")
        try {
            part.writeText(value.toString())
            if (!part.renameTo(receipt)) throw IOException("Cannot publish update receipt")
        } finally { part.delete() }
    }
}
