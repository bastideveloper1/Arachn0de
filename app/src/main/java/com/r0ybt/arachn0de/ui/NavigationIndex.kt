package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.access.ElementSearch
import com.r0ybt.arachn0de.access.ElementResult
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** No hidden-screen work or replay from a previously closed consumer. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun navigationIndex(source:Flow<Pair<List<Project>,List<Node>>?>,scope:CoroutineScope):StateFlow<List<ElementResult>?> =
    source.mapLatest {input->input?.let {withContext(Dispatchers.Default) {ElementSearch.index(it.first,it.second)}}}
        .stateIn(scope,SharingStarted.WhileSubscribed(stopTimeoutMillis=0,replayExpirationMillis=0),null)
