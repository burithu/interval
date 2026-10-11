package io.github.burithu.interval.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.AppSettings
import io.github.burithu.interval.ui.BottomBar
import io.github.burithu.interval.ui.SmallButton
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Screen 01. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).statusBarsPadding().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        ) {
            Text("INTERVAL", style = t.sheetTitle, color = c.fg)
            Text(
                buildAnnotatedString {
                    append("Get up.\n")
                    withStyle(SpanStyle(color = c.accent)) { append("Every") }
                    append("\nhour.")
                },
                style = t.display,
                color = c.fg,
            )
            Text(
                "Repeating reminders that restart themselves. For water, walks, stretches and food.",
                style = t.body,
                color = c.muted,
                modifier = Modifier.width(300.dp),
            )
        }
        BottomBar("Get started", onStart)
    }
}

enum class SetupStage { PERMISSIONS, RELIABILITY }

/** Screens 02 and 03. Re-checks every step whenever the user comes back from Settings. */
@Composable
fun SetupScreen(container: AppContainer, stage: SetupStage, onNext: () -> Unit, onSkip: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val steps = remember(refresh, settings.confirmedSteps) {
        when (stage) {
            SetupStage.PERMISSIONS -> SetupChecks.permissionSteps(context)
            SetupStage.RELIABILITY -> SetupChecks.reliabilitySteps(context, settings.confirmedSteps)
        }
    }
    var dialog by remember { mutableStateOf<String?>(null) }

    val onAction: (SetupStep) -> Unit = { step ->
        when (val a = step.action) {
            StepAction.RequestNotifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            is StepAction.OpenSettings -> if (!SetupChecks.open(context, a)) {
                dialog = "This phone doesn't have that settings page. Open Settings → Apps → Interval and look for \"${step.title}\"."
            }
            is StepAction.Instructions -> dialog = a.text
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.weight(1f).statusBarsPadding().padding(horizontal = Spacing.screen)) {
            Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val label = when (stage) {
                    SetupStage.PERMISSIONS -> "Step 1 of 2"
                    SetupStage.RELIABILITY -> if (SetupChecks.isXiaomi) "Step 2 of 2 · Xiaomi detected" else "Step 2 of 2"
                }
                Text(label.uppercase(), style = t.fieldLabel, color = c.muted)
                Text(
                    if (stage == SetupStage.PERMISSIONS) "PERMISSIONS" else "MAKE REMINDERS RELIABLE",
                    style = t.screenTitle, color = c.fg,
                )
                if (stage == SetupStage.RELIABILITY) {
                    Text(
                        if (SetupChecks.isXiaomi) "Xiaomi phones stop background apps to save battery and hide alarms from new apps. These settings keep Interval working."
                        else "Some phones delay background apps to save battery.",
                        style = t.body, color = c.muted,
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(steps.size, key = { steps[it].id }) { i ->
                    StepRow(
                        number = i + 1,
                        step = steps[i],
                        onAction = { onAction(steps[i]) },
                        onConfirm = { scope.launch { container.settings.setStepConfirmed(steps[i].id, true) } },
                    )
                    HorizontalDivider(color = c.line)
                }
            }
            Text(
                "Skip for now",
                style = t.body.copy(textDecoration = TextDecoration.Underline),
                color = c.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(role = Role.Button, onClick = onSkip).padding(Spacing.l),
            )
        }
        BottomBar(if (stage == SetupStage.PERMISSIONS) "Continue" else "Done", onNext)
    }

    dialog?.let { text ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }
}

@Composable
private fun StepRow(number: Int, step: SetupStep, onAction: () -> Unit, onConfirm: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("$number", style = t.numeral, color = c.accent, modifier = Modifier.width(Spacing.screen))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title, style = t.reminderName, color = c.fg, modifier = Modifier.weight(1f))
                if (step.status == StepStatus.DONE) {
                    Text("✓ DONE", style = t.fieldLabel, color = c.accent)
                } else {
                    SmallButton(step.actionLabel, onAction)
                }
            }
            Text(step.reason, style = t.meta, color = c.muted)
            if (step.status == StepStatus.UNKNOWN) {
                Text(
                    "This phone can't confirm it. Mark done once it's on.",
                    style = t.meta, color = c.muted,
                )
                SmallButton("Mark done", onConfirm, color = c.accent)
            }
        }
    }
}
