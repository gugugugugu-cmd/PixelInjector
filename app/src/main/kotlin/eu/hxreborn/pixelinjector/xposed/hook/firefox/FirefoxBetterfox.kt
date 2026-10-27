package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.betterfox.BETTERFOX_ASSET
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.chooseList
import eu.hxreborn.pixelinjector.betterfox.decodeOverrides
import eu.hxreborn.pixelinjector.betterfox.formatPrefValue
import eu.hxreborn.pixelinjector.betterfox.resolveBetterfox
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.zip.ZipFile

private const val TWEAK = "FirefoxBetterfox"
private const val RUNTIME_SETTINGS = "org.mozilla.geckoview.RuntimeSettings"
private const val GECKO_RUNTIME_SETTINGS = "org.mozilla.geckoview.GeckoRuntimeSettings"

private val bundled: BetterfoxList? by lazy { loadBundled() }

private val switch = Switch(Prefs.FIREFOX_BETTERFOX) { refresh() }
private val offGroups = Value(Prefs.FIREFOX_BETTERFOX_OFF_GROUPS, { refresh() }) { it }
private val overrides =
    Value(Prefs.FIREFOX_BETTERFOX_OVERRIDES, { refresh() }) { decodeOverrides(it) }
private val fetchedList =
    Value(Prefs.FIREFOX_BETTERFOX_LIST, { refresh() }) { BetterfoxList.parseOrNull(it) }

@Volatile private var appliedPrefs: Map<String, Any> = emptyMap()

internal val firefoxBetterfox =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch, offGroups, overrides, fetchedList),
        install = XposedModule::installFirefoxBetterfox,
    )

private class BetterfoxBindings(
    val getPrefsMap: Method,
    val settings: Class<*>,
) {
    companion object {
        fun resolve(cl: ClassLoader) =
            BetterfoxBindings(
                getPrefsMap = cl.loadClass(RUNTIME_SETTINGS).requiredMethod("getPrefsMap"),
                settings = cl.loadClass(GECKO_RUNTIME_SETTINGS),
            )
    }
}

private fun loadBundled(): BetterfoxList? =
    resolving {
        ZipFile(module.moduleApplicationInfo.sourceDir).use { zip ->
            val entry = checkNotNull(zip.getEntry("assets/$BETTERFOX_ASSET")) { "no-asset" }
            BetterfoxList.parse(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
        }
    }.getOrElse {
        Logger.error("bundled list failed tweak=$TWEAK reason=${it.message}", it)
        null
    }

private fun refresh() {
    val list = bundled?.let { chooseList(it, fetchedList.value) }
    appliedPrefs =
        if (switch.enabled && list != null) {
            resolveBetterfox(list, offGroups.value, overrides.value)
        } else {
            emptyMap()
        }
}

private fun XposedModule.installFirefoxBetterfox(cl: ClassLoader): Boolean {
    val bindings =
        resolving { BetterfoxBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.getPrefsMap.signature()}")
    hook(bindings.getPrefsMap).intercept { chain ->
        @Suppress("UNCHECKED_CAST")
        val stock = chain.proceed() as Map<String, Any?>
        val extra = appliedPrefs
        if (extra.isEmpty() || !bindings.settings.isInstance(chain.thisObject)) {
            stock
        } else {
            val replaced = extra.keys.filter { it in stock }.joinToString(",")
            val values =
                extra.entries.joinToString(",") { "${it.key}=${formatPrefValue(it.value)}" }
            Logger.info(
                "applied tweak=$TWEAK prefs=${extra.size} replaced=$replaced values=$values",
            )
            LinkedHashMap(stock).apply { putAll(extra) }
        }
    }
    return true
}
