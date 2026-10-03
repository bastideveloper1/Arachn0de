package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.domain.model.*

internal class ScopeFilters {
    var time by mutableStateOf(TimeFilter.ALL)
    var person by mutableStateOf<String?>(null)
    var completion by mutableStateOf(CompletionFilter.ALL)
    var tag by mutableStateOf<String?>(null)
    var open by mutableStateOf(false)
    val active get() = time != TimeFilter.ALL || person != null || completion != CompletionFilter.ALL || tag != null
    fun clear() { time = TimeFilter.ALL; person = null; completion = CompletionFilter.ALL; tag = null }
    companion object { val Saver = listSaver<ScopeFilters, String>(save = { listOf(it.time.name, it.person.orEmpty(), it.completion.name, it.tag.orEmpty(), it.open.toString()) },
        restore = { ScopeFilters().apply { time = TimeFilter.valueOf(it[0]); person = it[1].ifEmpty { null }; completion = CompletionFilter.valueOf(it[2]); tag = it[3].ifEmpty { null }; open = it[4].toBoolean() } }) }
}
internal class ScopeFilterStore {
    val scopes = mutableStateMapOf<String, ScopeFilters>()
    fun scope(id: String): ScopeFilters = scopes.getOrPut(id) { ScopeFilters() }
    companion object { val Saver = listSaver<ScopeFilterStore, String>(
        save = { store -> store.scopes.flatMap { (id, s) -> listOf(id, s.time.name, s.person.orEmpty(), s.completion.name, s.tag.orEmpty(), s.open.toString()) } },
        restore = { values -> ScopeFilterStore().apply { values.chunked(6).forEach { row -> scopes[row[0]] = ScopeFilters().apply { time = TimeFilter.valueOf(row[1]); person = row[2].ifEmpty { null }; completion = CompletionFilter.valueOf(row[3]); tag = row[4].ifEmpty { null }; open = row[5].toBoolean() } } } }) }
}
@Composable internal fun ScopeFiltersDialog(state: ScopeFilters, people: List<Person>, tags: List<Tag>) {
    AlertDialog(onDismissRequest = { state.open = false }, title = { Text("Filtros de esta vista") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ScopeMenu("Tiempo: ${timeFilterLabel(state.time)}") { close -> TimeFilter.entries.forEach { value -> DropdownMenuItem(text = { Text(timeFilterLabel(value)) }, onClick = { state.time = value; close() }) } }
            ScopeMenu("Persona: ${people.firstOrNull { it.id == state.person }?.name ?: "Todas"}") { close ->
                DropdownMenuItem(text = { Text("Todas") }, onClick = { state.person = null; close() })
                people.forEach { person -> DropdownMenuItem(text = { Text(person.name) }, onClick = { state.person = person.id; close() }) }
            }
            ScopeMenu("Estado: ${completionLabel(state.completion)}") { close -> CompletionFilter.entries.forEach { value -> DropdownMenuItem(text = { Text(completionLabel(value)) }, onClick = { state.completion = value; close() }) } }
            TagSelector(tags, setOfNotNull(state.tag), { state.tag = it.firstOrNull() }, single = true)
            TextButton(onClick = { state.clear(); state.open = false }) { Text("Limpiar filtros") }
        }
    }, confirmButton = { TextButton(onClick = { state.open = false }) { Text("Aplicar") } })
}
@Composable private fun ScopeMenu(label: String, content: @Composable (() -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text(label) }; DropdownMenu(open, { open = false }) { content { open = false } } }
}
