package com.r0ybt.arachn0de

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.r0ybt.arachn0de.ui.AppSafeArea
import com.r0ybt.arachn0de.ui.AppRoot
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as Arachn0deApplication
        setContent {
            Arachn0deTheme {
                AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) }
            }
        }
    }
}
