package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w640dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapFramingSharedTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun sharedSquareFramingKeepsAvatarControlsAndPendingZoomAcrossRecreation() {
        val bitmap=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888)
        val restoration=StateRestorationTester(compose);var confirmed:com.r0ybt.arachn0de.domain.model.AvatarFraming?=null
        restoration.setContent {BitmapFramingDialog(bitmap,com.r0ybt.arachn0de.domain.model.AvatarFraming(2f,.4f,-.3f),{confirmed=it},{},"Encuadrar avatar",stateKey="avatar-fixture")}
        compose.onNodeWithContentDescription("Vista previa del avatar").assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(240.dp)
        compose.onNodeWithText("Ampliar").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Zoom: 2.40×").assertExists()
        compose.onNodeWithText("Centrar").performScrollTo().performClick();compose.onNodeWithText("Confirmar").performClick()
        assertEquals(2.4f,confirmed!!.zoom,.0001f);assertEquals(0f,confirmed!!.x);assertEquals(0f,confirmed!!.y)
        // The composition owns its bitmap until the rule disposes it.
    }
    @Test fun sharedTouchFramingCentersConfirmsAndCancelsWithoutChangingOtherImages() {
        val bitmap=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888)
        var open by mutableStateOf(true);var framing by mutableStateOf(com.r0ybt.arachn0de.domain.model.AvatarFraming(2f,.5f,-.5f))
        val other=com.r0ybt.arachn0de.domain.model.AvatarFraming(3f,-.2f,.6f)
        compose.setContent {if(open)BitmapFramingDialog(bitmap,framing,{framing=it;open=false},{open=false},"Encuadrar imagen",simple=true,aspect=3f,stateKey="fixture")}
        compose.onNodeWithContentDescription("Encuadre de la imagen").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(20f,10f))}
        compose.onNodeWithText("Centrar").performClick();compose.onNodeWithText("Confirmar").performClick()
        assertEquals(0f,framing.x);assertEquals(0f,framing.y);assertEquals(2f,framing.zoom)
        val confirmed=framing
        compose.runOnIdle {open=true}
        compose.onNodeWithContentDescription("Encuadre de la imagen").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(-30f,15f))}
        compose.onNodeWithText("Cancelar").performClick();assertEquals(confirmed,framing);assertEquals(3f,other.zoom)
        compose.runOnIdle {bitmap.recycle()}
    }
}
