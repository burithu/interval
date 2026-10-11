package io.github.burithu.interval

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntervalTheme {
                SetupCheckScreen()
            }
        }
    }
}

/** M0 placeholder: proves the theme tokens and Archivo widths render. Replaced in M2. */
@Composable
private fun SetupCheckScreen() {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("REMINDERS", style = t.screenTitle, color = c.fg)
        Text("NEXT UP", style = t.fieldLabel, color = c.muted)
        Text("12 min", style = t.countdown, color = c.accent)
        Text("Drink water", style = t.reminderName, color = c.fg)
        Text("Rings at 13:30 · then every 45 min", style = t.meta, color = c.muted)
    }
}
