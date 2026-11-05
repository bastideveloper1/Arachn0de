package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException

/** Only an in-flight visual intent; the repository remains the source of truth. */
internal class FavoriteFeedback(scope:CoroutineScope) {
    val operation=OperationState(scope)
    private var pending by mutableStateOf<Boolean?>(null)
    fun shown(confirmed:Boolean)=pending ?: confirmed
    fun acknowledge(confirmed:Boolean) {if(!operation.busy && pending==confirmed) pending=null}
    fun toggle(confirmed:Boolean,write:suspend (Boolean)->Unit,onFailure:suspend ()->Unit={}) {
        if(operation.busy) return
        val desired=!shown(confirmed)
        pending=desired
        operation.submit("No se pudo cambiar Favoritos.",{
            try {write(desired);true}
            catch(cancelled:CancellationException) {pending=null;throw cancelled}
            catch(failure:Exception) {pending=null;onFailure();throw failure}
        })
    }
}
