package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.FinancialSnapshot
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.report.*
import kotlinx.coroutines.*
import java.util.Locale

/** Ephemeral export operation; saved state contains cache filenames, never financial data. */
@Composable
internal fun ObligationReportActions(snapshot: FinancialSnapshot?, projects: List<Project>, personName: String?, locale: Locale) {
    val context = LocalContext.current
    val files = remember(context) { ObligationReportFiles((context.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.privateContext ?: context.applicationContext) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var readyName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSaveName by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ObligationReportFiles.MIME)) { uri ->
        val name = pendingSaveName
        pendingSaveName = null
        if (uri == null) {
            busy = false
        } else if (name == null) {
            busy = false
            error = "No se pudo recuperar el informe. Genéralo de nuevo."
        } else {
            busy = true
            scope.launch {
                try {
                    files.save(files.cachedFile(name), uri)
                    notice = "Informe guardado."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: OutOfMemoryError) {
                    error = "No hay memoria suficiente para guardar el informe. Reintenta."
                } catch (_: Exception) {
                    error = "No se pudo guardar el informe. Reintenta; si quedó un archivo incompleto en el destino, elimínalo."
                } finally { busy = false }
            }
        }
    }

    val working = busy || pendingSaveName != null
    TextButton(enabled = !working && readyName == null && snapshot?.tasks?.isNotEmpty() == true,
        modifier = Modifier.testTag("generate-obligation-report"),
        onClick = {
            val captured = snapshot ?: return@TextButton
            val capturedProjects = projects
            val capturedPerson = personName
            val capturedLocale = locale
            val generatedAt = System.currentTimeMillis()
            busy = true; error = null; notice = null
            scope.launch {
                try {
                    val data = withContext(Dispatchers.Default) {
                        ObligationReportData.from(captured, capturedProjects, capturedPerson, generatedAt, capturedLocale)
                    }
                    readyName = files.generate(data).name
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ReportTooLargeException) {
                    error = failure.message
                } catch (failure: ReportMemoryException) {
                    error = failure.message
                } catch (_: OutOfMemoryError) {
                    error = "No hay memoria suficiente para generar el informe. Reduce el período."
                } catch (_: Exception) {
                    error = "No se pudo generar el informe. Reintenta o elige un período más corto."
                } finally { busy = false }
            }
        }) {
        if (busy && readyName == null) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Generando informe…")
        } else Text("Generar informe")
    }
    readyName?.let { name ->
        AlertDialog(
            onDismissRequest = { if (!working) { readyName = null; notice = null } },
            title = { Text("Informe PNG listo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice ?: "El informe contiene el contexto capturado al generarlo.")
                    if (working) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Guardando informe…")
                    }
                    TextButton(enabled = !working, onClick = {
                        try {
                            context.startActivity(Intent.createChooser(files.shareIntent(files.cachedFile(name)), "Compartir informe"))
                        } catch (_: Exception) {
                            error = "No se pudo compartir el informe. Reintenta o vuelve a generarlo."
                        }
                    }) { Text("Compartir") }
                    TextButton(enabled = !working, onClick = {
                        try {
                            files.cachedFile(name)
                            pendingSaveName = name
                            saveLauncher.launch(name)
                        } catch (_: Exception) {
                            pendingSaveName = null
                            error = "No se pudo abrir el selector para guardar. Reintenta o vuelve a generar el informe."
                        }
                    }) { Text("Guardar PNG") }
                }
            },
            confirmButton = { TextButton(enabled = !working, onClick = { readyName = null; notice = null }) { Text("Cerrar") } },
        )
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("No se pudo completar la operación") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Aceptar") } })
    }
}
