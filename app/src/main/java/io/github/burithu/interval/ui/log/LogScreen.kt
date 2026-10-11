package io.github.burithu.interval.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.db.EventLogEntity
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
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text("←", style = t.screenTitle, color = c.fg, modifier = Modifier.clickable(role = Role.Button, onClick = onBack).padding(Spacing.m))
            Text("EVENT LOG", style = t.sheetTitle, color = c.fg)
        }
        LazyColumn(Modifier.padding(horizontal = Spacing.screen)) {
            items(rows, key = { it.first.id }) { (e, label) ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
                    Row {
                        Text(stamp.format(e.at.atZone(zone)), style = t.meta, color = c.muted, modifier = Modifier.weight(1f))
                        Text(e.type.name, style = t.fieldLabel, color = c.accent)
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
