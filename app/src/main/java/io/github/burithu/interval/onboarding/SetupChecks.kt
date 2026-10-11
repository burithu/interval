package io.github.burithu.interval.onboarding

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

enum class StepStatus {
    DONE,

    /** Detected as off. Counts toward the Home warning banner if the step is required. */
    NEEDED,

    /** Cannot be detected on this phone. The user confirms it by hand. */
    UNKNOWN,
}

sealed interface StepAction {
    data object RequestNotifications : StepAction
    data class OpenSettings(val intents: List<Intent>) : StepAction
    data class Instructions(val text: String) : StepAction
}

data class SetupStep(
    val id: String,
    val title: String,
    val reason: String,
    val status: StepStatus,
    val action: StepAction,
    val actionLabel: String,
    val required: Boolean = true,
)

/**
 * Everything Interval needs from the phone (SPEC §7, HyperOS findings). Each step reports
 * whether it is done and how to fix it, with fallbacks when a vendor settings page is missing.
 */
object SetupChecks {

    val isXiaomi: Boolean
        get() = listOf(Build.MANUFACTURER, Build.BRAND).any {
            it.equals("xiaomi", true) || it.equals("redmi", true) || it.equals("poco", true)
        }

    fun permissionSteps(context: Context): List<SetupStep> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                SetupStep(
                    id = "notifications", title = "Notifications",
                    reason = "So reminders can reach you.",
                    status = done(
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED,
                    ),
                    action = StepAction.RequestNotifications, actionLabel = "Allow",
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                SetupStep(
                    id = "exact_alarms", title = "Exact alarms",
                    reason = "So reminders ring on time, not 10 minutes late.",
                    status = done(context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()),
                    action = settings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackageUri = true),
                    actionLabel = "Allow",
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                SetupStep(
                    id = "full_screen", title = "Full-screen alarms",
                    reason = "So Alarm-style reminders can take over the screen.",
                    status = done(context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()),
                    action = settings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, withPackageUri = true),
                    actionLabel = "Allow",
                ),
            )
        }
        add(
            SetupStep(
                id = "overlay", title = "Display over other apps",
                reason = "So the alarm screen can open while the phone is locked.",
                status = done(Settings.canDrawOverlays(context)),
                action = settings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackageUri = true),
                actionLabel = "Allow",
            ),
        )
    }

    /** [confirmed] holds ids of steps the user marked done by hand. */
    fun reliabilitySteps(context: Context, confirmed: Set<String>): List<SetupStep> {
        val pkg = context.packageName
        val details = appDetails(context)
        val battery = SetupStep(
            id = "battery", title = "Battery saver",
            reason = if (isXiaomi) "Set to \"No restrictions\" so HyperOS doesn't stop reminders."
            else "Let Interval run unrestricted so reminders aren't delayed.",
            status = done(context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)),
            action = StepAction.OpenSettings(
                listOfNotNull(
                    if (isXiaomi) Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                        .putExtra("package_name", pkg).putExtra("package_label", "Interval") else null,
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    details,
                ),
            ),
            actionLabel = "Open",
        )
        if (!isXiaomi) return listOf(battery)

        val lockAndPopups = listOf(miuiOp(context, OP_SHOW_WHEN_LOCKED), miuiOp(context, OP_BACKGROUND_START))
        return listOf(
            SetupStep(
                id = "autostart", title = "Autostart",
                reason = "Lets Interval restore its reminders after a reboot or update.",
                status = miuiOp(context, OP_AUTOSTART).toStatus("autostart" in confirmed),
                action = StepAction.OpenSettings(
                    listOf(
                        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                        details,
                    ),
                ),
                actionLabel = "Open",
            ),
            battery,
            SetupStep(
                id = "lock_screen", title = "Show on lock screen",
                reason = "Turn on \"Show on Lock screen\" and \"Display pop-up windows while running in the background\". The alarm screen needs both.",
                status = when {
                    lockAndPopups.all { it == true } -> StepStatus.DONE
                    lockAndPopups.any { it == false } -> StepStatus.NEEDED
                    "lock_screen" in confirmed -> StepStatus.DONE
                    else -> StepStatus.UNKNOWN
                },
                action = StepAction.OpenSettings(
                    listOf(
                        Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", pkg),
                        Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                            .putExtra("extra_pkgname", pkg),
                        details,
                    ),
                ),
                actionLabel = "Open",
            ),
            SetupStep(
                id = "notification_popups", title = "Notification pop-ups",
                reason = "Turn on \"Floating notifications\" and \"Lock screen notifications\" so reminders appear, not just sound.",
                status = if ("notification_popups" in confirmed) StepStatus.DONE else StepStatus.UNKNOWN,
                action = StepAction.OpenSettings(
                    listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), details),
                ),
                actionLabel = "Open",
            ),
            SetupStep(
                id = "recents_lock", title = "Lock in recent apps",
                reason = "Optional. Open recent apps, then long-press Interval and tap the lock.",
                status = if ("recents_lock" in confirmed) StepStatus.DONE else StepStatus.UNKNOWN,
                action = StepAction.Instructions("Open recent apps, long-press Interval, and tap the lock icon."),
                actionLabel = "How",
                required = false,
            ),
        )
    }

    /** Required steps detected as off. Drives the Home warning banner (screen 07). */
    fun missingRequired(context: Context, confirmed: Set<String>): List<SetupStep> =
        (permissionSteps(context) + reliabilitySteps(context, confirmed))
            .filter { it.required && it.status == StepStatus.NEEDED }

    /** Opens the first settings page this phone has. Returns false if none could be opened. */
    fun open(context: Context, action: StepAction.OpenSettings): Boolean {
        for (intent in action.intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private fun settings(context: Context, action: String, withPackageUri: Boolean) = StepAction.OpenSettings(
        listOf(
            Intent(action).apply { if (withPackageUri) data = "package:${context.packageName}".toUri() },
            Intent(action),
            appDetails(context),
        ),
    )

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    private fun done(ok: Boolean) = if (ok) StepStatus.DONE else StepStatus.NEEDED

    private fun Boolean?.toStatus(confirmed: Boolean) = when (this) {
        true -> StepStatus.DONE
        false -> StepStatus.NEEDED
        null -> if (confirmed) StepStatus.DONE else StepStatus.UNKNOWN
    }

    /**
     * Reads a Xiaomi-private app op. Returns null if the phone does not allow reading it,
     * in which case the step falls back to manual confirmation.
     */
    private fun miuiOp(context: Context, op: Int): Boolean? = try {
        val method = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
        )
        val mode = method.invoke(context.getSystemService(AppOpsManager::class.java), op, Process.myUid(), context.packageName) as Int
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        null
    }

    // MIUI / HyperOS private app ops.
    private const val OP_AUTOSTART = 10008
    private const val OP_SHOW_WHEN_LOCKED = 10020
    private const val OP_BACKGROUND_START = 10021
}
