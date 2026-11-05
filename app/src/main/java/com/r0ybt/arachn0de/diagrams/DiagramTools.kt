package com.r0ybt.arachn0de.diagrams

import com.r0ybt.arachn0de.visual.ToolLabeledButton
import com.r0ybt.arachn0de.visual.ToolMagnetIcon

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.r0ybt.arachn0de.ui.theme.role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette

@Composable internal fun DiagramToolRow(content:@Composable RowScope.()->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DiagramTool(label:String,glyph:String,active:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    val palette=LocalAppearancePalette.current
    val color=if(!enabled)palette.role("disabled") else if(active)palette.primary else palette.role("icons")
    TooltipBox(positionProvider=TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),tooltip={PlainTooltip {Text(label)}},state=rememberTooltipState()) {
        ToolLabeledButton(label,active,enabled,"diagram-tool:$label",onClick=onClick,icon={
            val icon=when(glyph) {
                "table"->Icons.Default.TableChart;"elements"->Icons.Default.AddBox;"text"->Icons.Default.TextFields;"import"->Icons.Default.AddPhotoAlternate;"replace"->Icons.Default.Image
                "delete"->Icons.Default.Delete;"duplicate"->Icons.Default.ContentCopy;"color"->Icons.Default.Palette
                "fill"->Icons.Default.FormatColorFill;"border"->Icons.Default.BorderColor;"size"->Icons.Default.AspectRatio
                "label"->Icons.Default.Edit;"play"->Icons.Default.PlayArrow;"pause"->Icons.Default.Pause;"stop"->Icons.Default.Stop
                "present"->Icons.Default.Slideshow;"save"->Icons.Default.Save;"copy"->Icons.Default.FileCopy
                "export"->Icons.Default.IosShare;"undo"->Icons.AutoMirrored.Filled.Undo;"redo"->Icons.AutoMirrored.Filled.Redo
                "grid"->Icons.Default.GridOn;"snap"->Icons.Default.Grid4x4;"guides"->Icons.Default.CenterFocusStrong
                "fit"->Icons.Default.FitScreen;"pan"->Icons.Default.OpenWith;"select"->Icons.Default.TouchApp
                "multi"->Icons.Default.SelectAll;"connect"->Icons.Default.AddLink;"image"->Icons.Default.Image;"file"->Icons.AutoMirrored.Filled.InsertDriveFile
                "settings"->Icons.Default.Settings;"history"->Icons.Default.History;"document"->Icons.Default.Description
                "minimize"->Icons.Default.KeyboardArrowDown;"help"->Icons.AutoMirrored.Filled.HelpOutline;"back"->Icons.AutoMirrored.Filled.ArrowBack;"done"->Icons.Default.Check
                else->null
            }
            if(glyph=="snap")ToolMagnetIcon(color)
            else if(icon!=null)Icon(icon,null,Modifier.size(22.dp),tint=color)
            else Canvas(Modifier.size(24.dp)) {
                val w=size.width;val h=size.height;val stroke=Stroke(2.dp.toPx())
                fun line(a:Offset,b:Offset)=drawLine(color,a,b,2.dp.toPx())
                fun arrow(x:Float,left:Boolean) {val sign=if(left)1 else -1;line(Offset(x,h/2),Offset(x+sign*w*.23f,h*.25f));line(Offset(x,h/2),Offset(x+sign*w*.23f,h*.75f))}
                when {
                    glyph.startsWith("ratio:")-> {val parts=glyph.split(':');val ratio=parts[1].toFloat()/parts[2].toFloat();val rw=if(ratio>1)w*.9f else h*.9f*ratio;val rh=rw/ratio;drawRect(color,Offset((w-rw)/2,(h-rh)/2),Size(rw,rh),style=stroke)}
                    glyph.startsWith("number:")->drawContext.canvas.nativeCanvas.drawText(glyph.substringAfter(':'),w/2,h*.72f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {this.color=color.toArgb();textSize=18.dp.toPx();textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
                    glyph.startsWith("color:")-> {drawRect(Color(glyph.substringAfter(':').toLong(16).toInt()));drawRect(color,style=stroke)}
                    glyph=="transparent"->{for(x in 0..3)for(y in 0..3)drawRect(if((x+y)%2==0)Color.LightGray else Color.DarkGray,Offset(w*x/4,h*y/4),Size(w/4,h/4));drawRect(color,style=stroke)}
                    glyph=="particle"->drawCircle(color,5.dp.toPx(),Offset(w/2,h/2))
                    glyph=="sphere"->{drawCircle(color,11.dp.toPx(),Offset(w/2,h/2),style=stroke);drawCircle(color,3.dp.toPx(),Offset(w*.38f,h*.35f))}
                    glyph=="rect"->drawRect(color,Offset(w*.08f,h*.2f),Size(w*.84f,h*.6f),style=stroke)
                    glyph=="round"->drawRoundRect(color,Offset(w*.08f,h*.2f),Size(w*.84f,h*.6f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.15f),style=stroke)
                    glyph=="ellipse"->drawOval(color,Offset(w*.08f,h*.2f),Size(w*.84f,h*.6f),style=stroke)
                    glyph=="diamond"->drawPath(Path().apply {moveTo(w/2,h*.05f);lineTo(w*.95f,h/2);lineTo(w/2,h*.95f);lineTo(w*.05f,h/2);close()},color,style=stroke)
                    glyph in listOf("straight","orthogonal","curve")-> {
                        val path=Path().apply {moveTo(w*.05f,h*.85f);when(glyph) {"straight"->lineTo(w*.95f,h*.15f);"orthogonal"->{lineTo(w*.5f,h*.85f);lineTo(w*.5f,h*.15f);lineTo(w*.95f,h*.15f)};else->cubicTo(w*.8f,h*.85f,w*.2f,h*.15f,w*.95f,h*.15f)}}
                        drawPath(path,color,style=stroke)
                    }
                    glyph in listOf("solid","dashed","dotted")->drawLine(color,Offset(0f,h/2),Offset(w,h/2),2.dp.toPx(),cap=if(glyph=="dotted")StrokeCap.Round else StrokeCap.Butt,pathEffect=when(glyph) {"dashed"->PathEffect.dashPathEffect(floatArrayOf(w*.2f,w*.12f));"dotted"->PathEffect.dashPathEffect(floatArrayOf(1f,w*.16f));else->null})
                    glyph.startsWith("arrow:")-> {line(Offset(w*.05f,h/2),Offset(w*.95f,h/2));val kind=glyph.substringAfter(':');if(kind=="start"||kind=="both")arrow(w*.05f,true);if(kind=="end"||kind=="both")arrow(w*.95f,false)}
                    glyph.startsWith("align:")-> {
                        val action=DiagramArrange.valueOf(glyph.substringAfter(':'))
                        if(action in setOf(DiagramArrange.HSPACE,DiagramArrange.VSPACE)) {
                            repeat(3) {i->if(action==DiagramArrange.HSPACE)drawRect(color,Offset(w*(.08f+i*.34f),h*.2f),Size(w*.15f,h*.6f),style=stroke) else drawRect(color,Offset(w*.2f,h*(.08f+i*.34f)),Size(w*.6f,h*.15f),style=stroke)}
                        } else {
                            val horizontal=action in setOf(DiagramArrange.LEFT,DiagramArrange.HCENTER,DiagramArrange.RIGHT)
                            val anchor=when(action) {DiagramArrange.LEFT,DiagramArrange.TOP->.12f;DiagramArrange.RIGHT,DiagramArrange.BOTTOM->.88f;else->.5f}
                            if(horizontal)line(Offset(w*anchor,0f),Offset(w*anchor,h)) else line(Offset(0f,h*anchor),Offset(w,h*anchor))
                            repeat(2) {i->val length=if(i==0).65f else .4f;val start=when(action) {DiagramArrange.LEFT,DiagramArrange.TOP->anchor;DiagramArrange.RIGHT,DiagramArrange.BOTTOM->anchor-length;else->anchor-length/2}
                                if(horizontal)drawRect(color,Offset(w*start,h*(.15f+i*.5f)),Size(w*length,h*.22f)) else drawRect(color,Offset(w*(.15f+i*.5f),h*start),Size(w*.22f,h*length))}
                        }
                    }
                }
                if(active)drawCircle(palette.primary,3.dp.toPx(),Offset(w,h))
            }
        })
    }
}
@Composable internal fun DiagramColors(value:Int,change:(Int)->Unit) {
    DiagramToolRow {listOf("Transparente" to 0,"Blanco" to -1,"Negro" to 0xff17202a.toInt(),"Azul" to 0xff4c8ddd.toInt(),"Rojo" to 0xffd84343.toInt(),"Verde" to 0xff48a878.toInt()).forEach {(label,color)->
        DiagramTool(label,if(color==0)"transparent" else "color:${color.toUInt().toString(16)}",value==color) {change(color)}
    }}
}
