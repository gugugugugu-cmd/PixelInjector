package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "HeadsUpDuration"
private const val MANAGER = "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl"
private const val BASE_MANAGER = "com.android.systemui.statusbar.policy.BaseHeadsUpManager"
private const val MS_PER_SECOND = 1000
private const val STATE_MANAGER = "manager"
private const val STATE_AUTO = "auto"
private const val STATE_MINIMUM = "minimum"

@Volatile private var current: HeadsUpBindings? = null

private val switch = Switch(Prefs.HEADS_UP_DURATION) { current?.apply() }
private val seconds =
    Value(Prefs.HEADS_UP_SECONDS, onLoad = { current?.apply() }) {
        it.coerceIn(Prefs.HEADS_UP_SECONDS_MIN, Prefs.HEADS_UP_SECONDS_MAX)
    }

internal val headsUpDuration =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, seconds),
        install = XposedModule::installHeadsUpDuration,
        saveState = { current?.save() },
        restoreState = { saved -> (saved as? Map<*, *>)?.let { current?.restore(it) } },
    )

private class HeadsUpBindings(
    val autoDismiss: Field,
    val minimum: Field,
    val createEntry: List<Method>,
) {
    @Volatile private var live: WeakReference<Any>? = null

    @Volatile private var stockAuto = 0

    @Volatile private var stockMinimum = 0

    fun capture(manager: Any) {
        if (live?.get() === manager) return
        stockAuto = autoDismiss.getInt(manager)
        stockMinimum = minimum.getInt(manager)
        live = WeakReference(manager)
        apply()
    }

    fun apply() {
        val manager = live?.get() ?: return
        runCatching {
            val auto = if (switch.enabled) seconds.value * MS_PER_SECOND else stockAuto
            autoDismiss.setInt(manager, auto)
            minimum.setInt(manager, minOf(stockMinimum, auto))
            Logger.info("applied tweak=$TWEAK autoDismissMs=$auto")
        }.onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
    }

    fun save(): HashMap<String, Any>? {
        val manager = live?.get() ?: return null
        return hashMapOf(
            STATE_MANAGER to manager,
            STATE_AUTO to stockAuto,
            STATE_MINIMUM to stockMinimum,
        )
    }

    fun restore(saved: Map<*, *>) {
        val manager = saved[STATE_MANAGER]
        val auto = saved[STATE_AUTO] as? Int
        val minimum = saved[STATE_MINIMUM] as? Int
        if (manager == null || auto == null || minimum == null) {
            return Logger.info("restore skipped tweak=$TWEAK reason=no-live")
        }
        stockAuto = auto
        stockMinimum = minimum
        live = WeakReference(manager)
        Logger.info("carried tweak=$TWEAK autoDismissMs=$stockAuto")
        apply()
    }

    companion object {
        fun resolve(cl: ClassLoader): HeadsUpBindings {
            val manager =
                resolving {
                    cl.loadClass(
                        MANAGER,
                    )
                }.getOrElse { cl.loadClass(BASE_MANAGER) }
            return HeadsUpBindings(
                autoDismiss = manager.requiredField("mAutoDismissTime"),
                minimum =
                    manager.requiredField(
                        "mMinimumDisplayTimeDefault",
                        "mMinimumDisplayTime",
                    ),
                createEntry =
                    resolving { manager.requiredMethods("createHeadsUpEntry") }
                        .getOrElse { manager.requiredMethods("showNotification") },
            )
        }
    }
}

private fun XposedModule.installHeadsUpDuration(cl: ClassLoader): Boolean {
    val bindings =
        resolving { HeadsUpBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.autoDismiss.signature()},${bindings.minimum.signature()}",
    )
    current = bindings
    for (constructor in bindings.autoDismiss.declaringClass.declaredConstructors) {
        hook(constructor).intercept { chain ->
            val result = chain.proceed()
            runCatching { chain.thisObject?.let(bindings::capture) }
                .onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
            result
        }
    }
    for (method in bindings.createEntry) {
        hook(method).intercept { chain ->
            runCatching { chain.thisObject?.let(bindings::capture) }
                .onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
            chain.proceed()
        }
    }
    return true
}
