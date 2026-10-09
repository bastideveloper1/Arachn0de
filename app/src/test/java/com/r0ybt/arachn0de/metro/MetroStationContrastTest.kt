package com.r0ybt.arachn0de.metro

import androidx.compose.ui.graphics.Color
import com.r0ybt.arachn0de.ui.theme.AppearanceTheme
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class MetroStationContrastTest {
    private fun luminance(c:Color):Double {
        fun channel(v:Float)=if(v<=.04045f) v/12.92 else ((v+.055)/1.055).pow(2.4)
        return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue)
    }
    private fun contrast(a:Color,b:Color):Double {
        val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    @Test fun everyThemeAndOverlappingStationStateKeepsReadableText() {
        for(theme in AppearanceTheme.entries) for(mask in 0 until 64) {
            // Current, next, completed, selected, combination and normal do not override foreground.
            val emphasized=mask and (1 or 8 or 16)!=0
            val (surface,text)=metroStationColors(theme.palette,emphasized)
            assertEquals(theme.palette.text,text)
            assertTrue("${theme.name}: $mask",contrast(surface,text)>=4.5)
            for(service in listOf("R","V","C",null)) {
                if(service=="R" || service=="C") assertTrue(contrast(surface,MetroRed)>=4.5)
                if(service=="V" || service=="C") assertTrue(contrast(surface,MetroGreen)>=4.5)
            }
        }
    }
}
