package com.r0ybt.arachn0de.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/** Draw-only blocks: offsets, letters, indentation and attachment references remain unchanged. */
internal fun DrawScope.drawDescriptionCodeBlocks(layout:TextLayoutResult,background:Color,outline:Color) {
    layout.layoutInput.text.getStringAnnotations("description-code",0,layout.layoutInput.text.length).forEach {range->
        if(range.start>=range.end || range.end>layout.layoutInput.text.length)return@forEach
        val first=layout.getLineForOffset(range.start)
        val last=layout.getLineForOffset(range.end-1)
        val top=layout.getLineTop(first)
        val bottom=layout.getLineBottom(last)
        drawRoundRect(background,Offset(0f,top),Size(size.width,bottom-top),CornerRadius(4.dp.toPx()))
        val stroke=1.dp.toPx()
        drawRoundRect(outline.copy(alpha=.65f),Offset(stroke/2,top+stroke/2),
            Size((size.width-stroke).coerceAtLeast(0f),(bottom-top-stroke).coerceAtLeast(0f)),
            CornerRadius(4.dp.toPx()),style=Stroke(stroke))
        // Draw the code icon without inserting characters or changing selection offsets.
        val middle=(layout.getLineTop(first)+layout.getLineBottom(first))/2
        val unit=minOf(3.dp.toPx(),(bottom-top)/6)
        val left=6.dp.toPx()
        fun segment(x1:Float,y1:Float,x2:Float,y2:Float) =
            drawLine(outline,Offset(x1,y1),Offset(x2,y2),1.4.dp.toPx(),StrokeCap.Round)
        segment(left+unit,middle-unit,left,middle)
        segment(left,middle,left+unit,middle+unit)
        segment(left+3*unit,middle-unit,left+4*unit,middle)
        segment(left+4*unit,middle,left+3*unit,middle+unit)
    }
}
