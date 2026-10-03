package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Only data, selection, month or timezone rebuild totals; minute ticks do not. */
@Composable
internal fun rememberFinancial(
    tree: NodeTreeSnapshot, responsibleByNode: Map<String, List<Person>>,
    selection: FinancialSelection, currentMonth: CalendarMonth, zoneId: String,
): State<FinancialSnapshot?> = produceState<FinancialSnapshot?>(null, tree, responsibleByNode, selection, currentMonth, zoneId) {
    value = withContext(Dispatchers.Default) {
        FinancialSnapshot(tree, responsibleByNode, selection, currentMonth, TimeZone.getTimeZone(zoneId))
    }
}
