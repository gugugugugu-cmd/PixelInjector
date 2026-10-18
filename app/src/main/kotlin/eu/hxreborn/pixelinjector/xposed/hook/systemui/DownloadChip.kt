package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.app.Notification
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

private const val TWEAK = "DownloadChip"
private const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
private const val LISTENER = "com.android.systemui.statusbar.NotificationListener"
private const val CLOCK = "com.android.systemui.statusbar.policy.Clock"
private const val ICON_AREA_ID = "notification_icon_area"
private const val START_SIDE_ID = "status_bar_start_side_except_heads_up"
private const val WINDOW_MANAGER_GLOBAL = "android.view.WindowManagerGlobal"
private const val STATE_CHIP = "chip"
private const val STATE_LISTENER = "listener"
private const val CHIP_WIDTH_DP = 56
private const val CHIP_PADDING_DP = 4
private const val CHIP_MARGIN_DP = 3
private const val ICON_SIZE_DP = 10
private const val ICON_GAP_DP = 3
private const val BAR_HEIGHT_DP = 3
private const val PILL_ALPHA = 0x33
private const val TRACK_ALPHA = 0x55
private const val ALPHA_SHIFT = 24
private const val ALPHA_MASK = 0x00FFFFFF

@Volatile private var chip: LinearLayout? = null

@Volatile private var listener: WeakReference<NotificationListenerService>? = null

@Volatile private var tint = Color.WHITE

private var tracked: String? = null

private val switch =
    Switch(Prefs.DOWNLOAD_CHIP) { resync() }

internal val downloadChip =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installDownloadChip,
        saveState = {
            hashMapOf<String, Any?>(STATE_CHIP to chip, STATE_LISTENER to listener?.get())
        },
        restoreState = { saved ->
            val map = saved as? Map<*, *>
            chip = map?.get(STATE_CHIP) as? LinearLayout
            (map?.get(STATE_LISTENER) as? NotificationListenerService)?.let {
                listener = WeakReference(it)
            }
            resync()
        },
    )

