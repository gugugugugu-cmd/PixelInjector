package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE
import eu.hxreborn.pixelinjector.icons.ShadeIcons
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "EmptyShade"
private const val CONTENT =
    "com.android.systemui.notifications.stack.emptyshade.ui.composable.EmptyShadeContentImplKt"

// View shade of stable Android 17 (CD1A.261005.003 SystemUIGoogle), not yet in AOSP. Older binder:
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/emptyshade/ui/viewbinder/EmptyShadeViewBinder.kt
private const val VIEW_COLLECTOR =
    "com.android.systemui.statusbar.notification.emptyshade.ui.viewbinder.EmptyShadeViewBinder\$bind\$2\$1\$1"
private const val ICON_MESSAGE_MODEL =
    "com.android.systemui.statusbar.notification.emptyshade.ui.shared.model.IconMessageModel"
private const val ICON_VIEW =
    "com.android.systemui.statusbar.notification.emptyshade.ui.view.EmptyShadeIconView"
private const val RESOURCE_ICON = "com.android.systemui.common.shared.model.Icon\$Resource"
private const val LOADED_ICON = "com.android.systemui.common.shared.model.Icon\$Loaded"
private const val STOCK_ICON = "ic_trophy"
private const val DRAWABLE_TYPE = "drawable"
private const val TEXT_ARG = 0
private const val ICON_ARG = 1
private const val SNAPSHOT_STATE = "androidx.compose.runtime.SnapshotStateKt"
private const val STATE = "androidx.compose.runtime.State"
private const val MUTABLE_STATE = "androidx.compose.runtime.MutableState"
private const val DEFAULT_POLICY_ARG_MASK = 2

@Volatile private var current: ShadeBindings? = null

@Volatile private var currentView: ViewBindings? = null

private val requestRecompose: (Any?) -> Unit = {
    runCatching { current?.requestRecompose() }
        .onFailure { Logger.warn("recompose failed tweak=$TWEAK reason=${it.reason()}") }
    currentView?.reapply()
}

private val switch = Switch(Prefs.EMPTY_SHADE, requestRecompose)
private val customText = Value(Prefs.EMPTY_SHADE_TEXT, requestRecompose) { it.trim() }
private val customIcon =
    Value(Prefs.EMPTY_SHADE_ICON_PATH, requestRecompose) {
        it.takeIf(String::isNotEmpty)?.let(::renderableIcon)
    }

private fun renderableIcon(pathData: String): Drawable? =
    runCatching {
        val icon = ShadeIcons.fromPathData(pathData)
        check(icon.intrinsicWidth > 0 && icon.intrinsicWidth == icon.intrinsicHeight) {
            "intrinsic=${icon.intrinsicWidth}x${icon.intrinsicHeight}"
        }
        FixedSizeNoThrowIcon(icon).also { it.recordTestDraw() }
    }.onFailure { Logger.warn("icon rejected tweak=$TWEAK reason=${it.reason()}", it) }.getOrNull()

private class FixedSizeNoThrowIcon(
    icon: Drawable,
) : DrawableWrapper(icon) {
    private val intrinsicSize = icon.intrinsicWidth

    @Volatile private var drawFailed = false

    override fun getIntrinsicWidth(): Int = intrinsicSize

    override fun getIntrinsicHeight(): Int = intrinsicSize

    fun recordTestDraw() {
        val picture = Picture()
        setBounds(0, 0, intrinsicSize, intrinsicSize)
        super.draw(picture.beginRecording(intrinsicSize, intrinsicSize))
        picture.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (drawFailed) return
        runCatching { super.draw(canvas) }.onFailure {
            drawFailed = true
            Logger.error("draw failed tweak=$TWEAK reason=${it.reason()}", it)
        }
    }
}

internal val emptyShade =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, customText, customIcon),
        saveState = { current?.recomposeState },
        restoreState = { saved -> current?.restoreRecomposeState(saved) },
        install = XposedModule::installEmptyShade,
    )

private class StockIcon(
    private val resourceIconClass: Class<*>,
    private val resourceIconId: Field,
    private val currentApplication: Method,
) {
    @Volatile var id = 0
        private set

    fun matches(icon: Any?): Boolean {
        if (!resourceIconClass.isInstance(icon)) return false
        if (id == 0) id = lookUpId()
        return id != 0 && resourceIconId.getInt(icon) == id
    }

    private fun lookUpId(): Int =
        runCatching {
            (currentApplication.invoke(null) as? Context)
                ?.resources
                ?.getIdentifier(STOCK_ICON, DRAWABLE_TYPE, SYSTEMUI_PACKAGE)
        }.getOrNull() ?: 0

    companion object {
        fun resolve(cl: ClassLoader): StockIcon {
            val resourceIconClass = cl.loadClass(RESOURCE_ICON)
            return StockIcon(
                resourceIconClass = resourceIconClass,
                resourceIconId = resourceIconClass.requiredField("resId"),
                currentApplication =
                    Class
                        .forName("android.app.ActivityThread", false, cl)
                        .getMethod("currentApplication"),
            )
        }
    }
}

