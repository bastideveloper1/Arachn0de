package com.r0ybt.arachn0de.document

import android.content.Context
import android.net.Uri
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import kotlinx.coroutines.*

/** No private output/staging files: the validated plan is rendered directly to the user's SAF file. */
internal suspend fun exportReport(context:Context,privateContext:Context,tree:Uri,content:ReportContent,options:ReportOptions,plan:ReportPlan,
    journal:DiagramExportJournal=DiagramExportJournal(context,privateContext,report=true)):Uri=withContext(Dispatchers.IO) {
    options.validate();val job=currentCoroutineContext();var ticket:DiagramExportJournal.Ticket?=null
    try {
        withContext(NonCancellable) {ticket=journal.beginReport(tree,options.format.mime,options.format.extension)}
        job.ensureActive();val current=requireNotNull(ticket);val uri=requireNotNull(current.uri)
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) {"No se pudo abrir el documento de destino."}.use {output->
            // DOCX finishes its ZIP without closing the provider stream; the outer scope closes it once.
            val borrowed=object:java.io.FilterOutputStream(output) {override fun close() {flush()}}
            if(options.format==ReportFormat.PDF)writeReportPdf(content,plan,borrowed) {job.ensureActive()}
            else ReportDocx.write(content,options,borrowed) {job.ensureActive()}
            output.flush()
        }
        job.ensureActive();journal.complete(current);uri
    } catch(failure:Throwable) {
        ticket?.let {t->try {journal.rollback(t)} catch(cleanup:Throwable) {throw IllegalStateException("Quedó una exportación parcial pendiente de limpieza. Se reintentará al abrir Exportar PDF / Word con acceso a la carpeta.",failure.apply {addSuppressed(cleanup)})}}
        throw failure
    }
}
