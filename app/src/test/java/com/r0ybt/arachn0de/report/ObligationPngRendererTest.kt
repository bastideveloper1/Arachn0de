package com.r0ybt.arachn0de.report

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObligationPngRendererTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val renderer get() = ObligationPngRenderer(context.resources)
    @Test fun measuresAndRendersRealNonemptyPngWithSeparateCurrenciesAndLogo() {
        val document = renderer.measure(ReportFixture.data())
        assertTrue(document.height in 1000..ObligationPngRenderer.MAX_HEIGHT)
        val texts = document.marks.filterIsInstance<ObligationPngRenderer.Mark.Text>().map { it.layout.text.toString() }
        assertTrue(texts.containsAll(listOf("CLP", "USD", "INFORME DE OBLIGACIONES", "Generado por Arachn0de")))
        assertTrue(texts.contains("Responsables: Roy · Scarlett"))
        val bitmap = renderer.render(document)
        val bytes = ByteArrayOutputStream()
        try {
            ObligationReportFiles.writePng(bitmap, bytes)
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.toByteArray().take(8).toByteArray())
            assertTrue(bytes.size() > 10000)
            val decoded = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
            assertEquals(1440, decoded.width); assertEquals(document.height, decoded.height)
            assertNotEquals(decoded.getPixel(0, 0), decoded.getPixel(100, 600))
            decoded.recycle()
            // Synthetic fixture only; review artifact is not a user's financial report.
            val preview = File(context.cacheDir, "report-preview.png")
            preview.writeBytes(bytes.toByteArray())
            File("/tmp/arachn0de-report-preview.png").writeBytes(bytes.toByteArray())
        } finally { bitmap.recycle() }
    }
    @Test fun originalLogoLoadsAndScalesWithoutDistortion() {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.arachn0de_logo)
        assertNotNull(logo)
        val bounds = ObligationPngRenderer.logoBounds(logo.width, logo.height)
        assertEquals(logo.width.toFloat() / logo.height, bounds.width() / bounds.height(), 0.0001f)
        val rectangular = ObligationPngRenderer.logoBounds(200, 100)
        assertEquals(2f, rectangular.width() / rectangular.height(), 0.0001f)
        assertTrue(bounds.width() <= 64 && bounds.height() <= 64)
        logo.recycle()
    }
    @Test fun longTextWrapsWithinPanelsAndHeightIncludesAllRows() {
        // Layout assertions use a fixed budget, independent of the test worker's live heap.
        // The separate rejection test still checks height, memory and pathological text limits.
        val budget = ObligationPngRenderer.MAX_BITMAP_BYTES
        val short = renderer.measure(ReportFixture.data(ReportFixture.snapshot(source = listOf(ReportFixture.node("one")))), budget)
        val many = ReportFixture.data(ReportFixture.snapshot(source = List(10) {
            ReportFixture.node("n$it", title = "Obligación con un título largo que debe ocupar varias líneas y conservar todo su contenido")
        }, assignments = mapOf("n0" to List(12) { ReportFixture.roy.copy(id = "$it", name = "Responsable con nombre largo $it") })))
        val document = renderer.measure(many, budget)
        assertTrue(document.height > short.height)
        val texts = document.marks.filterIsInstance<ObligationPngRenderer.Mark.Text>()
        assertTrue(texts.any { it.layout.lineCount > 1 })
        texts.forEach { assertTrue(it.y + it.layout.height < document.height); assertTrue(it.x + it.layout.width <= 1360) }
        assertEquals(10, texts.count { it.layout.text.startsWith("Obligación con") })
    }
    @Test fun excessiveHeightMemoryAndPathologicalTextAreRejectedBeforeBitmapCreation() {
        val huge = ReportFixture.data(ReportFixture.snapshot(source = List(1000) { ReportFixture.node("n$it") }))
        assertThrows(ReportTooLargeException::class.java) { renderer.measure(huge, Long.MAX_VALUE) }
        assertThrows(ReportMemoryException::class.java) { renderer.measure(ReportFixture.data(), 1024) }
        val text = ReportFixture.data(ReportFixture.snapshot(source = listOf(ReportFixture.node("long", title = "x".repeat(20001)))))
        assertThrows(ReportTooLargeException::class.java) { renderer.measure(text, Long.MAX_VALUE) }
    }
}
