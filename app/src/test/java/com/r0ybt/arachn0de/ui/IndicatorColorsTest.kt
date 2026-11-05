package com.r0ybt.arachn0de.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class IndicatorColorsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val storage get()=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("indicator-test",0)
    @Before fun clear() {storage.edit().clear().commit()}
    @Test fun monochromeContrastAdaptsAndReadableCustomColorsAreUnchanged() {
        for(background in listOf(Color.Black,Color.White,Color(0xff202020),Color(0xffeeeeee))) {
            for(tint in listOf(Color.White,Color.Black,Color.LightGray,Color.DarkGray,Color.Red,Color.Blue,Color.Green)) {
                val actual=contrastingIndicator(tint,background,true)
                val before=androidx.core.graphics.ColorUtils.calculateContrast(tint.toArgb(),background.toArgb())
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(actual.toArgb(),background.toArgb())>=3.0)
                if(before>=3.0)assertEquals(tint,actual)
            }
            assertNotEquals(contrastingIndicator(background,background,true),contrastingIndicator(background,background,false))
        }
    }
    @Test fun perCardBackgroundAndThemeSwitchDoNotWriteChosenColors() {
        val preferences=AppearancePreferences(storage)
        preferences.setIndicatorColor("notes",IndicatorColor.WHITE)
        var background by mutableStateOf(Color.Black)
        var active=Color.Unspecified;var inactive=Color.Unspecified
        compose.setContent {Arachn0deTheme(preferences) {
            GeneralCard(colors=androidx.compose.material3.CardDefaults.cardColors(containerColor=background)) {
                val a=cardIndicatorColor("notes",true);val b=cardIndicatorColor("notes",false)
                SideEffect {active=a;inactive=b}
            }
        }}
        for(theme in listOf(AppearanceTheme.Arachn0de,AppearanceTheme.BlackAndWhite,AppearanceTheme.Arachn0de)) {
            compose.runOnIdle {preferences.setTheme(theme);background=if(theme==AppearanceTheme.BlackAndWhite)Color.White else Color.Black}
            compose.runOnIdle {
                assertEquals(if(background==Color.White)Color.Black else IndicatorColor.WHITE.color,active)
                assertNotEquals(active,inactive)
                assertEquals(IndicatorColor.WHITE,preferences.settings.indicatorColors["notes"])
                assertEquals("WHITE",storage.getString("indicator_color_notes",null))
            }
        }
    }



    @Test fun vividPalettePreservesChoicesIncludingLilac() {
        val chromatic=IndicatorColor.entries.filter {it!=IndicatorColor.WHITE && it!=IndicatorColor.GREY}
        chromatic.forEach {choice->
            val c=choice.color
            val maximum=maxOf(c.red,c.green,c.blue)
            val minimum=minOf(c.red,c.green,c.blue)
            assertTrue(choice.name,maximum>=0.9f && (maximum-minimum)/maximum>=0.5f)
            val preferences=AppearancePreferences(storage)
            preferences.setIndicatorColor("high",choice)
            val encoded=com.r0ybt.arachn0de.security.EncryptedPreferences.encode(storage.all)
            AppearancePreferences.validate(encoded)
            val restored=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("indicator-restored",0)
            restored.edit().clear().putString("indicator_color_high",choice.name).commit()
            assertEquals(choice,AppearancePreferences(restored).settings.indicatorColors["high"])
        }
    }
    @Test fun independentChoicesPersistAndResetOnlyColorsWithExactOriginalDefaults() {
        val preferences=AppearancePreferences(storage)
        assertTrue(preferences.settings.indicatorColors.isEmpty())
        customizableCardIndicators.forEachIndexed {index,indicator->preferences.setIndicatorColor(indicator.name,IndicatorColor.entries[index%IndicatorColor.entries.size])}
        assertEquals(customizableCardIndicators.size,preferences.settings.indicatorColors.size)
        assertEquals(preferences.settings,AppearancePreferences(storage).settings)
        preferences.setIndicatorColor("notes",IndicatorColor.BLUE);preferences.setIndicatorColor("people",IndicatorColor.BLUE)
        assertEquals(IndicatorColor.BLUE,preferences.settings.indicatorColors["notes"])
        assertEquals(IndicatorColor.BLUE,preferences.settings.indicatorColors["people"])
        preferences.setTextSize(AppearanceTextSize.Large);preferences.resetIndicatorColors()
        assertTrue(AppearancePreferences(storage).settings.indicatorColors.isEmpty())
        assertEquals(AppearanceTextSize.Large,preferences.settings.textSize)
        assertEquals(Color(0xFFEEEEEE),IndicatorColor.WHITE.color)
    }
    @Test fun cardTintsUpdateImmediatelyWhileAllInactiveSignalsRemainVisible() {
        val preferences=AppearancePreferences(storage)
        val ids=customizableCardIndicators.map {it.name}
        var active=emptyMap<String,Color>();var inactive=emptyMap<String,Color>()
        compose.setContent {Arachn0deTheme(preferences) {
            val colors=ids.associateWith {cardIndicatorColor(it,true)}
            val grey=ids.associateWith {cardIndicatorColor(it,false)}
            SideEffect {active=colors;inactive=grey}
            Column(Modifier.width(400.dp)) {
                CompactCardIndicators("empty")
                CompactCardIndicators("full",CompactCardFacts(people=setOf("p"),technologies=setOf("t"),notes=setOf("n"),tasks=setOf("x"),files=setOf("f"),due=true,today=true,high=true))
            }
        }}
        compose.runOnIdle {assertTrue(active.values.all {it==BlackWhite90.Text});assertTrue(inactive.values.all {it==BlackWhite90.Inactive})}
        ids.forEachIndexed {index,id->compose.runOnIdle {preferences.setIndicatorColor(id,IndicatorColor.entries[index%IndicatorColor.entries.size])}}
        compose.runOnIdle {ids.forEachIndexed {index,id->assertEquals(IndicatorColor.entries[index%IndicatorColor.entries.size].color,active[id]);assertEquals(BlackWhite90.Inactive,inactive[id])}}
        ids.filterNot {it in setOf("verify","birthday")}.forEach {compose.onNodeWithTag("card-signal:full:$it").assertExists()}
        ids.filterNot {it in setOf("verify","birthday")}.forEach {compose.onNodeWithTag("card-signal:empty:$it").assertExists()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,if(it=="notes") "Sin contenido" else "Sin datos"))}
        compose.runOnIdle {preferences.resetIndicatorColors()}
        compose.runOnIdle {assertTrue(active.values.all {it==BlackWhite90.Text})}
    }
    @Test fun permanentSlotsFitNarrowCardsAndKeepTheirPositionsWhenActivated() {
        var facts by mutableStateOf(CompactCardFacts())
        compose.setContent {Arachn0deTheme {
            Box(Modifier.width(140.dp)) {CompactCardIndicators("narrow",facts,showTechnologies=false)}
        }}
        val ids=listOf("people","notes","due","tasks","files","today","high")
        val before=ids.map {compose.onNodeWithTag("card-signal:narrow:$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot}
        before.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left)}
        assertTrue(before.map {it.width}.all {kotlin.math.abs(it-before.first().width)<1f})
        compose.onNodeWithTag("card-signal:narrow:technologies").assertDoesNotExist()
        compose.runOnIdle {facts=CompactCardFacts(people=setOf("p"),notes=setOf("n"),tasks=setOf("t"),files=setOf("f"),due=true,today=true,high=true)}
        ids.forEachIndexed {index,id->
            val icon=compose.onNodeWithTag("card-signal:narrow:$id").assertIsDisplayed()
            icon.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,if(id=="notes") "Con contenido" else "Configurado"))
            assertEquals(before[index],icon.fetchSemanticsNode().boundsInRoot)
        }
    }
    @Test fun settingsDrawerKeepsOneRenamedDesignDestination() {
        var opened=false
        compose.setContent {Arachn0deTheme {AppIdentityDrawer({},onAppearance={opened=true})}}
        compose.onNodeWithText("Ajustes").performScrollTo().performClick()
        compose.onAllNodesWithText("Apariencia").assertCountEquals(1)
        compose.onNodeWithText("Apariencia").assertDoesNotExist()
        compose.onNodeWithText("Apariencia").performScrollTo().performClick()
        assertTrue(opened)
    }
    @Test fun designSectionChoosesPaletteAndRestoresWithoutRemovingExistingOptions() {
        val preferences=AppearancePreferences(storage)
        compose.setContent {Arachn0deTheme(preferences) {AppearanceScreen({},preferences)}}
        compose.onNodeWithText("Apariencia").assertExists()
        compose.onNodeWithText("Tema").assertExists()
        compose.onNodeWithText("Tamaño de texto").performScrollTo().assertExists()
        compose.onNodeWithTag("indicator-setting:notes").performScrollTo().performClick()
        compose.onNodeWithTag("indicator-color:VIOLET").performScrollTo().performClick()
        assertEquals(IndicatorColor.VIOLET,preferences.settings.indicatorColors["notes"])
        for(name in listOf("verify","birthday")) {
            compose.onNodeWithTag("indicator-setting:$name").performScrollTo().performClick()
            compose.onNodeWithTag("indicator-color:BLUE").performScrollTo().performClick()
            assertEquals(IndicatorColor.BLUE,preferences.settings.indicatorColors[name])
        }
        compose.onNodeWithTag("indicator-reset").performScrollTo().performClick()
        assertTrue(preferences.settings.indicatorColors.isEmpty())
    }
    @org.robolectric.annotation.Config(qualifiers="w320dp-h850dp")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun twoColumnGridConfiguresFiveEffortColorsIndependentlyAndResetsOnlyThoseLevels() {
        val preferences=AppearancePreferences(storage)
        preferences.setIndicatorColor("verify",IndicatorColor.VIOLET)
        val selected=(1..5).associateWith {IndicatorColor.entries[it]}
        var applied=emptyMap<Int,Color>()
        compose.setContent {Arachn0deTheme(preferences) {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.8f)) {
                val colors=(1..5).associateWith {effortIndicatorColor(it)}
                SideEffect {applied=colors}
                AppearanceScreen({},preferences)
            }
        }}
        val rows=(customizableCardIndicators.map {it.name}+"effort").chunked(2)
        for(pair in rows) {
            compose.onNodeWithTag("indicator-setting:${pair.first()}").performScrollTo().assertHeightIsAtLeast(48.dp)
            if(pair.size==2) {
                val left=compose.onNodeWithTag("indicator-setting:${pair[0]}").fetchSemanticsNode().boundsInRoot
                val right=compose.onNodeWithTag("indicator-setting:${pair[1]}").fetchSemanticsNode().boundsInRoot
                assertEquals(left.top,right.top,1f);assertTrue(left.right<right.left)
            }
        }
        compose.onNodeWithTag("indicator-setting:effort").performScrollTo().performClick()
        for((level,color) in selected) {
            compose.onNodeWithTag("effort-color-level:$level").assertIsDisplayed().assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("indicator-color:${color.name}").performScrollTo().performClick()
            assertEquals(color,preferences.settings.indicatorColors["effort_$level"])
            compose.runOnIdle {assertEquals(color.color,applied[level])}
        }
        assertEquals(preferences.settings,AppearancePreferences(storage).settings)
        assertEquals(IndicatorColor.VIOLET,preferences.settings.indicatorColors["verify"])
        compose.onNodeWithTag("effort-colors-reset").performClick()
        assertTrue(effortIndicatorNames.none {it in AppearancePreferences(storage).settings.indicatorColors})
        compose.runOnIdle {assertTrue(applied.values.all {it==BlackWhite90.Text})}
        assertEquals(IndicatorColor.VIOLET,preferences.settings.indicatorColors["verify"])
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("indicator-reset").performScrollTo().performClick()
        assertTrue(AppearancePreferences(storage).settings.indicatorColors.isEmpty())
    }

}
