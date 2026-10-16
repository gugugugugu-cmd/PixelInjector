package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "HideAppShortcuts"
private const val POPUP = "com.android.launcher3.popup.PopupContainerWithArrow"
private const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
private const val BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"

private val switch = Switch(Prefs.HIDE_APP_SHORTCUTS)

internal val hideAppShortcuts =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installHideAppShortcuts,
    )

private class PopupBindings(
    val showRows: Method,
    val countIndex: Int,
    val loadAppShortcuts: Method?,
) {
    companion object {
        fun resolve(cl: ClassLoader): PopupBindings {
            val popup = cl.loadClass(POPUP)
            val itemInfo = cl.loadClass(ITEM_INFO)
            val iconRows =
                runCatching {
                    popup.requiredMethod(
                        "populateAndShowRows",
                        cl.loadClass(BUBBLE_TEXT_VIEW),
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }.getOrNull()
            if (iconRows != null) return PopupBindings(iconRows, 1, null)
            val showRows =
                runCatching {
                    popup.requiredMethod(
                        "showComposePopup",
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }.getOrElse {
                    popup.requiredMethod(
                        "populateAndShowRows",
                        itemInfo,
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }
            return PopupBindings(
                showRows = showRows,
                countIndex = showRows.parameterTypes.indexOf(Int::class.javaPrimitiveType),
                loadAppShortcuts =
                    popup.requiredMethod(
                        "loadAppShortcuts",
                        itemInfo,
                    ),
            )
        }
    }
}

private fun XposedModule.installHideAppShortcuts(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { PopupBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val members = listOfNotNull(bindings.showRows, bindings.loadAppShortcuts)
    Logger.info("resolved tweak=$TWEAK members=${members.joinToString(",") { it.signature() }}")
    hook(bindings.showRows).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        chain.proceed(chain.args.toTypedArray<Any?>().also { it[bindings.countIndex] = 0 })
    }
    bindings.loadAppShortcuts?.let { loadAppShortcuts ->
        hook(loadAppShortcuts).intercept { chain ->
            if (switch.enabled) null else chain.proceed()
        }
    }
    return true
}
