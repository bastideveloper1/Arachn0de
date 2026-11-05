package com.r0ybt.arachn0de.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.ui.theme.AppearanceTheme
import com.r0ybt.arachn0de.ui.theme.role
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomBlockOneIconsTest {
    private val labels=listOf("Bloquear","Buscar","Contenido","Crear","Crear borrador","Crear núcleo",
        "Desde plantilla","Filtrar","Nuevo elemento","Opciones","Ordenar","Abrir menú","Cerrar menú")

    @Test fun functionMappingsStayDistinctAndLeaveOtherActionsUntouched() {
        assertEquals(13,labels.map {requireNotNull(blockOneIconResource(it))}.toSet().size)
        labels.forEach {assertNull("No override outside bottom bars: $it",contextActionIconResource(it))}
        listOf("Descripción","Cuenta","Operar","Pagar","Editar","Mostrar barra contextual","Más acciones",null).forEach {
            assertNull(it,blockOneIconResource(it))
        }
        assertEquals(blockOneIconResource("Abrir menú"),blockOneIconResource("Menú"))
        assertNull(blockOneIconResource("Cerrar menú global"))
    }

    @Test fun vectorsRenderInsideViewportAtExistingSizesInEveryOfficialTheme() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        for(theme in AppearanceTheme.entries) for(label in labels) for(size in listOf(18,24,28,240)) {
            val drawable=app.getDrawable(requireNotNull(blockOneIconResource(label)))!!.mutate()
            val tint=theme.palette.role("icons").toArgb()
            drawable.setTint(tint)
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,size,size);drawable.draw(Canvas(bitmap))
            val pixels=IntArray(size*size);bitmap.getPixels(pixels,0,size,0,0,size,size)
            assertTrue("Visible $label / $theme / $size",pixels.any {it ushr 24>200})
            assertTrue("Tint $label / $theme",pixels.filter {it ushr 24>200}.all {pixel->listOf(0,8,16).all {shift->
                kotlin.math.abs(((pixel ushr shift) and 255)-((tint ushr shift) and 255))<=2
            }})
            // At small sizes antialias can legitimately reach the edge pixel.
            if(size==240) assertTrue("Unclipped $label",(0 until size).all {i->
                pixels[i] ushr 24==0 && pixels[(size-1)*size+i] ushr 24==0 &&
                    pixels[i*size] ushr 24==0 && pixels[i*size+size-1] ushr 24==0
            })
            bitmap.recycle()
        }
    }
}
