package eu.hxreborn.pixelinjector.ui.dashboard

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EditNotifications
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.KDE_CONNECT_PACKAGE
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.prefs.StringPref
import eu.hxreborn.pixelinjector.ui.navigation.Destination

enum class TweakGroup {
    SYSTEM_UI,
    SYSTEM,
    LAUNCHER,
    GBOARD,
    DIALER,
    FILES,
    KDE_CONNECT,
    GITHUB,
}

class TweakEditor(
    val title: Int,
    val description: Int?,
    val destination: Destination,
    val summary: (AppPrefs) -> String,
)

class TweakSlider(
    val title: Int,
    val description: Int,
    val pref: IntPref,
)

class TweakSound(
    val title: Int,
    val pref: StringPref,
    val destination: Destination,
)

class TweakUi(
    val key: String,
    val pref: BoolPref,
    val title: Int,
    val description: Int,
    val group: TweakGroup,
    val editor: TweakEditor? = null,
    val slider: TweakSlider? = null,
    val sound: TweakSound? = null,
    val minSdk: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)

val tweakUis: List<TweakUi> =
    listOf(
        TweakUi("WarmTiles", Prefs.WARM_TILES, R.string.tweak_warm_tiles, R.string.tweak_warm_tiles_desc, TweakGroup.SYSTEM_UI),
        TweakUi(
            "PillShot",
            Prefs.PILL_SHOT,
            R.string.tweak_pill_shot,
            R.string.tweak_pill_shot_desc,
            TweakGroup.SYSTEM_UI,
            editor =
                TweakEditor(
                    R.string.tweak_pill_shot_apps,
                    null,
                    Destination.PillShotApps,
                ) { it[Prefs.PILL_SHOT_APPS].size.toString() },
        ),
        TweakUi(
            "ScreenshotSound",
            Prefs.SCREENSHOT_SOUND,
            R.string.tweak_screenshot_sound,
            R.string.tweak_screenshot_sound_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "EmptyShade",
            Prefs.EMPTY_SHADE,
            R.string.tweak_empty_shade,
            R.string.tweak_empty_shade_desc,
            TweakGroup.SYSTEM_UI,
            editor = TweakEditor(R.string.tweak_empty_shade_edit, null, Destination.EmptyShadeEditor) { it[Prefs.EMPTY_SHADE_TEXT] },
            minSdk = Build.VERSION_CODES.CINNAMON_BUN,
        ),
        TweakUi(
            "ExpandNotifications",
            Prefs.EXPAND_NOTIFICATIONS,
            R.string.tweak_expand_notifications,
            R.string.tweak_expand_notifications_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "OngoingNoDismiss",
            Prefs.ONGOING_NO_DISMISS,
            R.string.tweak_ongoing_no_dismiss,
            R.string.tweak_ongoing_no_dismiss_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "StatusBarDoubleTap",
            Prefs.STATUS_BAR_DOUBLE_TAP,
            R.string.tweak_status_bar_double_tap,
            R.string.tweak_status_bar_double_tap_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "BiometricBypass",
            Prefs.BIOMETRIC_BYPASS,
            R.string.tweak_biometric_bypass,
            R.string.tweak_biometric_bypass_desc,
            TweakGroup.SYSTEM_UI,
            sound = TweakSound(R.string.tweak_biometric_sound, Prefs.BIOMETRIC_SOUND, Destination.SoundEditor),
        ),
        TweakUi(
            "ForceNewTask",
            Prefs.NEW_TASK,
            R.string.tweak_new_task,
            R.string.tweak_new_task_desc,
            TweakGroup.SYSTEM,
            editor =
                TweakEditor(R.string.tweak_new_task_rules, R.string.tweak_new_task_rules_desc, Destination.RulesEditor) { prefs ->
                    prefs[Prefs.NEW_TASK_RULES].lines().count { it.isNotBlank() }.toString()
                },
        ),
        TweakUi(
            "ClipboardAllowlist",
            Prefs.CLIPBOARD,
            R.string.tweak_clipboard,
            R.string.tweak_clipboard_desc,
            TweakGroup.SYSTEM,
            editor = TweakEditor(R.string.tweak_clipboard_apps, null, Destination.AppsEditor) { it[Prefs.CLIPBOARD_APPS].size.toString() },
        ),
        TweakUi(
            "DoubleTapToSleep",
            Prefs.DOUBLE_TAP_TO_SLEEP,
            R.string.tweak_double_tap_sleep,
            R.string.tweak_double_tap_sleep_desc,
            TweakGroup.LAUNCHER,
        ),
        TweakUi(
            "TaskbarHandle",
            Prefs.HIDE_PILL,
            R.string.tweak_hide_pill,
            R.string.tweak_hide_pill_desc,
            TweakGroup.LAUNCHER,
            slider = TweakSlider(R.string.tweak_nav_space, R.string.tweak_nav_space_desc, Prefs.NAV_SPACE),
        ),
        TweakUi(
            "HideSearchBar",
            Prefs.HIDE_SEARCH_BAR,
            R.string.tweak_hide_search_bar,
            R.string.tweak_hide_search_bar_desc,
            TweakGroup.LAUNCHER,
        ),
        TweakUi("GboardBlack", Prefs.GBOARD_BLACK, R.string.tweak_gboard_black, R.string.tweak_gboard_black_desc, TweakGroup.GBOARD),
        TweakUi("DialerDechip", Prefs.DIALER_DECHIP, R.string.tweak_dialer_dechip, R.string.tweak_dialer_dechip_desc, TweakGroup.DIALER),
        TweakUi(
            "FilesSortByDate",
            Prefs.FILES_SORT_BY_DATE,
            R.string.tweak_files_sort_by_date,
            R.string.tweak_files_sort_by_date_desc,
            TweakGroup.FILES,
        ),
        TweakUi(
            "KdeConnectQuiet",
            Prefs.KDE_CONNECT_QUIET,
            R.string.tweak_kde_connect_quiet,
            R.string.tweak_kde_connect_quiet_desc,
            TweakGroup.KDE_CONNECT,
        ),
        TweakUi("GhFastPass", Prefs.GH_FAST_PASS, R.string.tweak_gh_fast_pass, R.string.tweak_gh_fast_pass_desc, TweakGroup.GITHUB),
    ).filter { Build.VERSION.SDK_INT >= it.minSdk }

