package com.r0ybt.arachn0de.document

import android.graphics.pdf.PdfDocument
import java.io.OutputStream

internal fun writeReportPdf(content:ReportContent,plan:ReportPlan,output:OutputStream,check:()->Unit={}) {
    val pdf=PdfDocument()
    try {
        plan.pages.forEachIndexed {i,page->check();val p=pdf.startPage(PdfDocument.PageInfo.Builder(ReportLayout.WIDTH,ReportLayout.HEIGHT,i+1).create())
            try {ReportLayout.draw(p.canvas,page,content,i+1,plan.pages.size,check)} finally {pdf.finishPage(p)}
        }
        check();pdf.writeTo(output)
    } finally {pdf.close()}
}
