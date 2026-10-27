package eu.hxreborn.pixelinjector

import eu.hxreborn.pixelinjector.ModuleConstants.DIALER_PACKAGE
import eu.hxreborn.pixelinjector.ModuleConstants.FILES_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.FIREFOX_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GITHUB_PACKAGE
import eu.hxreborn.pixelinjector.ModuleConstants.KDE_CONNECT_PACKAGE
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE

internal enum class Target {
    SYSTEM,
    SYSTEMUI,
    SCREENSHOT,
    LAUNCHER,
    GBOARD,
    DIALER,
    FILES,
    KDE_CONNECT,
    GITHUB,
    FIREFOX,
    ;

    companion object {
        fun of(
            process: String,
            systemServer: Boolean,
        ): Target? =
            when {
                systemServer -> SYSTEM
                process == SYSTEMUI_PACKAGE -> SYSTEMUI
                process == "$SYSTEMUI_PACKAGE:screenshot" -> SCREENSHOT
                process in LAUNCHER_PACKAGES -> LAUNCHER
                process in GBOARD_PACKAGES -> GBOARD
                process == DIALER_PACKAGE -> DIALER
                process in FILES_PACKAGES -> FILES
                process == KDE_CONNECT_PACKAGE -> KDE_CONNECT
                process == GITHUB_PACKAGE -> GITHUB
                process in FIREFOX_PACKAGES -> FIREFOX
                else -> null
            }
    }
}