val restartOnChange: Map<PrefSpec<*>, Set<String>> =
    mapOf(
        Prefs.HIDE_SEARCH_BAR to LAUNCHER_PACKAGES,
        Prefs.NAV_SPACE to LAUNCHER_PACKAGES,
        Prefs.GBOARD_BLACK to GBOARD_PACKAGES,
        Prefs.KDE_CONNECT_QUIET to setOf(KDE_CONNECT_PACKAGE),
    )

private val moduleIcons: Map<String, Int> =
    mapOf(
        "WarmTiles" to R.drawable.ic_tweak_warm_tiles,
        "BiometricBypass" to R.drawable.ic_tweak_biometric_bypass,
        "DialerDechip" to R.drawable.ic_tweak_dialer_dechip,
        "FilesSortByDate" to R.drawable.ic_tweak_files_sort,
        "GhFastPass" to R.drawable.ic_github,
    )

private val tweakSymbols: Map<String, ImageVector> =
    mapOf(
        "PillShot" to Icons.Outlined.Screenshot,
        "ScreenshotSound" to Icons.Outlined.VolumeOff,
        "EmptyShade" to Icons.Outlined.EditNotifications,
        "ExpandNotifications" to Icons.Outlined.UnfoldMore,
        "OngoingNoDismiss" to Icons.Outlined.PushPin,
        "StatusBarDoubleTap" to Icons.Outlined.TouchApp,
        "ForceNewTask" to Icons.Outlined.Layers,
        "ClipboardAllowlist" to Icons.Outlined.ContentPaste,
        "DoubleTapToSleep" to Icons.Outlined.Bedtime,
        "TaskbarHandle" to Icons.Outlined.HorizontalRule,
        "HideSearchBar" to Icons.Outlined.SearchOff,
        "GboardBlack" to Icons.Outlined.DarkMode,
        "KdeConnectQuiet" to Icons.Outlined.WifiOff,
    )

@Composable
fun tweakSymbol(key: String): ImageVector? = moduleIcons[key]?.let { ImageVector.vectorResource(it) } ?: tweakSymbols[key]

val groupSymbols: Map<TweakGroup, ImageVector> =
    mapOf(
        TweakGroup.SYSTEM_UI to Icons.Outlined.PhoneAndroid,
        TweakGroup.SYSTEM to Icons.Outlined.Android,
        TweakGroup.LAUNCHER to Icons.Outlined.Home,
        TweakGroup.GBOARD to Icons.Outlined.Keyboard,
        TweakGroup.DIALER to Icons.Outlined.Call,
        TweakGroup.FILES to Icons.Outlined.Folder,
        TweakGroup.KDE_CONNECT to Icons.Outlined.Devices,
        TweakGroup.GITHUB to Icons.Outlined.Code,
    )
