package com.r0ybt.arachn0de.report

import android.content.res.Resources
import android.graphics.*
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.ui.theme.AppearanceTheme
import kotlin.math.ceil

class ReportTooLargeException : Exception("El informe es demasiado largo para generar un PNG. Elige un mes o una Persona.")
class ReportMemoryException : Exception("No hay memoria suficiente para generar el informe. Cierra otras aplicaciones o reduce el período.")

/** Measures a document, then paints it. Never reads a View, Compose tree, or database. */
class ObligationPngRenderer(private val resources: Resources) {
    companion object {
        const val WIDTH = 1440
        const val MAX_HEIGHT = 8000
        const val MAX_BITMAP_BYTES = 48L * 1024 * 1024

        fun bitmapBudget(): Long {
            val runtime = Runtime.getRuntime()
            val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
            return minOf(MAX_BITMAP_BYTES, runtime.maxMemory() / 4, available / 2).coerceAtLeast(0)
        }

        fun logoBounds(sourceWidth: Int, sourceHeight: Int): RectF {
            require(sourceWidth > 0 && sourceHeight > 0)
            val scale = minOf(64f / sourceWidth, 64f / sourceHeight)
            val w = sourceWidth * scale; val h = sourceHeight * scale
            return RectF(WIDTH - 352f, 72f + (64f - h) / 2, WIDTH - 352f + w, 72f + (64f + h) / 2)
        }
    }

    internal sealed interface Mark {
        data class Text(val layout: StaticLayout, val x: Float, val y: Float) : Mark
        data class Panel(val bounds: RectF) : Mark
        data class Rule(val y: Float) : Mark
        data object Logo : Mark
    }
    class Document internal constructor(val height: Int, internal val marks: List<Mark>)

