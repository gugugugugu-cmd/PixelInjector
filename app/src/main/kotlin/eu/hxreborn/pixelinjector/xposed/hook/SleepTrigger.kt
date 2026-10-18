package eu.hxreborn.pixelinjector.xposed.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import eu.hxreborn.pixelinjector.xposed.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

private const val KEYCODE_LOCK = 324
private const val LOCK_THEN_SLEEP = "input keyevent $KEYCODE_LOCK ${KeyEvent.KEYCODE_SLEEP}"

internal class SleepTrigger(
    private val tweak: String,
    private val viaRoot: Boolean,
) {
    @SuppressLint("MissingPermission")
    fun sleep(context: Context) {
        context
            .getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        if (viaRoot) thread { lockAndSleepWithRoot() } else lockAndSleepInProcess(context)
    }

    private fun lockAndSleepWithRoot() {
        runCatching { runRootCommand(LOCK_THEN_SLEEP) }
            .onSuccess { (code, output) ->
                if (code == 0) {
                    Logger.info("sleep path=root tweak=$tweak exit=0")
                } else {
                    Logger.error("sleep path=root tweak=$tweak exit=$code output=$output")
                }
            }.onFailure { Logger.error("sleep failed path=root tweak=$tweak reason=${it.message}") }
    }

    private fun lockAndSleepInProcess(context: Context) {
        runCatching {
            val windowManager =
                Class
                    .forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService")
                    .invoke(null)
            windowManager.javaClass
                .getMethod(
                    "lockNow",
                    Bundle::class.java,
                ).invoke(windowManager, null)
            val power = context.getSystemService(PowerManager::class.java)
            PowerManager::class.java
                .getMethod("goToSleep", Long::class.javaPrimitiveType)
                .invoke(power, SystemClock.uptimeMillis())
        }.onSuccess { Logger.info("sleep path=lockNow tweak=$tweak") }
            .onFailure {
                Logger.error(
                    "sleep failed path=lockNow tweak=$tweak reason=${it.cause?.message ?: it.message}",
                    it,
                )
            }
    }
}

private fun runRootCommand(command: String): Pair<Int, String> {
    val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    process.outputStream.close()
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw TimeoutException("su timeout")
    }
    val output =
        process.inputStream
            .bufferedReader()
            .readText()
            .trim()
            .replace('\n', ' ')
    return process.exitValue() to output
}
