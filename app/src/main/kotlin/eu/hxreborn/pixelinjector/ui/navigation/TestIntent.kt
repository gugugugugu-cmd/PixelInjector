package eu.hxreborn.pixelinjector.ui.navigation

import android.content.Intent
import eu.hxreborn.pixelinjector.BuildConfig

const val EXTRA_NAV = "nav"
const val EXTRA_SCROLL = "scroll"
const val EXTRA_MENU = "menu"
const val EXTRA_BETTERFOX = "betterfox"
private const val ICERAVEN = "io.github.forkmaintainers.iceraven"

class TestNavigation(
    val destinations: List<Destination>,
    val scrollTo: String?,
    val openMenu: Boolean,
    val openInfo: Boolean,
)

fun testNavigation(intent: Intent?): TestNavigation? {
    if (!BuildConfig.TEST_INTENTS || intent == null) return null
    val nav = intent.getStringExtra(EXTRA_NAV) ?: return null
    val destinations =
        nav.split(',').filter { it.isNotBlank() }.map { token ->
            val name = token.substringBefore('=')
            val argument = token.substringAfter('=', "")
            when (name) {
                "betterfox" -> Destination.BetterfoxEditor(argument.ifEmpty { ICERAVEN })
                "betterfox.backup" -> Destination.BetterfoxBackup
                "betterfox.restore" -> Destination.BetterfoxRestore
                "betterfox.pref" -> Destination.BetterfoxPref(argument)
                "betterfox.confirm" -> Destination.BetterfoxRestoreConfirm(argument)
                "settings" -> Destination.Settings
                else -> error("unknown nav token $token")
            }
        }
    return TestNavigation(
        destinations,
        intent.getStringExtra(EXTRA_SCROLL),
        intent.getBooleanExtra(EXTRA_MENU, false),
        intent.getBooleanExtra("info", false),
    )
}

fun testBetterfoxCommand(intent: Intent?): String? = if (BuildConfig.TEST_INTENTS) intent?.getStringExtra(EXTRA_BETTERFOX) else null
