package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import eu.hxreborn.pixelinjector.icons.FingerprintStyles
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import java.util.zip.ZipFile

private const val TWEAK = "FingerprintIcon"
private const val ICON_VIEW = "com.android.systemui.keyguard.ui.view.DeviceEntryIconView"

@Volatile private var current: FingerprintBindings? = null

private val switch = Switch(Prefs.FINGERPRINT_ICON) { current?.updateAll() }
private val style = Value(Prefs.FINGERPRINT_ICON_STYLE, onLoad = { current?.updateAll() }) { it }
private val hideCircle = Switch(Prefs.FINGERPRINT_HIDE_CIRCLE) { current?.updateAll() }

internal val fingerprintIcon =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, style, hideCircle),
        install = XposedModule::installFingerprintIcon,
        saveState = { current?.let { ArrayList<View>(it.views) } },
        restoreState = { saved -> (saved as? List<*>)?.let { current?.restore(it) } },
    )

private class FingerprintSwap(
    stock: Drawable,
    private val fingerprintStates: List<IntArray>,
) : DrawableWrapper(stock) {
    var replacement: Drawable? = null
        set(value) {
            field = value
            value?.bounds = square(bounds)
            invalidateSelf()
        }

    private fun isFingerprint(state: IntArray): Boolean =
        fingerprintStates.any {
            StateSet.stateSetMatches(it, state)
        }

    override fun draw(canvas: Canvas) {
        if (isFingerprint(state)) replacement?.draw(canvas) else super.draw(canvas)
    }

    override fun onStateChange(state: IntArray): Boolean {
        val wasFingerprint = isFingerprint(drawable?.state ?: StateSet.NOTHING)
        val changed = super.onStateChange(state)
        if (wasFingerprint != isFingerprint(state)) drawable?.jumpToCurrentState()
        return changed
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        replacement?.bounds = square(bounds)
    }

    private fun square(bounds: Rect): Rect {
        val half = minOf(bounds.width(), bounds.height()) / 2
        return Rect(
            bounds.centerX() - half,
            bounds.centerY() - half,
            bounds.centerX() + half,
            bounds.centerY() + half,
        )
    }
}

private class FingerprintBindings(
    val iconView: Field,
    val bgView: Field,
    val fingerprintStates: List<IntArray>,
) {
    val views: MutableSet<View> =
        Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap()),
        )

    @Volatile private var cachedIcon: Pair<String, Drawable?>? = null

    fun register(view: View) {
        views += view
        view.post { updateView(view) }
    }

    fun updateAll() {
        cachedIcon = null
        synchronized(views) { views.toList() }.forEach { view -> view.post { updateView(view) } }
    }

    fun registerAttached() {
        runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val roots =
                global
                    .requiredField(
                        "mViews",
                    ).get(global.getMethod("getInstance").invoke(null)) as List<*>
            val found = roots.filterIsInstance<View>().flatMap { iconViewsIn(it) }
            found.forEach(::register)
            Logger.info("attached tweak=$TWEAK views=${found.size}")
        }.onFailure { Logger.error("attached failed tweak=$TWEAK reason=${it.message}", it) }
    }

    private fun iconViewsIn(view: View): List<View> =
        when {
            iconView.declaringClass.isInstance(view) -> {
                listOf(view)
            }

            view is ViewGroup -> {
                (0 until view.childCount).flatMap {
                    iconViewsIn(
                        view.getChildAt(it),
                    )
                }
            }

            else -> {
                emptyList()
            }
        }

    fun restore(saved: List<*>) {
        saved.filterIsInstance<View>().forEach(::register)
        Logger.info("carried tweak=$TWEAK views=${saved.size}")
    }

    private fun newReplacement(
        view: View,
        style: String,
    ): Drawable? {
        val cached = cachedIcon
        val prototype = if (cached?.first == style) cached.second else loadIcon(view, style)
        return prototype?.constantState?.newDrawable(view.resources)?.mutate()
    }

    private fun loadIcon(
        view: View,
        style: String,
    ): Drawable? {
        val drawable =
            if (FingerprintStyles.isCustom(style)) {
                module.openRemoteFile(FingerprintStyles.IMAGE_FILE).use { fd ->
                    BitmapFactory
                        .decodeFileDescriptor(
                            fd.fileDescriptor,
                        )?.let { BitmapDrawable(view.resources, it) }
                }
            } else {
                ZipFile(module.moduleApplicationInfo.sourceDir).use { zip ->
                    zip.getEntry("assets/${FingerprintStyles.assetPath(style)}")?.let { entry ->
                        zip.getInputStream(entry).use(BitmapFactory::decodeStream)?.let {
                            BitmapDrawable(view.resources, it)
                        }
                    }
                }
            }
        if (drawable == null) {
            Logger.error("icon failed tweak=$TWEAK style=$style reason=no-drawable")
        }
        cachedIcon = style to drawable
        return drawable
    }

    private fun updateView(view: View) {
        runCatching {
            val icon = iconView.get(view) as ImageView
            val bg = bgView.get(view) as ImageView
            val style = style.value
            val replacement =
                if (switch.enabled && style != FingerprintStyles.STOCK &&
                    style != FingerprintStyles.NONE
                ) {
                    newReplacement(view, style)
                } else {
                    null
                }
            val stock = unwrap(icon.drawable)
            if (replacement != null || (switch.enabled && style == FingerprintStyles.NONE)) {
                val wrapper =
                    icon.drawable as? FingerprintSwap
                        ?: FingerprintSwap(stock, fingerprintStates).also(icon::setImageDrawable)
                wrapper.replacement = replacement
            } else if (icon.drawable !== stock) {
                icon.setImageDrawable(stock)
            }
            val circleScale = if (switch.enabled && hideCircle.enabled) 0f else 1f
            bg.scaleX = circleScale
            bg.scaleY = circleScale
            val styleLabel = style.ifEmpty { "stock" }
            Logger.info(
                "applied tweak=$TWEAK style=$styleLabel hideCircle=${hideCircle.enabled} on=${switch.enabled}",
            )
        }.onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
    }

    private fun unwrap(drawable: Drawable): Drawable =
        if (drawable is DrawableWrapper &&
            drawable.javaClass.name == FingerprintSwap::class.java.name
        ) {
            drawable.drawable ?: drawable
        } else {
            drawable
        }

    companion object {
        fun resolve(cl: ClassLoader): FingerprintBindings {
            val view = cl.loadClass(ICON_VIEW)
            val iconType = cl.loadClass("$ICON_VIEW\$IconType")
            val getIconState =
                view.requiredMethod(
                    "getIconState",
                    iconType,
                    Boolean::class.javaPrimitiveType,
                )
            val fingerprint = iconType.enumConstants.first { (it as Enum<*>).name == "FINGERPRINT" }
            return FingerprintBindings(
                iconView = view.requiredField("iconView"),
                bgView = view.requiredField("bgView"),
                fingerprintStates =
                    listOf(false, true).map {
                        getIconState.invoke(null, fingerprint, it) as IntArray
                    },
            )
        }
    }
}

private fun XposedModule.installFingerprintIcon(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { FingerprintBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.iconView.signature()},${bindings.bgView.signature()}",
    )
    current = bindings
    for (constructor in bindings.iconView.declaringClass.declaredConstructors) {
        hook(constructor).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? View)?.let(bindings::register)
            result
        }
    }
    Handler(Looper.getMainLooper()).post { bindings.registerAttached() }
    return true
}
