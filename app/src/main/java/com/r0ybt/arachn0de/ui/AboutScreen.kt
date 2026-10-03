package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.BuildConfig
import com.r0ybt.arachn0de.update.*
import com.r0ybt.arachn0de.ui.state.UpdateActions
import com.r0ybt.arachn0de.ui.state.UpdateState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun AboutScreen(onBack: () -> Unit, repository: UpdateRepository = remember { UpdateRepository() }, downloads: UpdateDownloads? = null) {
    var linkError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val store = remember(context) { UpdateDownloadStore(File(context.cacheDir, "updates"), GitHubAssetFetcher(), AndroidApkValidator(context.applicationContext)) }
    val installer = remember(context) { AndroidUpdateInstaller(context) }
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope, store, downloads) { UpdateActions(repository, scope, BuildConfig.VERSION_NAME, downloads ?: store) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { actions.androidReturned(cancelled = true, settings = true) }
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { actions.androidReturned(cancelled = it.resultCode == Activity.RESULT_CANCELED) }
    LaunchedEffect(actions) { actions.recover() }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acerca de Arachn0de", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Volver") }
            Text("Versión instalada: ${BuildConfig.VERSION_NAME}")
            Text("Canal Beta · GitHub Releases oficiales")
            Button(onClick = actions::check, enabled = !actions.busy) {
                Text(if (actions.state == UpdateState.Error) "Volver a intentar" else "Buscar actualizaciones")
            }
            actions.notice?.let { Text(it) }
            when (val state = actions.state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> Text("Buscando actualizaciones…")
                is UpdateState.Downloading -> Text("Descargando actualización…")
                is UpdateState.Verifying -> Text("Verificando actualización…")
                is UpdateState.Ready -> {
                    Text("Actualización ${state.update.release.tag} verificada y lista para instalar.")
                    Text(state.update.release.title)
                    Text(state.update.release.notes.ifBlank { "Esta versión no incluye notas de cambios." })
                    Button(enabled = !actions.busy, onClick = {
                        actions.prepareInstall { update ->
                            try {
                                if (installer.needsSourcePermission()) settingsLauncher.launch(installer.sourceSettingsIntent())
                                else installLauncher.launch(installer.installIntent(update))
                            } catch (_: Exception) { actions.launchFailed() }
                        }
                    }) { Text("Instalar actualización") }
                }
                UpdateState.UpToDate -> Text("Arachn0de está actualizado.")
                UpdateState.Error -> {
                    if (actions.downloadRetry != null) Button(enabled = !actions.busy, onClick = actions::download) { Text("Reintentar descarga") }
                    else Text("No se pudieron comprobar las actualizaciones. Revisa tu conexión o vuelve a intentar más tarde.", color = MaterialTheme.colorScheme.error)
                }
                is UpdateState.Available -> {
                    Text("Nueva versión disponible", style = MaterialTheme.typography.titleLarge)
                    Text("Instalada: v${BuildConfig.VERSION_NAME}")
                    Text("Disponible: ${state.release.tag}")
                    Text(state.release.title)
                    if (state.release.prerelease) Text("Beta / versión preliminar")
                    Text(state.release.notes.ifBlank { "Esta versión no incluye notas de cambios." })
                    Button(onClick = actions::download, enabled = !actions.busy) { Text("Descargar actualización") }
                }
            }
            HorizontalDivider()
            Text("Autor", style = MaterialTheme.typography.titleMedium)
            Text("r0ybt")
            for ((label, url) in listOf(
                "Instagram · @itsbasti_an" to "https://www.instagram.com/itsbasti_an/",
                "GitHub · bastideveloper1" to "https://github.com/bastideveloper1",
                "Mastodon · @Yll@infosec.exchange" to "https://infosec.exchange/@Yll",
                "Web · 27thdeer.com" to "https://27thdeer.com/",
            )) {
                OutlinedButton(onClick = { linkError = !openExternalLink(context, url) }) { Text(label) }
            }
            HorizontalDivider()
            Text("Código fuente", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { linkError = !openExternalLink(context, "https://github.com/bastideveloper1/Arachn0de") }) {
                Text("github.com/bastideveloper1/Arachn0de")
            }
            if (linkError) Text("No se pudo abrir el enlace. No hay una aplicación disponible o Android impidió abrirlo.", color = MaterialTheme.colorScheme.error)
        }
    }
}

internal fun openExternalLink(context: android.content.Context, url: String): Boolean = try {
    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    true
} catch (_: android.content.ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
