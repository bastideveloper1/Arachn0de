package com.r0ybt.arachn0de

import com.r0ybt.arachn0de.update.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateTest {
    private fun item(tag: String, draft: Boolean = false, beta: Boolean = true, apk: Boolean = true, host: String = "github.com"): JSONObject {
        val assets = JSONArray()
        for (name in listOfNotNull(if (apk) "Arachn0de-$tag.apk" else null, "SHA256SUMS.txt")) {
            assets.put(JSONObject().put("name", name).put("size", 123).put("state", "uploaded")
                .put("browser_download_url", "https://$host/bastideveloper1/Arachn0de/releases/download/$tag/$name"))
        }
        return JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", beta)
            .put("name", "Arachn0de $tag Beta").put("body", "Cambios\n- Nota").put("assets", assets)
    }
    private fun parse(vararg items: JSONObject) = ReleaseParser.parse(JSONArray(items.toList()).toString()).releases
    private fun repo(vararg items: JSONObject) = UpdateRepository(ReleaseSource { parse(*items) })

    @Test fun sameVersionIsUpToDate() = runBlocking { assertNull(repo(item("v0.2.0")).newerRelease("0.2.0")) }
    @Test fun lowerVersionIsUpToDate() = runBlocking { assertNull(repo(item("v0.1.9")).newerRelease("0.2.0")) }
    @Test fun higherBetaIsAvailable() = runBlocking {
        val release = repo(item("v0.3.0")).newerRelease("0.2.0")!!
        assertTrue(release.prerelease); assertEquals("v0.3.0", release.tag)
        assertEquals("Cambios\n- Nota", release.notes)
    }
    @Test fun numericComponentsAndSemverPrecedence() {
        fun version(v: String) = SemanticVersion.parse(v)!!
        assertTrue(version("0.2.10") > version("0.2.9"))
        assertTrue(version("1.0.0-beta.10") > version("1.0.0-beta.9"))
        assertTrue(version("1.0.0") > version("1.0.0-rc.1"))
        assertEquals(0, version("1.0.0+build.1").compareTo(version("1.0.0+build.2")))
        for (v in listOf("latest", "1.2", "01.2.3", "1.2.3-beta.01", "-1.2.3")) assertNull(SemanticVersion.parse(v))
    }
    @Test fun draftsAndInvalidTagsIgnored() = runBlocking {
        assertNull(repo(item("v9.0.0", draft = true), item("garbage")).newerRelease("0.2.0"))
    }
    @Test fun emptyListIsUpToDate() = runBlocking { assertNull(repo().newerRelease("0.2.0")) }
    @Test fun highestVersionSelectedRegardlessOfOrder() = runBlocking {
        assertEquals("v0.2.10", repo(item("v0.2.9"), item("v0.2.10"), item("v0.2.8")).newerRelease("0.2.0")!!.tag)
    }
    @Test fun officialApkAndChecksumIdentified() {
        val release = parse(item("v0.3.0")).single()
        assertEquals("Arachn0de-v0.3.0.apk", release.apk.name)
        assertEquals("SHA256SUMS.txt", release.checksum!!.name)
        assertTrue(release.apk.downloadUrl.startsWith("https://github.com/bastideveloper1/Arachn0de/"))
    }
    @Test fun missingApkAndUntrustedUrlsIgnored() {
        assertTrue(parse(item("v0.3.0", apk = false), item("v0.4.0", host = "evil.example")).isEmpty())
        val duplicate = item("v0.5.0")
        duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(0))
        assertTrue(parse(duplicate).isEmpty())
    }
    @Test fun missingFieldsAndNonObjectEntriesDoNotCrash() {
        assertTrue(parse(JSONObject(), item("v0.3.0").put("draft", JSONObject.NULL)).isEmpty())
        assertTrue(ReleaseParser.parse("[null,3,\"bad\"]").releases.isEmpty())
    }
    @Test fun malformedResponseAndNetworkErrorBecomeRetryableError() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        var calls = 0
        val actions = UpdateActions(UpdateRepository(ReleaseSource {
            calls++
            if (calls == 1) throw java.io.IOException("offline")
            if (calls == 2) ReleaseParser.parse("invalid")
            emptyList()
        }), scope, "0.2.0")
        assertEquals(UpdateState.Idle, actions.state)
        actions.check(); assertEquals(UpdateState.Error, actions.state)
        actions.check(); assertEquals(UpdateState.Error, actions.state)
        actions.check(); assertEquals(UpdateState.UpToDate, actions.state)
        scope.cancel()
    }
    @Test fun checkingBlocksDuplicatesAndCancellationDoesNotReportError() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val ready = CompletableDeferred<List<GitHubRelease>>()
        var calls = 0
        val actions = UpdateActions(UpdateRepository(ReleaseSource { calls++; ready.await() }), scope, "0.2.0")
        actions.check(); actions.check()
        assertEquals(1, calls); assertEquals(UpdateState.Checking, actions.state)
        scope.cancel(); assertEquals(UpdateState.Idle, actions.state)
    }
    @Test fun availableStateRetainsReleaseForFutureDownload() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val actions = UpdateActions(repo(item("v0.3.0")), scope, "0.2.0")
        actions.check()
        assertEquals("v0.3.0", (actions.state as UpdateState.Available).release.tag)
        scope.cancel()
    }
}
