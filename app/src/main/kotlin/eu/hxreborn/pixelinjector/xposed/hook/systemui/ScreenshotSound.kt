package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "ScreenshotSound"
private const val PLAY_LAMBDA =
    "com.android.systemui.screenshot.ScreenshotSoundControllerImpl\$playScreenshotSound\$2"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val UNIT = "kotlin.Unit"
private const val PLAY_FAILED_MESSAGE = "Screenshot sound failed to play"
private const val INVOKE_SUSPEND = "invokeSuspend"

private val switch = Switch(Prefs.SCREENSHOT_SOUND)

internal val screenshotSound =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI, Target.SCREENSHOT),
        prefs = listOf(switch),
        install = XposedModule::installScreenshotSound,
    )

private class SoundBindings(
    val play: Method,
    val skipped: Any?,
) {
    val member: String
        get() = "${play.declaringClass.name.substringAfterLast('.')}#${play.name}"

    companion object {
        fun resolve(cl: ClassLoader): SoundBindings {
            val unit = { cl.loadClass(UNIT).getField("INSTANCE").get(null) }
            orNullIfMissing {
                cl
                    .loadClass(
                        PLAY_LAMBDA,
                    ).requiredMethod(INVOKE_SUSPEND, Any::class.java)
            }?.let { return SoundBindings(play = it, skipped = unit()) }
            orNullIfMissing { cl.loadClass(CONTROLLER).requiredMethod("playCameraSoundIfNeeded") }
                ?.let { return SoundBindings(play = it, skipped = null) }
            return SoundBindings(play = findPlayMethodByString(cl), skipped = unit())
        }
    }
}

private fun findPlayMethodByString(cl: ClassLoader): Method =
    DexKit
        .methods(TWEAK, cl) {
            it.findMethod { matcher { name(INVOKE_SUSPEND).usingStrings(PLAY_FAILED_MESSAGE) } }
        }.singleOrNull()
        ?.apply { isAccessible = true }
        ?: throw NoSuchMethodException("$INVOKE_SUSPEND usingStrings($PLAY_FAILED_MESSAGE)")

private fun XposedModule.installScreenshotSound(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { SoundBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.member}")
    hook(bindings.play).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        Logger.info("sound skipped tweak=$TWEAK")
        bindings.skipped
    }
    return true
}
