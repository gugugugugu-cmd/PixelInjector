package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
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
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.roundToInt

private const val TWEAK = "HomeMenuEntry"
private const val OPTIONS = "com.android.launcher3.popup.WorkspaceLongPressOptions"
private const val OPTIONS_POPUP = "com.android.launcher3.views.OptionsPopupView"
private const val OPTION_ITEM = "$OPTIONS_POPUP\$OptionItem"
private const val SETTINGS_ICON = "ic_setting"
private const val SAMPLE_PX = 48
private const val POPUP_DATA = "com.android.launcher3.popup.PopupData"
private const val STRING_CONTAINER = "com.android.launcher3.popup.ui.StringContainer"
private const val FIXED_STRING = "$STRING_CONTAINER\$FixedString"
private const val RESOURCE_STRING = "$STRING_CONTAINER\$ResourceString"
private const val LAUNCHER_EVENT = "com.android.launcher3.logging.StatsLogManager\$LauncherEvent"
private const val SETTINGS_LABEL = "settings_button_text"
private const val ENTRY_ID = 0x7e504901
private const val ENTRY_LABEL = "PixelInjector"
private const val ICON_ID = 0x7e504902
private const val LABEL_ID = 0x7e504903
private const val ICON_DP = 24
private const val GLYPH_SCALE = 0.5336f
private const val GLYPH_FILL = 0.92f

private val switch = Switch(Prefs.HOME_MENU_ENTRY)

internal val homeMenuEntry =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installHomeMenuEntry,
    )

private sealed interface MenuBindings {
    val member: Method

    fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*>
}

private class SettingsIds(
    context: Context,
) {
    val label = context.resources.getIdentifier(SETTINGS_LABEL, "string", context.packageName)
    val icon = context.resources.getIdentifier(SETTINGS_ICON, "drawable", context.packageName)
}

@Volatile private var cachedSettingsIds: SettingsIds? = null

private fun settingsIds(context: Context): SettingsIds =
    cachedSettingsIds ?: SettingsIds(context).also { cachedSettingsIds = it }

private fun ignoreEvent(cl: ClassLoader): Any =
    cl.loadClass(LAUNCHER_EVENT).enumConstants.first {
        (it as Enum<*>).name ==
            "IGNORE"
    }

private fun Constructor<*>.listWithEntry(
    options: List<*>,
    settings: Int,
    entry: Any?,
): List<*> {
    @Suppress("UNCHECKED_CAST")
    val list = newInstance(options.size + 1) as MutableList<Any?>
    list.addAll(options)
    list.add(if (settings >= 0) settings + 1 else list.size, entry)
    return list
}

private class ComposeMenu(
    override val member: Method,
    val listConstructor: Constructor<*>,
    val popupData: Constructor<*>,
    val label: Field,
    val iconResId: Field,
    val category: Field,
    val resourceId: Field,
    val fixedString: Constructor<*>,
    val ignoreEvent: Any,
    val action: Any,
) : MenuBindings {
    override fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*> {
        val settingsLabel = settingsIds(context).label
        val settings =
            options.indexOfFirst { item ->
                item != null &&
                    labelRes(item) == settingsLabel
            }
        val anchor = options.getOrNull(settings) ?: options.lastOrNull() ?: return options
        val entry =
            popupData.newInstance(
                ENTRY_ID,
                if (EntryIcon.servedAsResource(context)) ICON_ID else iconResId.get(anchor),
                fixedString.newInstance(ENTRY_LABEL),
                category.get(anchor),
                ignoreEvent,
                "",
                action,
            )

        return listConstructor.listWithEntry(options, settings, entry)
    }

    private fun labelRes(item: Any): Int? {
        val text = label.get(item) ?: return null
        return if (resourceId.declaringClass.isInstance(text)) resourceId.getInt(text) else null
    }

    companion object {
        fun resolve(cl: ClassLoader): ComposeMenu {
            val getAll = cl.loadClass(OPTIONS).requiredMethod("getAll", Context::class.java)
            val popupDataClass = cl.loadClass(POPUP_DATA)
            val stringContainer = cl.loadClass(STRING_CONTAINER)
            val constructor =
                popupDataClass.declaredConstructors.firstOrNull {
                    it.parameterCount == 7 && it.parameterTypes[2] == stringContainer
                } ?: throw NoSuchMethodException("PopupData.<init>(int,int,StringContainer,...)")
            val actionType = constructor.parameterTypes[6]
            return ComposeMenu(
                member = getAll,
                listConstructor =
                    getAll.returnType.getDeclaredConstructor(
                        Int::class.javaPrimitiveType,
                    ),
                popupData = constructor.apply { isAccessible = true },
                label = popupDataClass.requiredField("label"),
                iconResId = popupDataClass.requiredField("iconResId"),
                category = popupDataClass.requiredField("category"),
                resourceId = cl.loadClass(RESOURCE_STRING).requiredField("resId"),
                fixedString = cl.loadClass(FIXED_STRING).getDeclaredConstructor(String::class.java),
                ignoreEvent = ignoreEvent(cl),
                action =
                    Proxy.newProxyInstance(
                        actionType.classLoader,
                        arrayOf(actionType),
                        ::onAction,
                    ),
            )
        }
    }
}

