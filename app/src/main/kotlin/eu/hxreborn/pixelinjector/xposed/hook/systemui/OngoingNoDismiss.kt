package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import android.service.notification.StatusBarNotification
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule

private const val TWEAK = "OngoingNoDismiss"

private val switch = Switch(Prefs.ONGOING_NO_DISMISS)

internal val ongoingNoDismiss =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installOngoingNoDismiss,
    )

private fun XposedModule.installOngoingNoDismiss(cl: ClassLoader): Boolean {
    val isNonDismissable =
        runCatching {
            StatusBarNotification::class.java.requiredMethod(
                "isNonDismissable",
            )
        }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${isNonDismissable.signature()}")
    hook(isNonDismissable).intercept { chain ->
        val result = chain.proceed()
        if (!switch.enabled || result == true) return@intercept result
        (chain.thisObject as? StatusBarNotification)?.isOngoing == true
    }
    return true
}
