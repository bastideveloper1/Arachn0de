package com.r0ybt.arachn0de.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.r0ybt.arachn0de.domain.model.TitleLimits

/** Reject the entire excess edit, never truncate a paste or overwrite a recoverable long title. */
internal class TitleInputFeedback {
    var blocked by mutableStateOf(false)
        private set
    fun change(previous:String,next:String,limit:Int,accept:(String)->Unit) {
        val count=TitleLimits.count(next)
        if(count<=limit || (TitleLimits.count(previous)>limit && count<=TitleLimits.count(previous))) {
            blocked=false;accept(next)
        } else blocked=true
    }
}
@Composable internal fun rememberTitleInput()=remember {TitleInputFeedback()}
@Composable internal fun TitleLimitHint(value:String,limit:Int,blocked:Boolean=false) {
    val count=TitleLimits.count(value)
    val message=when {
        count>limit->"Supera el máximo de $limit caracteres. Reduce el título para guardar."
        blocked->"Máximo $limit caracteres. No se añadió el texto que excede el límite."
        count==limit->"Límite de $limit caracteres alcanzado."
        else->"Máximo $limit caracteres."
    }
    Text("$count / $limit · $message",style=MaterialTheme.typography.labelSmall,
        color=if(count>limit || blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