private class LabelResMenu(
    override val member: Method,
    val listConstructor: Constructor<*>,
    val popupData: Constructor<*>,
    val labelResId: Field,
    val category: Field,
    val ignoreEvent: Any,
    val action: Any,
) : MenuBindings {
    override fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*> {
        if (!EntryIcon.servedAsResource(context)) return options
        val settingsLabel = settingsIds(context).label
        val settings = options.indexOfFirst { it != null && labelResId.getInt(it) == settingsLabel }
        val anchor = options.getOrNull(settings) ?: options.lastOrNull() ?: return options
        val entry =
            popupData.newInstance(
                ICON_ID,
                LABEL_ID,
                category.get(anchor),
                ignoreEvent,
                action,
            )

        return listConstructor.listWithEntry(options, settings, entry)
    }

    companion object {
        fun resolve(cl: ClassLoader): LabelResMenu {
            val getAll = cl.loadClass(OPTIONS).requiredMethod("getAll", Context::class.java)
            val popupDataClass = cl.loadClass(POPUP_DATA)
            val constructor =
                popupDataClass.declaredConstructors.firstOrNull {
                    it.parameterCount == 5 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        it.parameterTypes[1] == Int::class.javaPrimitiveType
                }
                    ?: throw NoSuchMethodException(
                        "PopupData.<init>(int,int,PopupCategory,EventEnum,Function3)",
                    )
            val actionType = constructor.parameterTypes[4]
            return LabelResMenu(
                member = getAll,
                listConstructor =
                    getAll.returnType.getDeclaredConstructor(
                        Int::class.javaPrimitiveType,
                    ),
                popupData = constructor.apply { isAccessible = true },
                labelResId = popupDataClass.requiredField("labelResId"),
                category = popupDataClass.requiredField("category"),
                ignoreEvent = ignoreEvent(cl),
                action =
                    Proxy.newProxyInstance(
                        actionType.classLoader,
                        arrayOf(actionType),
                        ::onAction,
                    ),
            )
        }
    }
}

