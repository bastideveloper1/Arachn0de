package com.r0ybt.arachn0de

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.r0ybt.arachn0de.ui.AppSafeArea
import com.r0ybt.arachn0de.ui.AppRoot
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme

class MainActivity : ComponentActivity() {
    private var recurrenceJob: kotlinx.coroutines.Job? = null

    override fun onPause() {
        recurrenceJob?.cancel()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        recurrenceJob = lifecycleScope.launch {
            try {
                val repository = (application as Arachn0deApplication).nodeRepository.recurrence
                while (repository.materializeDue()) kotlinx.coroutines.yield()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@MainActivity, "No se pudieron generar las recurrencias. Se reintentará al volver a abrir.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

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