private class ChipBindings(
    val onFinishInflate: Method,
    val posted: Method,
    val removed: List<Method>,
    val connected: Method,
    val onDarkChanged: Method?,
) {
    companion object {
        fun resolve(cl: ClassLoader): ChipBindings {
            val listenerClass = cl.loadClass(LISTENER)
            return ChipBindings(
                onFinishInflate = cl.loadClass(STATUS_BAR_VIEW).requiredMethod("onFinishInflate"),
                posted =
                    listenerClass.requiredMethod(
                        "onNotificationPosted",
                        StatusBarNotification::class.java,
                        NotificationListenerService.RankingMap::class.java,
                    ),
                removed = listenerClass.requiredMethods("onNotificationRemoved"),
                connected = listenerClass.requiredMethod("onListenerConnected"),
                onDarkChanged =
                    runCatching {
                        cl.loadClass(CLOCK).requiredMethod(
                            "onDarkChanged",
                            java.util.ArrayList::class.java,
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                        )
                    }.getOrNull(),
            )
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun buildChip(context: Context): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        val pad = context.dp(CHIP_PADDING_DP)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply { cornerRadius = Float.MAX_VALUE }
        layoutParams =
            LinearLayout
                .LayoutParams(context.dp(CHIP_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    marginStart = context.dp(CHIP_MARGIN_DP)
                    gravity = Gravity.CENTER_VERTICAL
                }
        addView(
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams =
                    LinearLayout
                        .LayoutParams(context.dp(ICON_SIZE_DP), context.dp(ICON_SIZE_DP))
                        .apply { marginEnd = context.dp(ICON_GAP_DP) }
            },
        )
        addView(
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                layoutParams = LinearLayout.LayoutParams(0, context.dp(BAR_HEIGHT_DP), 1f)
            },
        )
        applyTint(this)
    }

private fun applyTint(view: LinearLayout) {
    val color = tint
    (view.background as GradientDrawable).setColor(withAlpha(color, PILL_ALPHA))
    (view.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(color)
    (view.getChildAt(1) as ProgressBar).apply {
        progressTintList = ColorStateList.valueOf(color)
        progressBackgroundTintList = ColorStateList.valueOf(withAlpha(color, TRACK_ALPHA))
    }
}

private fun withAlpha(
    color: Int,
    alpha: Int,
): Int = (alpha shl ALPHA_SHIFT) or (color and ALPHA_MASK)

private fun hasProgress(notification: Notification): Boolean {
    val extras = notification.extras ?: return false
    return extras.containsKey(Notification.EXTRA_PROGRESS) &&
        extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 &&
        !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
}

private fun resync() {
    chip?.post {
        tracked = null
        render()
        if (switch.enabled) replay()
    }
}

private fun render() {
    chip?.visibility = if (switch.enabled && tracked != null) View.VISIBLE else View.GONE
}

private fun replay() {
    runCatching { listener?.get()?.activeNotifications?.forEach(::onPosted) }
        .onFailure { Logger.error("replay failed tweak=$TWEAK reason=${it.message}", it) }
}

private fun onPosted(sbn: StatusBarNotification) {
    val view = chip ?: return
    if (!switch.enabled) return
    val notification = sbn.notification
    if (!hasProgress(notification)) {
        if (sbn.key == tracked) release()
        return
    }
    if (tracked != null && tracked != sbn.key) return
    val extras = notification.extras
    if (tracked == null) {
        tracked = sbn.key
        (view.getChildAt(0) as ImageView).setImageDrawable(
            notification.smallIcon?.loadDrawable(view.context),
        )
    }
    (view.getChildAt(1) as ProgressBar).apply {
        max = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        progress = extras.getInt(Notification.EXTRA_PROGRESS)
    }
    render()
}

private fun onRemoved(sbn: StatusBarNotification) {
    if (sbn.key == tracked) release()
}

private fun release() {
    tracked = null
    render()
    if (switch.enabled) replay()
}

private fun attach(statusBar: ViewGroup) {
    val resources = statusBar.resources
    val packageName = statusBar.context.packageName
    val iconArea =
        statusBar.findViewById<View>(resources.getIdentifier(ICON_AREA_ID, "id", packageName))
            ?: error("no $ICON_AREA_ID")
    val startSide =
        statusBar.findViewById<View>(resources.getIdentifier(START_SIDE_ID, "id", packageName))
            ?: iconArea.parent as ViewGroup
    var anchor: View = iconArea
    while (anchor.parent !== startSide) anchor = anchor.parent as? View ?: break
    val container = anchor.parent as ViewGroup
    val view = chip?.takeIf { it.context === statusBar.context } ?: buildChip(statusBar.context)
    (view.parent as? ViewGroup)?.removeView(view)
    container.addView(view, container.indexOfChild(anchor))
    chip = view
    Logger.info("attached tweak=$TWEAK")
    resync()
}

private fun View.findInstance(type: Class<*>): View? {
    if (type.isInstance(this)) return this
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        group.getChildAt(index).findInstance(type)?.let { return it }
    }
    return null
}

private fun findLiveStatusBar(statusBarClass: Class<*>): ViewGroup? {
    val global = Class.forName(WINDOW_MANAGER_GLOBAL)
    val instance = global.getMethod("getInstance").invoke(null)
    val names = global.getMethod("getViewRootNames").invoke(instance) as Array<*>
    val getRootView = global.getMethod("getRootView", String::class.java)
    return names.firstNotNullOfOrNull { name ->
        (getRootView.invoke(instance, name) as? View)?.findInstance(statusBarClass) as? ViewGroup
    }
}

private fun attachLive(statusBarClass: Class<*>) {
    if (chip != null) return
    runCatching { findLiveStatusBar(statusBarClass)?.let(::attach) }
        .onFailure { Logger.error("attach failed tweak=$TWEAK reason=${it.message}", it) }
}

private fun XposedModule.installDownloadChip(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { ChipBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.onFinishInflate.signature()}," +
            "${bindings.posted.signature()},${bindings.removed.size}xonNotificationRemoved," +
            "${bindings.connected.signature()},${bindings.onDarkChanged?.signature() ?: "no-tint"}",
    )
    hook(bindings.onFinishInflate).intercept { chain ->
        val result = chain.proceed()
        runCatching { attach(chain.thisObject as ViewGroup) }
            .onFailure { Logger.error("attach failed tweak=$TWEAK reason=${it.message}", it) }
        result
    }
    hook(bindings.posted).intercept { chain ->
        val result = chain.proceed()
        val service = chain.thisObject as NotificationListenerService
        listener = WeakReference(service)
        val sbn = chain.getArg(0) as StatusBarNotification
        chip?.post { runCatching { onPosted(sbn) } }
        result
    }
    for (method in bindings.removed) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            val sbn = chain.getArg(0) as StatusBarNotification
            chip?.post { runCatching { onRemoved(sbn) } }
            result
        }
    }
    hook(bindings.connected).intercept { chain ->
        val result = chain.proceed()
        listener = WeakReference(chain.thisObject as NotificationListenerService)
        resync()
        result
    }
    bindings.onDarkChanged?.let { method ->
        hook(method).intercept { chain ->
            val result = chain.proceed()
            tint = chain.getArg(2) as Int
            chip?.let { view -> view.post { applyTint(view) } }
            result
        }
    }
    val statusBarClass = bindings.onFinishInflate.declaringClass
    Handler(Looper.getMainLooper()).post { attachLive(statusBarClass) }
    return true
}