private class ViewMenu(
    override val member: Method,
    val optionItem: Constructor<*>,
    val labelRes: Field,
    val label: Field,
    val icon: Field,
    val ignoreEvent: Any,
) : MenuBindings {
    override fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*> {
        val ids = settingsIds(context)
        val settings =
            options.indexOfFirst { item ->
                item != null &&
                    labelRes.getInt(item) == ids.label
            }
        val anchor = options.getOrNull(settings) ?: options.lastOrNull() ?: return options
        val entry =
            optionItem.newInstance(
                context,
                labelRes.getInt(anchor),
                ids.icon,
                ignoreEvent,
                View.OnLongClickListener { openCompanion(it.context) == null },
            )
        label.set(entry, ENTRY_LABEL)
        EntryIcon.bitmap(context)?.let { glyph ->
            val tint = sampleColor(icon.get(anchor) as Drawable?)
            icon.set(entry, BitmapDrawable(context.resources, glyph).apply { tint?.let(::setTint) })
        }
        val list = ArrayList<Any?>(options)
        list.add(if (settings >= 0) settings + 1 else list.size, entry)
        return list
    }

    private fun sampleColor(drawable: Drawable?): Int? {
        drawable ?: return null
        val sample = Bitmap.createBitmap(SAMPLE_PX, SAMPLE_PX, Bitmap.Config.ARGB_8888)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        copy.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
        copy.draw(Canvas(sample))
        val pixels =
            IntArray(SAMPLE_PX * SAMPLE_PX).also {
                sample.getPixels(it, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
            }
        return pixels.maxByOrNull { it ushr 24 }?.takeIf { it ushr 24 != 0 }?.or(0xFF shl 24)
    }

    companion object {
        fun resolve(cl: ClassLoader): ViewMenu {
            val popup = cl.loadClass(OPTIONS_POPUP)
            val getOptions =
                popup.declaredMethods.firstOrNull {
                    it.name == "getOptions" &&
                        it.parameterCount == 1
                }
                    ?: throw NoSuchMethodException("OptionsPopupView.getOptions(Launcher)")
            val item = cl.loadClass(OPTION_ITEM)
            val constructor =
                item.declaredConstructors.firstOrNull {
                    it.parameterCount == 5 &&
                        it.parameterTypes[0] == Context::class.java
                }
                    ?: throw NoSuchMethodException(
                        "OptionItem.<init>(Context,int,int,EventEnum,OnLongClickListener)",
                    )
            return ViewMenu(
                member = getOptions.apply { isAccessible = true },
                optionItem = constructor.apply { isAccessible = true },
                labelRes = item.requiredField("labelRes"),
                label = item.requiredField("label"),
                icon = item.requiredField("icon"),
                ignoreEvent = ignoreEvent(cl),
            )
        }
    }
}

private object EntryIcon {
    @Volatile private var glyph: Bitmap? = null
    private var hooked = false

    fun bitmap(context: Context): Bitmap? {
        if (glyph == null) {
            glyph =
                runCatching { render(context) }
                    .onFailure { Logger.error("icon failed tweak=$TWEAK reason=${it.message}", it) }
                    .getOrNull()
        }
        return glyph
    }

    @Synchronized
    fun servedAsResource(context: Context): Boolean {
        if (bitmap(context) == null) return false
        if (!hooked) {
            hookResources()
            hooked = true
        }
        return true
    }

    private fun render(context: Context): Bitmap {
        val glyph =
            context.packageManager
                .getResourcesForApplication(BuildConfig.APPLICATION_ID)
                .getDrawable(R.drawable.ic_launcher_foreground, null)
        val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val canvasSize = (size * GLYPH_FILL / GLYPH_SCALE).roundToInt()
        val offset = (size - canvasSize) / 2
        glyph.setBounds(offset, offset, offset + canvasSize, offset + canvasSize)
        return Bitmap
            .createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888,
            ).also { glyph.draw(Canvas(it)) }
    }

    private fun hookResources() {
        val resources = Resources::class.java
        module
            .hook(
                resources.requiredMethod(
                    "getValue",
                    Int::class.javaPrimitiveType,
                    TypedValue::class.java,
                    Boolean::class.javaPrimitiveType,
                ),
            ).intercept { chain ->
                if (chain.getArg(0) != ICON_ID) return@intercept chain.proceed()
                (chain.getArg(1) as TypedValue).apply {
                    type = TypedValue.TYPE_STRING
                    string = "res/drawable/pixelinjector.png"
                    resourceId = ICON_ID
                }
                null
            }
        module
            .hook(
                resources.requiredMethod(
                    "getDrawable",
                    Int::class.javaPrimitiveType,
                    Resources.Theme::class.java,
                ),
            ).intercept { chain ->
                val icon = glyph
                if (chain.getArg(0) != ICON_ID || icon == null) return@intercept chain.proceed()
                BitmapDrawable(chain.thisObject as Resources, icon)
            }
        module
            .hook(resources.requiredMethod("getText", Int::class.javaPrimitiveType))
            .intercept { chain ->
                if (chain.getArg(0) != LABEL_ID) return@intercept chain.proceed()
                ENTRY_LABEL
            }
        Logger.info(
            "hooked tweak=$TWEAK members=Resources#getValue,Resources#getDrawable,Resources#getText",
        )
    }
}

private fun onAction(
    proxy: Any,
    method: Method,
    args: Array<out Any?>?,
): Any? =
    when (method.name) {
        "equals" -> {
            proxy === args?.firstOrNull()
        }

        "hashCode" -> {
            System.identityHashCode(proxy)
        }

        "toString" -> {
            TWEAK
        }

        else -> {
            openCompanion(
                args?.firstNotNullOfOrNull { (it as? View)?.context ?: it as? Context },
            )
        }
    }

private fun openCompanion(context: Context?): Any? {
    context ?: return null.also { Logger.error("open failed tweak=$TWEAK reason=no-context") }
    runCatching {
        val intent =
            context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                ?: error("no launch intent")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Logger.info("opened tweak=$TWEAK")
    }.onFailure { Logger.error("open failed tweak=$TWEAK reason=${it.message}", it) }
    return null
}

private fun XposedModule.installHomeMenuEntry(cl: ClassLoader): Boolean {
    val bindings =
        runCatching<MenuBindings> { ComposeMenu.resolve(cl) }
            // Pixel Launcher 17 CP2A.260605.012
            .recoverCatching { LabelResMenu.resolve(cl) }
            .recoverCatching { ViewMenu.resolve(cl) }
            .getOrElse {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
                )
                return false
            }
    Logger.info("resolved tweak=$TWEAK members=${bindings.member.signature()}")
    hook(bindings.member).intercept { chain ->
        val options = chain.proceed()
        if (!switch.enabled || options !is List<*>) return@intercept options
        val context = chain.getArg(0) as? Context ?: return@intercept options

        runCatching { bindings.withEntry(context, options) }
            .onFailure { Logger.error("entry failed tweak=$TWEAK reason=${it.message}", it) }
            .getOrDefault(options)
    }
    return true
}
