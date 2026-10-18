package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import com.r0ybt.arachn0de.ui.theme.BashColors
import kotlinx.coroutines.delay

/** Decorative ink only: never participates in text, semantics, measurement or hit testing. */
internal class BashTextCursor {
    var layout by mutableStateOf<TextLayoutResult?>(null)
    var modifier:Modifier = Modifier
}

@Composable
internal fun rememberBashTextCursor():BashTextCursor {
    val cursor=remember {BashTextCursor()}
    if(!LocalBashPresentation.current) {cursor.modifier=Modifier;return cursor}
    val measurer=rememberTextMeasurer()
    val visible by produceState(true) {
        while(true) {delay(500);value=!value}
    }
    cursor.modifier=Modifier.drawWithContent {
        drawContent()
        val layout=cursor.layout
        if(visible && layout!=null && layout.lineCount>0 && layout.layoutInput.text.isNotEmpty()) {
            val line=layout.lineCount-1
            val glyph=measurer.measure("_",layout.layoutInput.style.merge(TextStyle(
                color=BashColors.Electric,fontFamily=FontFamily.Monospace)),maxLines=1)
            // Keep ink inside existing bounds, including clipped/ellipsized previews.
            val x=layout.getLineRight(line).coerceIn(0f,(size.width-glyph.size.width).coerceAtLeast(0f))
            val y=layout.getLineBaseline(line)-glyph.firstBaseline
            drawText(glyph,topLeft=Offset(x,y))
        }
    }
    return cursor
}
