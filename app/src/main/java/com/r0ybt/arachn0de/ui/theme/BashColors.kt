package com.r0ybt.arachn0de.ui.theme

import androidx.compose.ui.graphics.Color

internal object BashColors {
    val Background=Color(0xFF090C0A)
    val Electric=Color(0xFF39FF14)
    val Terminal=Color(0xFF7CFF7A)
    val Soft=Color(0xFFA5D6A7)
    val Muted=Color(0xFF829588)
    val Separator=Color(0xFF486A50)
    fun palette(base:AppearancePalette)=base.copy(
        background=Background,middle=Background,end=Background,scrim=Background,
        surface=Background,raised=Background,panel=Background,taskPanel=Background,
        text=Soft,muted=Muted,controls=Background,selection=Background,accentSurface=Background,
        solidPrimary=Terminal,onSolidPrimary=Background,outline=Separator,
        primary=Terminal,secondary=Soft,light=false)
}
