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
 * Phone makers whose battery managers stop background apps beyond standard Android (D36).
 * [guide] is the maker's page on dontkillmyapp.com. Only Xiaomi is device-tested; the others'
 * settings pages are best-known component names, each falling back to standard settings.
 */
enum class Maker(val label: String?, val brands: List<String>, val guide: String?, val intro: String) {
    XIAOMI(
        "Xiaomi", listOf("xiaomi", "redmi", "poco"), "xiaomi",
        "Xiaomi phones stop background apps to save battery and hide alarms from new apps. These settings keep Interval working.",
    ),
    SAMSUNG(
        "Samsung", listOf("samsung"), "samsung",
        "Samsung phones put unused apps to sleep, which stops their reminders. These settings keep Interval awake.",
    ),
    OPPO(
        "OnePlus / Oppo / Realme", listOf("oneplus", "oppo", "realme"), "oneplus",
        "This phone stops background apps that aren't allowed to auto-launch. These settings keep Interval working.",
    ),
    VIVO(
        "Vivo", listOf("vivo", "iqoo"), "vivo",
        "Vivo phones stop background apps to save battery. These settings keep Interval working.",
    ),
    HUAWEI(
        "Huawei / Honor", listOf("huawei", "honor"), "huawei",
        "This phone closes apps it manages automatically. These settings keep Interval working.",
    ),
    ASUS(
        "Asus", listOf("asus"), "asus",
        "Asus phones block apps from starting in the background. These settings keep Interval working.",
    ),
    OTHER(null, emptyList(), null, "Some phones delay background apps to save battery."),
    ;

    val guideUrl: String? get() = guide?.let { "https://dontkillmyapp.com/$it" }

    companion object {
        fun detect(manufacturer: String, brand: String): Maker = entries.firstOrNull { m ->
            m.brands.any { it.equals(manufacturer, true) || it.equals(brand, true) }
        } ?: OTHER
    }
}

/**
 * Everything Interval needs from the phone (SPEC §7, HyperOS findings). Each step reports
 * whether it is done and how to fix it, with fallbacks when a vendor settings page is missing.
 */
object SetupChecks {

    val maker: Maker by lazy { Maker.detect(Build.MANUFACTURER, Build.BRAND) }

    val isXiaomi: Boolean get() = maker == Maker.XIAOMI

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
            reason = when (maker) {
                Maker.XIAOMI -> "Set to \"No restrictions\" so HyperOS doesn't stop reminders."
                Maker.SAMSUNG -> "Set battery to \"Unrestricted\" so reminders aren't delayed."
                Maker.OPPO -> "Allow background activity and turn off battery optimisation for Interval."
                Maker.VIVO -> "Allow high background power use for Interval."
                else -> "Let Interval run unrestricted so reminders aren't delayed."
            },
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
        val recents = SetupStep(
            id = "recents_lock", title = "Lock in recent apps",
            reason = "Optional. Stops \"clear all\" in recent apps from closing Interval.",
            status = if ("recents_lock" in confirmed) StepStatus.DONE else StepStatus.UNKNOWN,
            action = StepAction.Instructions(
                "Open recent apps, then long-press Interval's card (or tap its menu) and choose Lock or Keep open.",
            ),
            actionLabel = "How",
            required = false,
        )

        // Vendor pages the phone can't report on: the user opens them and marks the step done.
        fun manual(id: String, title: String, reason: String, vararg components: Pair<String, String>) = SetupStep(
            id = id, title = title, reason = reason,
            status = if (id in confirmed) StepStatus.DONE else StepStatus.UNKNOWN,
            action = StepAction.OpenSettings(components.map { (p, c) -> Intent().setClassName(p, c) } + details),
            actionLabel = "Open",
        )

        return when (maker) {
            Maker.XIAOMI -> xiaomiSteps(context, confirmed, battery)
            Maker.SAMSUNG -> listOf(
                battery,
                manual(
                    "never_sleeping", "Never sleeping apps",
                    "Battery → Background usage limits → Never sleeping apps → add Interval. Make sure it isn't under Sleeping or Deep sleeping apps.",
                    "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
                    "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                    "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                ),
                recents,
            )
            Maker.OPPO -> listOf(
                manual(
                    "autostart", "Auto launch",
                    "Turn on Auto launch for Interval so it restores reminders after a reboot.",
                    "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
                    "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                    "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
                battery,
                recents,
            )
            Maker.VIVO -> listOf(
                manual(
                    "autostart", "Autostart",
                    "Allow Interval to start in the background so it restores reminders after a reboot.",
                    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                    "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                ),
                battery,
                recents,
            )
            Maker.HUAWEI -> listOf(
                manual(
                    "autostart", "App launch",
                    "Turn off \"Manage automatically\" for Interval, then keep Auto-launch, Secondary launch and Run in background on.",
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
                    "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                battery,
                recents,
            )
            Maker.ASUS -> listOf(
                manual(
                    "autostart", "Auto-start",
                    "Allow Interval in Auto-start Manager so it restores reminders after a reboot.",
                    "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
                    "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
                ),
                battery,
                recents,
            )
            Maker.OTHER -> listOf(battery)
        }
    }

    private fun xiaomiSteps(context: Context, confirmed: Set<String>, battery: SetupStep): List<SetupStep> {
        val pkg = context.packageName
        val details = appDetails(context)
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
