package io.github.ewoc2026.visualroutines

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider

/** Hosts the Compose application as the single Android entry point. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val viewModel = ViewModelProvider(
            this,
            VisualRoutinesViewModel.Factory(applicationContext),
        )[VisualRoutinesViewModel::class.java]
        setContent {
            VisualRoutinesApp(viewModel.state)
        }
    }
}
