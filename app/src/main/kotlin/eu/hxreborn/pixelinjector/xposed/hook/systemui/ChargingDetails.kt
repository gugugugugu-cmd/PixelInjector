package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.findFieldUpward
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import kotlin.math.abs

private const val TWEAK = "ChargingDetails"
private const val GOOGLE_CONTROLLER =
    "com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle"
private const val CONTROLLER = "com.android.systemui.statusbar.KeyguardIndicationController"
private const val MICRO = 1_000_000f
private const val MILLI = 1_000f
private const val TENTHS = 10f

private val switch = Switch(Prefs.CHARGING_DETAILS)

internal val chargingDetails =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installChargingDetails,
    )

private class IndicationBindings(
    val computePowerIndication: Method,
    val context: Field,
) {
    companion object {
        fun resolve(cl: ClassLoader): IndicationBindings {
            val controller =
                runCatching {
                    cl.loadClass(GOOGLE_CONTROLLER)
                }.getOrElse { cl.loadClass(CONTROLLER) }
            return IndicationBindings(
                computePowerIndication = controller.requiredMethod("computePowerIndication"),
                context =
                    controller.findFieldUpward("mContext")
                        ?: throw NoSuchFieldException("${controller.simpleName}.mContext"),
            )
        }
    }
}

private fun details(context: Context): String? {
    val battery =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return null
    val amps =
        abs(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / MICRO
    val volts = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / MILLI
    val celsius = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / TENTHS
    return String.format(
        Locale.ROOT,
        "%.1f W • %.2f V • %.2f A • %.1f °C",
        amps * volts,
        volts,
        amps,
        celsius,
    )
}

private fun XposedModule.installChargingDetails(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { IndicationBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.computePowerIndication.signature()}")
    hook(bindings.computePowerIndication).intercept { chain ->
        val result = chain.proceed()
        if (!switch.enabled || result !is String) return@intercept result
        val context =
            chain.thisObject?.let { bindings.context.get(it) } as? Context
                ?: return@intercept result
        runCatching { details(context) }
            .onFailure { Logger.error("details failed tweak=$TWEAK reason=${it.message}", it) }
            .getOrNull()
            ?.let { "$result\n$it" }
            ?: result
    }
    return true
}