private class ViewBindings(
    val emit: Method,
    private val collectorView: Field,
    private val modelMessage: Field,
    private val modelIcon: Field,
    private val textView: Field,
    private val iconView: Field,
    private val stockIcon: StockIcon,
) {
    @Volatile private var live: Pair<WeakReference<View>, Any>? = null

    fun onEmit(
        collector: Any,
        model: Any,
    ) {
        val view = collectorView.get(collector) as? View ?: return
        live = WeakReference(view) to model
        if (switch.enabled) apply(view, model)
    }

    fun reapply() {
        val (ref, model) = live ?: return
        val view = ref.get() ?: return
        view.post {
            runCatching { apply(view, model) }
                .onFailure { Logger.warn("reapply failed tweak=$TWEAK reason=${it.reason()}") }
        }
    }

    private fun apply(
        view: View,
        model: Any,
    ) {
        if (!stockIcon.matches(modelIcon.get(model))) return
        val enabled = switch.enabled
        (textView.get(view) as TextView).text =
            customText.value.takeIf { enabled && it.isNotEmpty() }
                ?: modelMessage.get(model) as String
        (iconView.get(view) as ImageView).setImageDrawable(
            customIcon.value.takeIf { enabled } ?: view.context.getDrawable(stockIcon.id),
        )
    }

    companion object {
        fun resolve(cl: ClassLoader): ViewBindings {
            val collector = cl.loadClass(VIEW_COLLECTOR)
            val model = cl.loadClass(ICON_MESSAGE_MODEL)
            val view = cl.loadClass(ICON_VIEW)
            return ViewBindings(
                emit = collector.requiredMethods("emit").single(),
                collectorView = collector.requiredField("\$view"),
                modelMessage = model.requiredField("message"),
                modelIcon = model.requiredField("icon"),
                textView = view.requiredField("textView"),
                iconView = view.requiredField("iconView"),
                stockIcon = StockIcon.resolve(cl),
            )
        }
    }
}

private class ShadeBindings(
    val content: Method,
    private val stockIcon: StockIcon,
    private val newLoadedIcon: Constructor<*>,
    private val getStateValue: Method,
    private val setStateValue: Method,
    @Volatile var recomposeState: Any,
) {
    @Volatile private var lastLoaded: Pair<Drawable, Any>? = null

    fun rewriteCaughtUp(args: Array<Any?>): Boolean {
        if (!stockIcon.matches(args[ICON_ARG])) return false
        customText.value.takeIf(String::isNotEmpty)?.let { args[TEXT_ARG] = it }
        customIcon.value?.let { args[ICON_ARG] = loadedIcon(it) }
        return true
    }

    private fun loadedIcon(drawable: Drawable): Any {
        lastLoaded?.takeIf { it.first === drawable }?.let { return it.second }
        val icon = newLoadedIcon.newInstance(drawable)
        Logger.info("applied tweak=$TWEAK icon=custom")
        lastLoaded = drawable to icon
        return icon
    }

    fun readRecomposeState() {
        getStateValue.invoke(recomposeState)
    }

    fun requestRecompose() {
        setStateValue.invoke(recomposeState, getStateValue.invoke(recomposeState) as Int + 1)
    }

    fun restoreRecomposeState(saved: Any?) {
        if (saved == null || !getStateValue.declaringClass.isInstance(saved)) return
        recomposeState = saved
        requestRecompose()
    }

    companion object {
        fun resolve(cl: ClassLoader): ShadeBindings {
            val newState =
                cl.loadClass(SNAPSHOT_STATE).requiredMethods("mutableStateOf\$default").single()
            return ShadeBindings(
                content =
                    cl
                        .loadClass(CONTENT)
                        .requiredMethods("EmptyShadeContent")
                        .single { it.parameterTypes.firstOrNull() == String::class.java },
                stockIcon = StockIcon.resolve(cl),
                newLoadedIcon = cl.loadClass(LOADED_ICON).getConstructor(Drawable::class.java),
                getStateValue = cl.loadClass(STATE).getMethod("getValue"),
                setStateValue = cl.loadClass(MUTABLE_STATE).getMethod("setValue", Any::class.java),
                recomposeState =
                    checkNotNull(
                        newState.invoke(null, 0, null, DEFAULT_POLICY_ARG_MASK, null),
                    ),
            )
        }
    }
}

private fun XposedModule.installEmptyShade(cl: ClassLoader): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
        Logger.debug { "install skipped tweak=$TWEAK reason=sdk sdk=${Build.VERSION.SDK_INT}" }
        return false
    }
    val bindings =
        runCatching { ShadeBindings.resolve(cl) }.getOrElse { composeMiss ->
            return installViewPath(cl, composeMiss)
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.content.signature()}")
    current = bindings
    hook(bindings.content).intercept { chain ->
        runCatching { bindings.readRecomposeState() }
        if (!switch.enabled) return@intercept chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        val rewritten =
            runCatching { bindings.rewriteCaughtUp(args) }.getOrElse {
                Logger.warn("rewrite failed tweak=$TWEAK reason=${it.reason()}")
                false
            }
        if (rewritten) chain.proceed(args) else chain.proceed()
    }
    return true
}

private fun XposedModule.installViewPath(
    cl: ClassLoader,
    composeMiss: Throwable,
): Boolean {
    val bindings =
        runCatching { ViewBindings.resolve(cl) }.getOrElse { viewMiss ->
            listOf(composeMiss, viewMiss).forEach {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
                )
            }
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.emit.signature()}")
    currentView = bindings
    hook(bindings.emit).intercept { chain ->
        val result = chain.proceed()
        runCatching { bindings.onEmit(chain.thisObject, chain.args[0]) }
            .onFailure { Logger.warn("rewrite failed tweak=$TWEAK reason=${it.reason()}") }
        result
    }
    return true
}
