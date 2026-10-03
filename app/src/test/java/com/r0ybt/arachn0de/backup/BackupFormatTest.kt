package com.r0ybt.arachn0de.backup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class BackupFormatTest {
    @Test fun fullLogicalRoundTripPreservesEveryFieldAndExactMoney() {
        val data = BackupFixture.complete()
        val archive = BackupFixture.archive(data)
        val restored = BackupJson.decode(BackupContainer.read(ByteArrayInputStream(archive)))
        BackupFixture.assertData(data, restored)
        assertEquals(data.appVersion, restored.appVersion); assertEquals(data.createdAt, restored.createdAt)
        assertEquals(Long.MAX_VALUE, restored.nodes.single { it.id == "bill" }.amountMinor)
    }
    @Test fun emptyBackupRoundTrip() {
        val data = BackupFixture.empty()
        BackupFixture.assertData(data, BackupJson.decode(BackupContainer.read(ByteArrayInputStream(BackupFixture.archive(data)))))
    }
    @Test fun corruptedTruncatedOversizedTrailingAndUnknownEnvelopesAreRejected() {
        val bytes = BackupFixture.archive(BackupFixture.empty())
        val variants = listOf(
            bytes.copyOfRange(0, bytes.size - 1), bytes + byteArrayOf(0),
            bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            bytes.copyOf().apply { this[0] = 0 },
            bytes.copyOf().apply { this[13] = 2 }, // Format Int (magic occupies ten bytes).
            bytes.copyOf().apply { this[17] = 1 }, // Unknown encoding.
            bytes.copyOf().apply { this[18] = 127 }, // Invalid length before allocation.
        )
        variants.forEach { assertTrue(runCatching { BackupContainer.read(ByteArrayInputStream(it)) }.isFailure) }
    }
    private fun altered(change: (JSONObject) -> Unit): ByteArray {
        val root = JSONObject(BackupJson.encode(BackupFixture.complete()).toString(Charsets.UTF_8))
        change(root)
        return root.toString().toByteArray(Charsets.UTF_8)
    }
    @Test fun incompatibleDataAndInvalidTypesFieldsDuplicatesAndUtf8AreRejected() {
        val variants = listOf(
            altered { it.put("dataVersion", 4) },
            altered { it.remove("nodes") }, altered { it.put("unexpected", true) },
            altered { it.getJSONArray("nodes").getJSONObject(0).put("isCompleted", 1) },
            altered { it.getJSONArray("nodes").getJSONObject(0).put("position", 2147483648L) },
            altered { it.getJSONArray("nodes").getJSONObject(2).put("amountMinor", "9223372036854775807") },
            altered { it.getJSONArray("nodes").getJSONObject(0).put("createdAt", 1.5) },
            "{\"dataVersion\":1,\"dataVersion\":1}".toByteArray(),
            byteArrayOf(-61, 40), "[[]]".toByteArray(),
        )
        variants.forEach { assertTrue(runCatching { BackupJson.decode(it) }.isFailure) }
    }
    @Test fun referencesCyclesPurposesCompletionDatesAndFinancesAreValidated() {
        val data = BackupFixture.complete()
        val variants = listOf(
            data.copy(projects = data.projects + data.projects.first()),
            data.copy(nodes = data.nodes + data.nodes.first()),
            data.copy(nodes = data.nodes.map { if (it.id == "task") it.copy(projectId = "missing") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "root") it.copy(parentId = "inner") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "task") it.copy(parentId = "root") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "inner") it.copy(parentId = "missing") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "root") it.copy(isCompleted = true) else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "note") it.copy(isCompleted = true) else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "root") it.copy(purpose = "NOTE") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "task") it.copy(purpose = "OTHER") else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "task") it.copy(startAt = 2, dueAt = 1) else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "bill") it.copy(amountMinor = null) else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "bill") it.copy(amountMinor = 0) else it }),
            data.copy(nodes = data.nodes.map { if (it.id == "bill") it.copy(currencyCode = "XXX") else it }),
            data.copy(assignments = data.assignments + data.assignments.first()),
            data.copy(assignments = data.assignments + com.r0ybt.arachn0de.data.local.NodePersonEntity("missing", "r")),
            data.copy(assignments = data.assignments + com.r0ybt.arachn0de.data.local.NodePersonEntity("bill", "missing")),
        )
        variants.forEach { assertTrue(runCatching { it.validate() }.isFailure) }
    }
    @Test fun missingExtraInvalidAndTruncatedAvatarsCannotBeRestored() {
        val data = BackupFixture.complete()
        val variants = listOf(
            data.copy(avatars = emptyMap()),
            data.copy(avatars = data.avatars + ("../outside.png" to BackupFixture.png())),
            data.copy(avatars = mapOf(BackupFixture.avatar to data.avatars.values.single().copyOfRange(0, 30))),
            data.copy(avatars = mapOf(BackupFixture.avatar to byteArrayOf(1))),
            data.copy(avatars = mapOf(BackupFixture.avatar to data.avatars.values.single().copyOf().apply { this[20] = 9 })),
        )
        variants.forEach { assertTrue(runCatching { it.validate() }.isFailure) }
    }
    @Test fun validChecksumDoesNotBypassRelationshipValidation() {
        val payload = altered { it.getJSONArray("nodes").getJSONObject(0).put("parentId", "inner") }
        val archive = ByteArrayOutputStream().also { BackupContainer.write(payload, it) }.toByteArray()
        assertTrue(runCatching { BackupJson.decode(BackupContainer.read(ByteArrayInputStream(archive))) }.isFailure)
    }
}
