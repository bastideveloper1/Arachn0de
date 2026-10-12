package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[28])
class PersonPhotoComparisonTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun comparisonIsLocalReversibleAndHasNoDefinitiveDefault() {
        compose.setContent {Arachn0deTheme {Column {
            PersonPhotoComparison(Modifier.width(280.dp),profile=true) {Box(it.aspectRatio(1f))}
        }}}
        compose.onNodeWithTag("person-preview-frame:none").assertExists()
        compose.onNodeWithTag("person-border-A").performClick().assertIsSelected()
        compose.onNodeWithTag("person-preview-frame:A").assertExists()
        compose.onNodeWithTag("person-border-B").performClick().assertIsSelected()
        compose.onNodeWithTag("person-border-A").assertIsNotSelected()
        compose.onNodeWithTag("person-preview-frame:B").assertExists()
        compose.onNodeWithTag("person-border-B").performClick().assertIsNotSelected()
        compose.onNodeWithTag("person-preview-frame:none").assertExists()
    }
}
