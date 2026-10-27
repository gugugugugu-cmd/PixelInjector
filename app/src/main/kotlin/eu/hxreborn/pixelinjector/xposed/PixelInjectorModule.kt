package eu.hxreborn.pixelinjector.xposed

import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

private const val SAVED_CLASSLOADER = "classLoader"
private const val SAVED_DEXKIT = "dexkit"

@PublishedApi
internal lateinit var module: PixelInjectorModule
    private set

class PixelInjectorModule : XposedModule() {
    private lateinit var process: String
    private var systemServer = false
    private var target: Target? = null
    private var classLoader: ClassLoader? = null
    private var remotePrefs: SharedPreferences? = null
    private var installed = false
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            val target = target ?: return@OnSharedPreferenceChangeListener
            try {
                loadHookPrefs(sp, target, process, key)
            } catch (e: Exception) {
                Logger.error("prefs reload failed reason=${e.message}", e)
            } catch (e: LinkageError) {
                Logger.error("prefs reload failed reason=${e.message}", e)
            }
        }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        process = param.processName
        systemServer = param.isSystemServer
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        installTweaks(process, systemServer = true, param.classLoader, origin = "boot")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        if (installed) {
            Logger.debug {
                "install skipped proc=$process pkg=${param.packageName} reason=already-installed"
            }
            return
        }
        installTweaks(process, systemServer, param.classLoader, origin = "boot")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val cl = classLoader
        val target = target
        if (cl == null) {
            Logger.warn("hot reload rejected reason=no-classloader")
            return false
        }
        val state = HashMap<String, Any?>()
        for (tweak in target?.let(::tweaksFor).orEmpty()) {
            runCatching { tweak.saveState() }
                .onSuccess { if (it != null) state[tweak.key] = it }
                .onFailure {
                    Logger.error(
                        "save failed tweak=${tweak.key} reason=${it.message}",
                        it,
                    )
                }
        }
        state[SAVED_CLASSLOADER] = cl
        state[SAVED_DEXKIT] = DexKit.save()
        param.setSavedInstanceState(state)
        remotePrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        module = this
        runCatching { super.onHotReloaded(param) }
            .onFailure {
                Logger.warn(
                    "hot reload old hook removal failed reason=${it.message}",
                    it,
                )
            }
        val saved = param.savedInstanceState as? Map<*, *>
        val cl = saved?.get(SAVED_CLASSLOADER) as? ClassLoader
        if (cl == null) {
            Logger.error("hot reload aborted reason=no-classloader")
            return
        }
        DexKit.restore(saved[SAVED_DEXKIT])
        installTweaks(param.processName, param.isSystemServer, cl, origin = "reload", saved = saved)
    }

    private fun installIsolated(
        tweak: Tweak,
        cl: ClassLoader,
    ): Boolean =
        try {
            tweak.install(this, cl)
        } catch (e: Exception) {
            installFailed(tweak, e)
        } catch (e: LinkageError) {
            installFailed(tweak, e)
        }

    private fun installFailed(
        tweak: Tweak,
        cause: Throwable,
    ): Boolean {
        Logger.error(
            "install failed tweak=${tweak.key} build=${Build.ID} reason=${cause.message}",
            cause,
        )
        return false
    }

    private fun installTweaks(
        process: String,
        systemServer: Boolean,
        cl: ClassLoader,
        origin: String,
        saved: Map<*, *>? = null,
    ) {
        classLoader = cl
        installed = true
        this.process = process
        Logger.info(
            "install proc=$process pid=${Process.myPid()} api=$apiVersion " +
                "version=${BuildConfig.VERSION_CODE} origin=$origin " +
                "framework=$frameworkName/$frameworkVersionCode",
        )
        val target = Target.of(process, systemServer)
        this.target = target
        if (target == null) {
            Logger.debug { "install skipped proc=$process reason=no-target" }
            return
        }
        runCatching { getRemotePreferences(Prefs.GROUP) }
            .onSuccess { prefs ->
                remotePrefs = prefs
                prefs.registerOnSharedPreferenceChangeListener(prefsListener)
                loadHookPrefs(prefs, target, process)
            }.onFailure {
                Logger.warn("remote prefs unavailable defaults=true reason=${it.message}", it)
            }
        val tweaks = tweaksFor(target)
        val summary =
            try {
                tweaks.associate { tweak ->
                    tweak.key to installIsolated(tweak, cl)
                }
            } finally {
                DexKit.release()
            }
        if (saved != null) {
            for (tweak in tweaks) {
                runCatching { tweak.restoreState(saved[tweak.key]) }
                    .onFailure {
                        Logger.error("restore failed tweak=${tweak.key} reason=${it.message}", it)
                    }
            }
        }
        Logger.info(
            "hooks installed " + summary.entries.joinToString(" ") { "${it.key}=${it.value}" },
        )
    }
}
