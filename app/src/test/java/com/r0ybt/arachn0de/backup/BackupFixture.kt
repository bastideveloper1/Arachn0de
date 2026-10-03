package com.r0ybt.arachn0de.backup

import android.graphics.Bitmap
import com.r0ybt.arachn0de.data.local.*
import java.io.ByteArrayOutputStream

internal object BackupFixture {
    // JVM directory fsync uses the host filesystem; Robolectric's ShadowLinux cannot open directories.
    fun syncDirectory(directory: java.io.File) {
        java.nio.channels.FileChannel.open(directory.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
    }
    val avatar = "12345678-1234-1234-1234-123456789abc.png"
    fun png(): ByteArray {
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.MAGENTA)
        return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it); image.recycle() }.toByteArray()
    }
    fun empty() = BackupData("0.2.1", 1234, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
    fun complete() = BackupData("0.2.1", 1234,
        listOf(ProjectEntity("p", "Proyecto ☘", "Descripción\nUnicode", 7, 1, 9), ProjectEntity("q", "Otro", "", 2, 2, 10)),
        listOf(
            NodeEntity("root", "p", null, "Capa", "Contenido", false, 17, 3, 11, 100, 200),
            NodeEntity("inner", "p", "root", "Subcapa", "", false, 8, 4, 12),
            NodeEntity("bill", "p", "inner", "Obligación", "Dinero", true, 40, 5, 13, 100, 200, "ACTION", Long.MAX_VALUE, "USD"),
            NodeEntity("note", "p", "root", "Nota", "Texto completo\n👋", false, 8, 6, 14, 50, 60, "NOTE"),
            NodeEntity("task", "q", null, "Pendiente", "", false, 8, 7, 15),
            NodeEntity("clp", "q", null, "CLP", "", false, 8, 8, 16, amountMinor = 50000, currencyCode = "CLP"),
            NodeEntity("eur", "q", null, "EUR", "", false, 100, 9, 17, amountMinor = 1050, currencyCode = "EUR"),
        ),
        listOf(PersonEntity("r", "Roy", avatar), PersonEntity("s", "Scarlett", avatar), PersonEntity("t", "Sin avatar", null)),
        listOf(NodePersonEntity("bill", "r"), NodePersonEntity("bill", "s"), NodePersonEntity("root", "r"), NodePersonEntity("note", "s")),
        mapOf(avatar to png()),
    )
    fun archive(data: BackupData): ByteArray = ByteArrayOutputStream().also { BackupContainer.write(BackupJson.encode(data), it) }.toByteArray()
    fun assertData(expected: BackupData, actual: BackupData) {
        org.junit.Assert.assertEquals(expected.projects.sortedBy { it.id }, actual.projects.sortedBy { it.id })
        org.junit.Assert.assertEquals(expected.nodes.sortedBy { it.id }, actual.nodes.sortedBy { it.id })
        org.junit.Assert.assertEquals(expected.assignments.toSet(), actual.assignments.toSet())
        org.junit.Assert.assertEquals(expected.persons.map { it.copy(avatarFile = null) }.sortedBy { it.id }, actual.persons.map { it.copy(avatarFile = null) }.sortedBy { it.id })
        expected.persons.forEach { person ->
            val restored = actual.persons.single { it.id == person.id }
            if (person.avatarFile == null) org.junit.Assert.assertNull(restored.avatarFile)
            else org.junit.Assert.assertArrayEquals(expected.avatars.getValue(person.avatarFile), actual.avatars.getValue(requireNotNull(restored.avatarFile)))
        }
        org.junit.Assert.assertEquals(expected.avatars.size, actual.avatars.size)
    }
}