    fun measure(data: ObligationReportData, budgetBytes: Long = bitmapBudget(), checkCancelled: () -> Unit = {}): Document {
        require(data.obligations.isNotEmpty()) { "No hay obligaciones para generar este informe." }
        val marks = mutableListOf<Mark>()
        val primary = AppearanceTheme.Arachn0de.palette.text.toArgb()
        val secondary = AppearanceTheme.Arachn0de.palette.muted.toArgb()
        val orange = AppearanceTheme.Arachn0de.palette.secondary.toArgb()
        val violet = AppearanceTheme.Arachn0de.palette.primary.toArgb()
        var y = 72f
        fun validate(height: Float) {
            if (!height.isFinite() || height > MAX_HEIGHT) throw ReportTooLargeException()
            if (ceil(height.toDouble()).toLong() * WIDTH * 4L > budgetBytes) throw ReportMemoryException()
        }
        fun text(value: String, x: Float, top: Float, width: Int, size: Float, color: Int = primary,
            bold: Boolean = false, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
            checkCancelled()
            // Bound layout work before allocating StaticLayout for pathological historical text.
            if (value.length > 20000) throw ReportTooLargeException()
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
                .setAlignment(alignment).setIncludePad(false).setLineSpacing(6f, 1f)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY).build()
            validate(top + layout.height + 72)
            marks.add(Mark.Text(layout, x, top))
            return layout.height.toFloat()
        }
        marks.add(Mark.Logo)
        text("ARACHN0DE", WIDTH - 268f, y + 18, 188, 26f, violet, true)
        y = 192f
        y += text("INFORME DE OBLIGACIONES", 80f, y, 1280, 50f, bold = true) + 24
        val context = listOfNotNull(data.person, data.period).joinToString(" · ")
        y += text(context, 80f, y, 1280, 34f, violet) + 40
        marks.add(Mark.Rule(y)); y += 48
        y += text("RESUMEN", 80f, y, 1280, 26f, violet, true) + 24
        data.currencies.forEach { currency ->
            checkCancelled()
            val start = y
            val panelIndex = marks.size
            marks.add(Mark.Panel(RectF()))
            y += 32
            y += text(currency.code, 112f, y, 1216, 30f, orange, true) + 24
            val values = listOf("Total" to currency.total, "Pendiente" to currency.pending, "Completado" to currency.completed)
            values.forEachIndexed { index, (label, amount) ->
                val x = 112f + index * 416
                val labelHeight = text(label, x, y, 384, 26f, secondary)
                text(amount, x, y + labelHeight + 12, 384, 34f, primary, true)
            }
            val texts = marks.subList(panelIndex + 1, marks.size).filterIsInstance<Mark.Text>()
            y = texts.maxOf { it.y + it.layout.height } + 32
            marks[panelIndex] = Mark.Panel(RectF(80f, start, 1360f, y))
            y += 16
        }
        y += 8
        y += text(data.counts, 80f, y, 1280, 26f, secondary) + 56
        y += text("OBLIGACIONES", 80f, y, 1280, 26f, violet, true) + 24
        data.obligations.forEach { entry ->
            checkCancelled()
            val start = y
            val panelIndex = marks.size
            marks.add(Mark.Panel(RectF()))
            y += 32
            val titleHeight = text(entry.title, 112f, y, 736, 36f, bold = true)
            val amountHeight = text(entry.amount, 880f, y, 448, 36f, orange, true, Layout.Alignment.ALIGN_OPPOSITE)
            y += maxOf(titleHeight, amountHeight) + 20
            y += text("${entry.due} · ${entry.status}", 112f, y, 1216, 28f, secondary) + 12
            y += text(entry.project, 112f, y, 1216, 28f, violet)
            entry.responsibleNames?.let {
                y += 12
                y += text("Responsables: $it", 112f, y, 1216, 28f, secondary)
            }
            y += 32
            validate(y + 72)
            marks[panelIndex] = Mark.Panel(RectF(80f, start, 1360f, y))
            y += 16
        }
        y += 32
        marks.add(Mark.Rule(y)); y += 32
        y += text("Generado por Arachn0de", 80f, y, 1280, 26f, violet) + 10
        y += text(data.generatedLabel, 80f, y, 1280, 24f, secondary) + 72
        validate(y)
        return Document(ceil(y.toDouble()).toInt(), marks.toList())
    }

    fun render(document: Document, checkCancelled: () -> Unit = {}): Bitmap {
        // Recheck the live heap immediately before the pixel allocation.
        if (document.height !in 1..MAX_HEIGHT) throw ReportTooLargeException()
        if (WIDTH.toLong() * document.height * 4L > bitmapBudget()) throw ReportMemoryException()
        var bitmap: Bitmap? = null
        var logo: Bitmap? = null
        try {
            checkCancelled()
            bitmap = Bitmap.createBitmap(WIDTH, document.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AppearanceTheme.Arachn0de.palette.end.toArgb())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            logo = BitmapFactory.decodeResource(resources, R.drawable.arachn0de_logo,
                BitmapFactory.Options().apply { inScaled = false; inSampleSize = 8 })
                ?: error("Logo no disponible")
            document.marks.forEach { mark ->
                checkCancelled()
                when (mark) {
                    is Mark.Text -> {
                        canvas.save(); canvas.translate(mark.x, mark.y)
                        mark.layout.draw(canvas); canvas.restore()
                    }
                    is Mark.Panel -> {
                        paint.color = AppearanceTheme.Arachn0de.palette.surface.toArgb()
                        canvas.drawRoundRect(mark.bounds, 20f, 20f, paint)
                    }
                    is Mark.Rule -> {
                        paint.color = AppearanceTheme.Arachn0de.palette.outline.toArgb(); paint.strokeWidth = 2f
                        canvas.drawLine(80f, mark.y, 1360f, mark.y, paint)
                    }
                    Mark.Logo -> {
                        paint.color = Color.WHITE; paint.isFilterBitmap = true
                        canvas.drawBitmap(logo, null, logoBounds(logo.width, logo.height), paint)
                    }
                }
            }
            return bitmap
        } catch (failure: OutOfMemoryError) {
            bitmap?.recycle()
            throw ReportMemoryException()
        } catch (failure: Throwable) {
            bitmap?.recycle()
            throw failure
        } finally { logo?.recycle() }
    }
}
