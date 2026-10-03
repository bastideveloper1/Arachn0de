package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.r0ybt.arachn0de.domain.model.AttentionSnapshot
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recalculate once per persisted snapshot/clock emission, off the UI thread. */
@Composable
internal fun rememberAttention(tree: NodeTreeSnapshot, now: Long): State<AttentionSnapshot?> =
    produceState<AttentionSnapshot?>(null, tree, now) {
        value = withContext(Dispatchers.Default) { AttentionSnapshot(tree, now) }
    }
