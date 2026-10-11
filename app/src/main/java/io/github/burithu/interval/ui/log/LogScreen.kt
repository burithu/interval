package io.github.burithu.interval.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.db.EventLogEntity
import io.github.burithu.interval.ui.Glyph
import io.github.burithu.interval.ui.TopBar
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogViewModel(c: AppContainer) : ViewModel() {
    /** Log rows paired with reminder labels (null once a reminder is deleted). */
    val rows: StateFlow<List<Pair<EventLogEntity, String?>>> = combine(
        c.db.logDao().observeRecent(),
        c.db.reminderDao().observeAll(),
    ) { events, reminders ->
        val labels = reminders.associate { it.id to it.label }
        events.map { it to it.reminderId?.let(labels::get) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val stamp = DateTimeFormatter.ofPattern("EEE HH:mm:ss")
private val short = DateTimeFormatter.ofPattern("EEE HH:mm")

/** Debug event log (D28). Newest first. */
@Composable
fun LogScreen(vm: LogViewModel, onBack: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val rows by vm.rows.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar("EVENT LOG", Glyph.BACK, "Back", onBack)
        LazyColumn(Modifier.padding(horizontal = Spacing.screen)) {
            items(rows, key = { it.first.id }) { (e, label) ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
                    Row {
                        Text(stamp.format(e.at.atZone(zone)), style = t.meta, color = c.muted, modifier = Modifier.weight(1f))
                        Text(e.type.name.replace('_', ' '), style = t.fieldLabel, color = c.accent)
                    }
                    val parts = listOfNotNull(
                        label ?: e.reminderId?.let { "#$it" },
                        e.scheduledFor?.let { "→ ${short.format(it.atZone(zone))}" },
                        e.detail,
                    )
                    if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), style = t.body, color = c.fg)
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}
