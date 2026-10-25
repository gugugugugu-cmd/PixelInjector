package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import kotlin.math.roundToInt

private const val TWEAK = "FirefoxLoadingIndicator"
private const val SWIPE_REFRESH_PACKAGE = "androidx.swiperefreshlayout.widget"
private const val SWIPE_REFRESH_LAYOUT = "$SWIPE_REFRESH_PACKAGE.SwipeRefreshLayout"
private const val PROGRESS_DRAWABLE = "$SWIPE_REFRESH_PACKAGE.CircularProgressDrawable"
private const val PROGRESS_RING = "$PROGRESS_DRAWABLE\$Ring"
private const val MAX_PROGRESS_TRIM = 0.8f
private const val CONTAINER_SIZE_DP = 48f

private val switch = Switch(Prefs.FIREFOX_LOADING_INDICATOR)

internal val firefoxLoadingIndicator =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch),
        install = XposedModule::installFirefoxLoadingIndicator,
    )

private object OvalOutline : ViewOutlineProvider() {
    override fun getOutline(
        view: View,
        outline: Outline,
    ) = outline.setOval(0, 0, view.width, view.height)
}

private class RefreshBindings(
    val constructor: Constructor<*>,
    val circleView: Field,
    val progress: Field,
    val circleDiameter: Field,
    val originalOffsetTop: Field,
    val currentTargetOffsetTop: Field,
    val ring: Field,
    val endTrim: Field,
) {
    fun apply(layout: ViewGroup) {
        val diameter = (CONTAINER_SIZE_DP * layout.resources.displayMetrics.density).roundToInt()
        circleDiameter.setInt(layout, diameter)
        originalOffsetTop.setInt(layout, -diameter)
        currentTargetOffsetTop.setInt(layout, -diameter)
        val stock = progress.get(layout) as Drawable
        val stockRing = ring.get(stock)
        val pullFraction = { endTrim.getFloat(stockRing) / MAX_PROGRESS_TRIM }
        val indicator = LoadingIndicatorDrawable(stock, layout.context, pullFraction)
        val view = circleView.get(layout) as ImageView
        view.background = ColorDrawable(Color.TRANSPARENT)
        view.outlineProvider = OvalOutline
        view.setImageDrawable(indicator)
        stock.callback = indicator
    }

    companion object {
        fun resolve(cl: ClassLoader): RefreshBindings {
            val layout = cl.loadClass(SWIPE_REFRESH_LAYOUT)
            return RefreshBindings(
                constructor =
                    layout.getDeclaredConstructor(
                        Context::class.java,
                        AttributeSet::class.java,
                    ),
                circleView = layout.requiredField("mCircleView"),
                progress = layout.requiredField("mProgress"),
                circleDiameter = layout.requiredField("mCircleDiameter"),
                originalOffsetTop = layout.requiredField("mOriginalOffsetTop"),
                currentTargetOffsetTop = layout.requiredField("mCurrentTargetOffsetTop"),
                ring = cl.loadClass(PROGRESS_DRAWABLE).requiredField("mRing"),
                endTrim = cl.loadClass(PROGRESS_RING).requiredField("mEndTrim"),
            )
        }
    }
}

private fun XposedModule.installFirefoxLoadingIndicator(cl: ClassLoader): Boolean {
    val bindings =
        resolving { RefreshBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=SwipeRefreshLayout#<init>,${bindings.endTrim.signature()}",
    )
    hook(bindings.constructor).intercept { chain ->
        val result = chain.proceed()
        if (switch.enabled) {
            runCatching { bindings.apply(chain.thisObject as ViewGroup) }
                .onSuccess { Logger.info("applied tweak=$TWEAK") }
                .onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
        }
        result
    }
    return true
}
