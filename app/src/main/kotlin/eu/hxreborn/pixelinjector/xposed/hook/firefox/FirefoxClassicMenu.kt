package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "FirefoxClassicMenu"
private const val MENU_PACKAGE = "org.mozilla.fenix.components.menu"
private const val MENU_COMPOSE = "$MENU_PACKAGE.compose"
private const val BOTTOM_SHEET_BEHAVIOR =
    "com.google.android.material.bottomsheet.BottomSheetBehavior"
private const val COORDINATOR_PARAMS =
    "androidx.coordinatorlayout.widget.CoordinatorLayout\$LayoutParams"
private const val FENIX_CONTEXT = "org.mozilla.fenix.ext.ContextKt"
private const val FENIX_COMPONENTS = "org.mozilla.fenix.components.Components"
private const val FENIX_SETTINGS = "org.mozilla.fenix.utils.Settings"
private const val COMPOSER = "androidx.compose.runtime.Composer"
private const val COLOR_SCHEME = "androidx.compose.material3.ColorScheme"
private const val COLOR_SCHEME_KT = "androidx.compose.material3.ColorSchemeKt"
private const val SPACED_ALIGNED = "androidx.compose.foundation.layout.Arrangement\$SpacedAligned"
private const val BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt"
private const val SURFACE_KT = "androidx.compose.material3.SurfaceKt"
private const val DIVIDER_KT = "androidx.compose.material3.DividerKt"
private const val LIST_ITEM_KT = "org.mozilla.fenix.compose.list.ListItemKt"
private const val MODIFIER = "androidx.compose.ui.Modifier"
private const val SIZE_KT = "androidx.compose.foundation.layout.SizeKt"
private const val PADDING_KT = "androidx.compose.foundation.layout.PaddingKt"
private const val CLIP_KT = "androidx.compose.ui.draw.ClipKt"
private const val SHAPE = "androidx.compose.ui.graphics.Shape"
private const val ROUNDED_CORNER_SHAPE_KT = "androidx.compose.foundation.shape.RoundedCornerShapeKt"
private const val RECTANGLE_SHAPE_KT = "androidx.compose.ui.graphics.RectangleShapeKt"
private const val TEXT_KT = "androidx.compose.material3.TextKt"
private const val TEXT_STYLE = "androidx.compose.ui.text.TextStyle"
private const val ACORN_TYPOGRAPHY_KT = "mozilla.components.compose.base.theme.AcornTypographyKt"
private const val ICON_KT = "androidx.compose.material3.IconKt"
private const val CHECKBOX_KT = "androidx.compose.material3.CheckboxKt"
private const val CHECKBOX_COLORS = "androidx.compose.material3.CheckboxColors"
private const val COMPOSE_COLOR = "androidx.compose.ui.graphics.Color"
private const val PAINTER = "androidx.compose.ui.graphics.painter.Painter"
private const val PAINTER_RESOURCES = "androidx.compose.ui.res.PainterResources_androidKt"
private const val STRING_RESOURCES = "androidx.compose.ui.res.StringResources_androidKt"
private const val ICON_RESOURCES = "mozilla.components.ui.icons.R\$drawable"
private const val FENIX_STRINGS = "org.mozilla.fenix.R\$string"
private const val MENU_WIDTH_DP = 270f
private const val CORNER_DP = 24f
private const val MARGIN_DP = 6f
private const val BOTTOM_GAP_DP = 13f
private const val ROW_HEIGHT_DP = 48f
private const val STOCK_ACCOUNT_ROW_HEIGHT_DP = 56f
private const val NAV_BUTTON_DP = 40f
private const val NAV_BUTTON_ICON_INSET_DP = 8f
private const val STOCK_NAV_ROW_PADDING_DP = 4f
private const val ICON_ROW_PADDING_DP = 8f
private const val NAV_SPACING_DP = 4f
private const val STOCK_ICON_LABEL_GAP_DP = 16f
private const val ICON_LABEL_GAP_DP = 12f
private const val SINGLE_LINE = 1
private const val DIVIDER_INSET_DP = 16f
private const val DIVIDER_PADDING_DP = 5f
private const val DIVIDER_THICKNESS_DEFAULT_MASK = 2
private const val CHECKBOX_MODIFIER_ENABLED_DEFAULT_MASK = 0xC
private const val MENU_ITEM_DEFAULTS_EXCEPT_ON_CLICK_MASK = 0x3F7FC
private const val MENU_ITEM_STATE = 8
private const val MENU_ITEM_ON_CLICK = 10
private const val MENU_ITEM_AFTER_CONTENT = 16
private const val ACTIVE_STATE = "ACTIVE"
private const val ENABLED_STATE = "ENABLED"
private const val LIBRARY_ROWS_GROUP_KEY = 0x4C494252
private const val MAIN_MENU_BOTTOM_TOOLBAR = 4
private const val MAIN_MENU_EXPANDED_TOOLBAR = 5
private const val MENU_FRAME_CONTENT_MODIFIER = 1
private const val WRAP_CONTENT_DEFAULTS_MASK = 3
private const val DISABLED_CONTAINER_ALPHA = 0x1F
private const val COLOR_SHIFT = 32
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFFL
private const val GROUP_SPACING_DP = 2f
private const val FRAME_SPACING_DP = 12f
private const val ENTER_MS = 200L
private const val RESTART_CALL_BIT = 1
private const val BACKGROUND_COLOR = 1
private const val BACKGROUND_SHAPE = 2
private const val MIN_HEIGHT = 2
private const val SURFACE_COLOR = 2
private const val LIST_ITEM_DESCRIPTION = 5
private const val LIST_ITEM_HEIGHT = 8
private const val LIST_ITEM_CONTENT_MAX_LINES = 5
private val stockRowHeights = setOf(56f, 52f)

private val menuFragments = listOf("MenuDialogFragment", "MenuFragment")

private val optionalMenuRows =
    listOf(
        "MenuItemKt" to "MenuTextItem",
        "LibraryMenuItemKt" to "LibraryMenuItem",
        "IPProtectionMenuItemKt" to "IPProtectionMenuItem",
        "ExtensionsMenuItemKt" to "NumberedChevronBadge",
    )

private enum class LibraryRow(
    val icon: String,
    val label: String,
    val onClickAt: Int,
) {
    HISTORY("mozac_ic_history_24", "library_history", 2),
    BOOKMARKS("mozac_ic_bookmark_tray_fill_24", "library_bookmarks", 1),
    DOWNLOADS("mozac_ic_download_24", "library_downloads", 3),
    PASSWORDS("mozac_ic_login_24", "browser_menu_passwords", 4),
}

private val switch = Switch(Prefs.FIREFOX_CLASSIC_MENU)

internal val firefoxClassicMenu =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch),
        install = XposedModule::installFirefoxClassicMenu,
    )

private enum class MenuScope { FRAME, GROUP, ROW, NAV, ACCOUNT }

private val menuScope = ThreadLocal<MenuScope?>()

private val fragmentCreatingDialog = ThreadLocal<Any?>()

private class SheetSurface(
    val stock: Long,
    val bright: Long,
    val chevronContainer: Long,
)

private val sheetSurface = ThreadLocal<SheetSurface?>()

private val awaitingFirstGroup = ThreadLocal<Boolean>()

private val badgeAsCheckbox = ThreadLocal<Boolean?>()

private class RoundOutline(
    private val radius: Float,
) : ViewOutlineProvider() {
    override fun getOutline(
        view: View,
        outline: Outline,
    ) = outline.setRoundRect(0, 0, view.width, view.height, radius)
}

private class MenuFragment(
    val onCreateDialog: Method,
    val sheetWidth: Method,
    val behavior: Field,
)

private fun Class<*>.firstDeclared(
    description: String,
    predicate: (Method) -> Boolean,
): Method = declaredMethods.firstOrNull(predicate) ?: throw NoSuchMethodException(description)

private fun Method.takes(
    index: Int,
    type: Class<*>?,
) = parameterTypes.getOrNull(index) == type

private class WindowBindings(
    val fragments: List<MenuFragment>,
    val setOnShowListener: Method,
    val viewRef: Field,
    val maxHeight: Field,
    val draggable: Field,
    val setHideable: Method,
    val gravity: Field,
    val components: Method,
    val settings: Method,
    val shouldUseBottomToolbar: Method,
) {
    fun isBottomToolbar(context: Context): Boolean =
        shouldUseBottomToolbar.invoke(settings.invoke(components.invoke(null, context))) as Boolean

    companion object {
        fun resolve(cl: ClassLoader): WindowBindings {
            val behavior = cl.loadClass(BOTTOM_SHEET_BEHAVIOR)
            val fragments =
                menuFragments.mapNotNull { name ->
                    orNullIfMissing { menuFragment(cl.loadClass("$MENU_PACKAGE.$name")) }
                }
            if (fragments.isEmpty()) {
                throw ClassNotFoundException("$MENU_PACKAGE.MenuDialogFragment")
            }
            val showListener = DialogInterface.OnShowListener::class.java
            val settings = cl.loadClass(FENIX_SETTINGS)
            return WindowBindings(
                fragments = fragments,
                setOnShowListener =
                    Dialog::class.java.requiredMethod("setOnShowListener", showListener),
                viewRef = behavior.requiredField("viewRef"),
                maxHeight = behavior.requiredField("maxHeight"),
                draggable = behavior.requiredField("draggable"),
                setHideable =
                    behavior.requiredMethod("setHideable", Boolean::class.javaPrimitiveType),
                gravity = cl.loadClass(COORDINATOR_PARAMS).getField("gravity"),
                components =
                    cl
                        .loadClass(FENIX_CONTEXT)
                        .requiredMethod("getComponents", Context::class.java),
                settings = cl.loadClass(FENIX_COMPONENTS).requiredMethod("getSettings"),
                shouldUseBottomToolbar = settings.requiredMethod("getShouldUseBottomToolbar"),
            )
        }

        private fun menuFragment(fragment: Class<*>) =
            MenuFragment(
                onCreateDialog = fragment.requiredMethod("onCreateDialog", Bundle::class.java),
                sheetWidth =
                    fragment.firstDeclared("${fragment.simpleName}.calculateMenuSheetWidth") {
                        it.name.startsWith("calculateMenuSheetWidth") &&
                            it.parameterCount == 0 &&
                            it.returnType == Int::class.javaPrimitiveType
                    },
                behavior = fragment.requiredField("bottomSheetBehavior"),
            )
    }
}

private class ComposeBindings(
    val menuItem: Method,
    val menuItemEnabled: Any,
    val rows: List<Method>,
    val mainMenu: Method,
    val toolsGroup: Method,
    val libraryGroup: Method,
    val libraryIcons: List<Int>,
    val libraryLabels: List<Int>,
    val badge: Method,
    val expandable: Method,
    val account: Method?,
    val text: Method,
    val captionStyle: Any,
    val width: Method,
    val defaultMinSize: Method,
    val rectangleShape: Any,
    val menuGroup: Method,
    val menuFrame: Method,
    val sheetHandle: Method,
    val sheet: Method,
    val navigation: Method,
    val navItem: Method,
    val iconListItem: Method,
    val listItemContent: Method,
    val icon: Method,
    val checkbox: Method,
    val checkboxColors: Constructor<*>,
    val transparent: Long,
    val painterResource: Method,
    val stringResource: Method,
    val startReplaceGroup: Method,
    val endReplaceGroup: Method,
    val background: Method,
    val surfaceComposable: Method,
    val spacedAligned: Constructor<*>,
    val divider: Method,
    val consume: Method,
    val localColorScheme: Any,
    val surfaceColor: Field,
    val surfaceBrightColor: Field,
    val surfaceContainerColor: Field,
    val surfaceContainerHighColor: Field,
    val outlineVariantColor: Field,
    val onSurfaceColor: Field,
    val onSurfaceVariantColor: Field,
    val primaryColor: Field,
    val modifierCompanion: Any,
    val wrapContentWidth: Method,
    val size: Method,
    val paddingSymmetric: Method,
    val clip: Method,
    val circleShape: Any,
) {
    fun composerIndex(method: Method) = method.parameterTypes.indexOf(consume.declaringClass)

    companion object {
        fun resolve(cl: ClassLoader): ComposeBindings {
            val composer = cl.loadClass(COMPOSER)
            val colorScheme = cl.loadClass(COLOR_SCHEME)

            fun menuComposable(
                owner: String,
                name: String,
            ) = cl.loadClass("$MENU_COMPOSE.$owner").requiredMethods(name).single()
            val menuItem = menuComposable("MenuItemKt", "MenuItem")
            val icons = cl.loadClass(ICON_RESOURCES)
            val strings = cl.loadClass(FENIX_STRINGS)
            val painter = cl.loadClass(PAINTER)
            val textStyle = cl.loadClass(TEXT_STYLE)
            val composeColor = cl.loadClass(COMPOSE_COLOR)
            return ComposeBindings(
                menuItem = menuItem,
                menuItemEnabled =
                    menuItem.parameterTypes[MENU_ITEM_STATE].enumConstants.single {
                        (it as Enum<*>).name == ENABLED_STATE
                    },
                rows =
                    listOf(menuItem) +
                        optionalMenuRows.mapNotNull { (owner, name) ->
                            orNullIfMissing { menuComposable(owner, name) }
                        },
                mainMenu = menuComposable("MainMenuKt", "MainMenu"),
                toolsGroup = menuComposable("MainMenuKt", "ToolsAndActionsMenuGroup"),
                libraryGroup = menuComposable("MainMenuKt", "LibraryMenuGroup"),
                libraryIcons = LibraryRow.entries.map { icons.requiredField(it.icon).getInt(null) },
                libraryLabels =
                    LibraryRow.entries.map { strings.requiredField(it.label).getInt(null) },
                badge = menuComposable("MenuItemKt", "Badge"),
                expandable = menuComposable("MenuItemKt", "ExpandableMenuItemAnimation"),
                account =
                    orNullIfMissing {
                        menuComposable("header.MozillaAccountMenuItemKt", "MozillaAccountMenuItem")
                    },
                text =
                    cl.loadClass(TEXT_KT).firstDeclared("TextKt.Text") {
                        it.name.startsWith("Text-") && it.takes(0, String::class.java) &&
                            textStyle in it.parameterTypes
                    },
                captionStyle =
                    cl
                        .loadClass(ACORN_TYPOGRAPHY_KT)
                        .requiredField("defaultTypography")
                        .get(null)
                        .let { it?.javaClass?.requiredField("caption")?.get(it) }
                        ?: throw NoSuchFieldException("AcornTypography.caption"),
                width =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.width") {
                        it.name.startsWith("width-") && it.parameterCount == 2
                    },
                defaultMinSize =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.defaultMinSize") {
                        it.name.startsWith("defaultMinSize-") && it.parameterCount == 3
                    },
                rectangleShape =
                    cl.loadClass(RECTANGLE_SHAPE_KT).requiredField("RectangleShape").get(null)
                        ?: throw NoSuchFieldException("RectangleShapeKt.RectangleShape"),
                menuGroup = menuComposable("MenuGroupKt", "MenuGroup"),
                menuFrame = menuComposable("MenuScaffoldKt", "MenuFrame"),
                sheetHandle = menuComposable("MenuDialogBottomSheetKt", "MenuBottomSheetHandle"),
                sheet = menuComposable("MenuDialogBottomSheetKt", "MenuDialogBottomSheet"),
                navigation = menuComposable("MenuNavigationKt", "MenuNavigation"),
                navItem = menuComposable("MenuNavigationKt", "MenuNavItem"),
                iconListItem =
                    cl.loadClass(LIST_ITEM_KT).firstDeclared("ListItemKt.IconListItem") {
                        it.name.startsWith("IconListItem-") &&
                            it.takes(LIST_ITEM_DESCRIPTION, String::class.java) &&
                            it.takes(LIST_ITEM_HEIGHT, Float::class.javaPrimitiveType)
                    },
                listItemContent =
                    cl.loadClass(LIST_ITEM_KT).firstDeclared("ListItemKt.ListItemContent") {
                        it.name.startsWith("ListItemContent-") &&
                            it.takes(LIST_ITEM_CONTENT_MAX_LINES, Int::class.javaPrimitiveType)
                    },
                icon =
                    cl.loadClass(ICON_KT).firstDeclared("IconKt.Icon") {
                        it.name.startsWith("Icon-") && painter in it.parameterTypes &&
                            Long::class.javaPrimitiveType in it.parameterTypes
                    },
                checkbox = cl.loadClass(CHECKBOX_KT).requiredMethods("Checkbox").single(),
                checkboxColors =
                    cl.loadClass(CHECKBOX_COLORS).declaredConstructors.singleOrNull {
                        it.parameterTypes.all { type -> type == Long::class.javaPrimitiveType }
                    } ?: throw NoSuchMethodException("CheckboxColors.<init>"),
                transparent = composeColor.requiredField("Transparent").getLong(null),
                painterResource =
                    cl.loadClass(PAINTER_RESOURCES).requiredMethod(
                        "painterResource",
                        Int::class.javaPrimitiveType,
                        composer,
                        Int::class.javaPrimitiveType,
                    ),
                stringResource =
                    cl
                        .loadClass(STRING_RESOURCES)
                        .requiredMethod("stringResource", Int::class.javaPrimitiveType, composer),
                startReplaceGroup =
                    composer.requiredMethod("startReplaceGroup", Int::class.javaPrimitiveType),
                endReplaceGroup = composer.requiredMethod("endReplaceGroup"),
                background =
                    cl.loadClass(BACKGROUND_KT).firstDeclared("BackgroundKt.background") {
                        it.name.startsWith("background-") &&
                            it.takes(BACKGROUND_COLOR, Long::class.javaPrimitiveType)
                    },
                surfaceComposable =
                    cl.loadClass(SURFACE_KT).firstDeclared("SurfaceKt.Surface") {
                        it.name.startsWith("Surface-") &&
                            it.takes(SURFACE_COLOR, Long::class.javaPrimitiveType)
                    },
                spacedAligned =
                    cl.loadClass(SPACED_ALIGNED).declaredConstructors.firstOrNull {
                        it.parameterCount == 3 &&
                            it.parameterTypes[0] == Float::class.javaPrimitiveType
                    } ?: throw NoSuchMethodException("Arrangement.SpacedAligned.<init>"),
                divider =
                    cl.loadClass(DIVIDER_KT).firstDeclared("DividerKt.HorizontalDivider") {
                        it.name.startsWith("HorizontalDivider-") && composer in it.parameterTypes
                    },
                consume =
                    composer.firstDeclared("Composer.consume") {
                        it.name == "consume" && it.parameterCount == 1
                    },
                localColorScheme =
                    cl.loadClass(COLOR_SCHEME_KT).requiredField("LocalColorScheme").get(null)
                        ?: throw NoSuchFieldException("ColorSchemeKt.LocalColorScheme"),
                surfaceColor = colorScheme.requiredField("surface"),
                surfaceBrightColor = colorScheme.requiredField("surfaceBright"),
                surfaceContainerColor = colorScheme.requiredField("surfaceContainer"),
                surfaceContainerHighColor = colorScheme.requiredField("surfaceContainerHigh"),
                outlineVariantColor = colorScheme.requiredField("outlineVariant"),
                onSurfaceColor = colorScheme.requiredField("onSurface"),
                onSurfaceVariantColor = colorScheme.requiredField("onSurfaceVariant"),
                primaryColor = colorScheme.requiredField("primary"),
                modifierCompanion =
                    cl.loadClass("$MODIFIER\$Companion").requiredField("\$\$INSTANCE").get(null)
                        ?: throw NoSuchFieldException("Modifier.Companion"),
                wrapContentWidth =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.wrapContentWidth") {
                        it.name.startsWith("wrapContentWidth") && it.parameterCount == 2 &&
                            it.takes(1, Int::class.javaPrimitiveType)
                    },
                size =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.size") {
                        it.name.startsWith("size-") && it.parameterCount == 2 &&
                            it.takes(1, Float::class.javaPrimitiveType)
                    },
                paddingSymmetric =
                    cl.loadClass(PADDING_KT).firstDeclared("PaddingKt.padding") {
                        it.name.startsWith("padding-") && it.parameterCount == 3 &&
                            it.takes(2, Float::class.javaPrimitiveType)
                    },
                clip =
                    cl.loadClass(CLIP_KT).requiredMethod(
                        "clip",
                        cl.loadClass(MODIFIER),
                        cl.loadClass(SHAPE),
                    ),
                circleShape =
                    cl.loadClass(ROUNDED_CORNER_SHAPE_KT).requiredField("CircleShape").get(null)
                        ?: throw NoSuchFieldException("RoundedCornerShapeKt.CircleShape"),
            )
        }
    }
}

private fun View.dp(value: Float) = value * resources.displayMetrics.density

private fun WindowBindings.showAsPopup(
    fragment: Any,
    dialog: Dialog,
) {
    val window = dialog.window ?: return
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if ((window.decorView.background as? ColorDrawable)?.color != Color.TRANSPARENT) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    val behaviorField =
        fragments.firstOrNull { it.behavior.declaringClass.isInstance(fragment) }?.behavior
            ?: return
    val behavior = behaviorField.get(fragment) ?: return
    val sheet = (viewRef.get(behavior) as? WeakReference<*>)?.get() as? View ?: return
    val bottomToolbar = isBottomToolbar(dialog.context)
    setHideable.invoke(behavior, false)
    draggable.setBoolean(behavior, false)
    sheet.animate().cancel()
    sheet.outlineProvider = RoundOutline(sheet.dp(CORNER_DP))
    sheet.clipToOutline = true
    sheet.elevation = 0f
    sheet.setOnApplyWindowInsetsListener { view, insets ->
        view.setPadding(0, 0, 0, 0)
        insets
    }
    (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
        if (gravity.declaringClass.isInstance(params)) {
            gravity.setInt(params, Gravity.TOP or Gravity.END)
        }
        params.marginEnd = sheet.dp(MARGIN_DP).toInt()
        sheet.layoutParams = params
    }
    sheet.viewTreeObserver.addOnPreDrawListener {
        positionSheet(sheet, behavior, bottomToolbar)
        true
    }
    sheet.startAnimation(enterAnimation(bottomToolbar))
    Logger.info("menu shown tweak=$TWEAK bottomToolbar=$bottomToolbar")
}

private fun WindowBindings.positionSheet(
    sheet: View,
    behavior: Any,
    bottomToolbar: Boolean,
) {
    val parentHeight = (sheet.parent as? View)?.height?.takeIf { it > 0 } ?: return
    val insets = sheet.rootWindowInsets
    val statusBar = insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
    val navigationBar = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
    val margin = sheet.dp(MARGIN_DP)
    val bottomGap = if (bottomToolbar) sheet.dp(BOTTOM_GAP_DP) else navigationBar + margin
    val height = parentHeight - statusBar - (margin + bottomGap).toInt()
    if (maxHeight.getInt(behavior) != height) {
        maxHeight.setInt(behavior, height)
        sheet.requestLayout()
    }
    val translation = if (bottomToolbar) -bottomGap else statusBar + margin - sheet.top
    if (sheet.translationY != translation) sheet.translationY = translation
}

private fun enterAnimation(bottomToolbar: Boolean) =
    AnimationSet(true).apply {
        val pivotY = if (bottomToolbar) 1f else 0f
        val relative = ScaleAnimation.RELATIVE_TO_SELF
        addAnimation(ScaleAnimation(0f, 1f, 0f, 1f, relative, 1f, relative, pivotY))
        addAnimation(AlphaAnimation(0f, 1f))
        interpolator = DecelerateInterpolator()
        duration = ENTER_MS
    }

private fun XposedModule.installFirefoxClassicMenu(cl: ClassLoader): Boolean {
    val bindings =
        resolving { WindowBindings.resolve(cl) to ComposeBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val (window, compose) = bindings
    val fragments =
        window.fragments.joinToString(",") { it.onCreateDialog.declaringClass.simpleName }
    Logger.info("resolved tweak=$TWEAK members=$fragments,${compose.iconListItem.name}")
    hookMenuWindow(window)
    hookMenuRows(compose)
    return true
}

private fun XposedModule.hookMenuWindow(window: WindowBindings) {
    window.fragments.forEach { fragment ->
        hook(fragment.onCreateDialog).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            fragmentCreatingDialog.set(chain.thisObject)
            val dialog =
                try {
                    chain.proceed()
                } finally {
                    fragmentCreatingDialog.remove()
                }
            (dialog as? Dialog)?.window?.setWindowAnimations(0)
            dialog
        }
        hook(fragment.sheetWidth).intercept { chain ->
            val stock = chain.proceed() as Int
            if (!switch.enabled) return@intercept stock
            val density = Resources.getSystem().displayMetrics.density
            minOf((MENU_WIDTH_DP * density).toInt(), stock)
        }
    }
    hook(window.setOnShowListener).intercept { chain ->
        val fragment = fragmentCreatingDialog.get() ?: return@intercept chain.proceed()
        val stock = chain.getArg(0) as? DialogInterface.OnShowListener
        val popup =
            DialogInterface.OnShowListener { shown ->
                stock?.onShow(shown)
                runCatching { window.showAsPopup(fragment, shown as Dialog) }.onFailure {
                    Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it)
                }
            }
        chain.proceed(arrayOf<Any?>(popup))
    }
}

private fun <T> within(
    scope: MenuScope,
    block: () -> T,
): T {
    val outer = menuScope.get()
    menuScope.set(scope)
    try {
        return block()
    } finally {
        menuScope.set(outer)
    }
}

private fun brightSurfaceFor(color: Any?): Long? {
    if (!switch.enabled) return null
    val surface = sheetSurface.get() ?: return null
    return surface.bright.takeIf { color == surface.stock }
}

private fun ComposeBindings.withSheetSurface(
    composer: Any?,
    block: () -> Any?,
): Any? {
    val scheme = consume.invoke(composer, localColorScheme)
    val outer = sheetSurface.get()
    sheetSurface.set(
        SheetSurface(
            surfaceColor.getLong(scheme),
            surfaceBrightColor.getLong(scheme),
            surfaceContainerHighColor.getLong(scheme),
        ),
    )
    try {
        return block()
    } finally {
        sheetSurface.set(outer)
    }
}

private fun <T> withCheckboxBadges(
    enabled: Boolean,
    block: () -> T,
): T {
    val outer = badgeAsCheckbox.get()
    badgeAsCheckbox.set(enabled)
    try {
        return block()
    } finally {
        badgeAsCheckbox.set(outer)
    }
}

private fun ComposeBindings.emitGroupDivider(
    composer: Any?,
    changed: Int,
) {
    val first = awaitingFirstGroup.get() == true
    awaitingFirstGroup.set(false)
    if (first || changed and RESTART_CALL_BIT != 0) return
    val color = outlineVariantColor.getLong(consume.invoke(composer, localColorScheme))
    val modifier =
        paddingSymmetric.invoke(
            null,
            modifierCompanion,
            DIVIDER_INSET_DP,
            DIVIDER_PADDING_DP,
        )
    divider.invoke(null, modifier, 0f, color, composer, 0, DIVIDER_THICKNESS_DEFAULT_MASK)
}

private fun ComposeBindings.navButtonModifier(
    stock: Any?,
    container: Long,
): Any? {
    val outer = sheetSurface.get()
    sheetSurface.remove()
    try {
        var modifier = stock ?: modifierCompanion
        modifier = wrapContentWidth.invoke(null, modifier, WRAP_CONTENT_DEFAULTS_MASK)
        modifier = size.invoke(null, modifier, NAV_BUTTON_DP)
        modifier = clip.invoke(null, modifier, circleShape)
        modifier = background.invoke(null, modifier, container, circleShape)
        return paddingSymmetric.invoke(null, modifier, 0f, NAV_BUTTON_ICON_INSET_DP)
    } finally {
        sheetSurface.set(outer)
    }
}

private fun XposedInterface.Chain.proceedReplacing(
    index: Int,
    value: Any?,
): Any? = proceed(args.toTypedArray<Any?>().also { it[index] = value })

private fun disabledContainer(onSurface: Long): Long {
    val rgb = (onSurface ushr COLOR_SHIFT) and RGB_MASK
    return ((DISABLED_CONTAINER_ALPHA.toLong() shl ALPHA_SHIFT) or rgb) shl COLOR_SHIFT
}

private fun ComposeBindings.emitCheckbox(
    checked: Boolean,
    composer: Any?,
) {
    val scheme = consume.invoke(composer, localColorScheme)
    val box = primaryColor.getLong(scheme)
    val outline = onSurfaceVariantColor.getLong(scheme)
    val colors =
        checkboxColors.newInstance(
            surfaceBrightColor.getLong(scheme),
            transparent,
            box,
            transparent,
            box,
            transparent,
            box,
            box,
            outline,
            box,
            outline,
            box,
        )
    checkbox.invoke(
        null,
        checked,
        null,
        null,
        false,
        colors,
        composer,
        0,
        CHECKBOX_MODIFIER_ENABLED_DEFAULT_MASK,
    )
}

private fun zeroOf(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        else -> null
    }

private fun ComposeBindings.emitLibraryRow(
    row: LibraryRow,
    onClick: Any?,
    composer: Any?,
) {
    val args = menuItem.parameterTypes.map(::zeroOf).toTypedArray()
    args[0] = stringResource.invoke(null, libraryLabels[row.ordinal], composer)
    args[1] = painterResource.invoke(null, libraryIcons[row.ordinal], composer, 0)
    args[MENU_ITEM_ON_CLICK] = onClick
    args[composerIndex(menuItem)] = composer
    args[args.lastIndex] = MENU_ITEM_DEFAULTS_EXCEPT_ON_CLICK_MASK
    menuItem.invoke(null, *args)
}

private fun XposedModule.hookMenuRows(compose: ComposeBindings) {
    hook(compose.sheetHandle).intercept { chain ->
        if (switch.enabled) null else chain.proceed()
    }
    hook(compose.mainMenu).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        args[MAIN_MENU_BOTTOM_TOOLBAR] = false
        args[MAIN_MENU_EXPANDED_TOOLBAR] = false
        chain.proceed(args)
    }
    val sheetComposerAt = compose.composerIndex(compose.sheet)
    hook(compose.sheet).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        compose.withSheetSurface(chain.getArg(sheetComposerAt)) { chain.proceed() }
    }
    val navigationComposerAt = compose.composerIndex(compose.navigation)
    hook(compose.navigation).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        compose.withSheetSurface(chain.getArg(navigationComposerAt)) {
            within(MenuScope.NAV) { chain.proceed() }
        }
    }
    val navItemComposerAt = compose.composerIndex(compose.navItem)
    hook(compose.navItem).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        val scheme =
            compose.consume.invoke(
                chain.getArg(navItemComposerAt),
                compose.localColorScheme,
            )
        val disabled = (chain.getArg(1) as? Enum<*>)?.name == "DISABLED"
        val container =
            if (disabled) {
                disabledContainer(compose.onSurfaceColor.getLong(scheme))
            } else {
                compose.surfaceContainerColor.getLong(scheme)
            }
        chain.proceedReplacing(0, compose.navButtonModifier(chain.getArg(0), container))
    }
    val iconTintAt = compose.icon.parameterTypes.indexOf(Long::class.javaPrimitiveType)
    val iconComposerAt = compose.composerIndex(compose.icon)
    hook(compose.icon).intercept { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.NAV) return@intercept chain.proceed()
        val scheme = compose.consume.invoke(chain.getArg(iconComposerAt), compose.localColorScheme)
        if (chain.getArg(iconTintAt) != compose.onSurfaceVariantColor.getLong(scheme)) {
            return@intercept chain.proceed()
        }
        chain.proceedReplacing(iconTintAt, compose.onSurfaceColor.getLong(scheme))
    }
    hook(compose.paddingSymmetric).intercept { chain ->
        val navRow =
            switch.enabled && menuScope.get() == MenuScope.NAV &&
                chain.getArg(1) == STOCK_NAV_ROW_PADDING_DP
        if (!navRow) return@intercept chain.proceed()
        chain.proceedReplacing(1, ICON_ROW_PADDING_DP)
    }
    compose.account?.let { account ->
        hook(account).intercept { chain ->
            if (switch.enabled) within(MenuScope.ACCOUNT) { chain.proceed() } else chain.proceed()
        }
    }
    hook(compose.width).intercept { chain ->
        val accountGap =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(1) == STOCK_ICON_LABEL_GAP_DP
        if (!accountGap) return@intercept chain.proceed()
        chain.proceedReplacing(1, ICON_LABEL_GAP_DP)
    }
    hook(compose.defaultMinSize).intercept { chain ->
        val accountHeight =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(MIN_HEIGHT) == STOCK_ACCOUNT_ROW_HEIGHT_DP
        if (!accountHeight) return@intercept chain.proceed()
        chain.proceedReplacing(MIN_HEIGHT, ROW_HEIGHT_DP)
    }
    val textStyleAt = compose.text.parameterTypes.indexOf(compose.captionStyle.javaClass)
    hook(compose.text).intercept { chain ->
        val accountDescription =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(textStyleAt) === compose.captionStyle
        if (accountDescription) null else chain.proceed()
    }
    hook(compose.background).intercept { chain ->
        val chevronContainer =
            switch.enabled && menuScope.get() == MenuScope.ROW &&
                chain.getArg(BACKGROUND_SHAPE) !== compose.rectangleShape &&
                chain.getArg(BACKGROUND_COLOR) == sheetSurface.get()?.chevronContainer
        if (!chevronContainer) return@intercept chain.proceed()
        chain.proceedReplacing(BACKGROUND_COLOR, compose.transparent)
    }
    compose.rows.forEach { row ->
        val rowComposerAt = compose.composerIndex(row)
        hook(row).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            compose.withSheetSurface(chain.getArg(rowComposerAt)) {
                within(MenuScope.ROW) { chain.proceed() }
            }
        }
    }
    hook(compose.menuFrame).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        awaitingFirstGroup.set(true)
        try {
            within(MenuScope.FRAME) {
                chain.proceedReplacing(MENU_FRAME_CONTENT_MODIFIER, compose.modifierCompanion)
            }
        } finally {
            awaitingFirstGroup.remove()
        }
    }
    val groupComposerAt = compose.composerIndex(compose.menuGroup)
    hook(compose.menuGroup).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        compose.emitGroupDivider(chain.getArg(groupComposerAt), chain.args.last() as Int)
        within(MenuScope.GROUP) { chain.proceed() }
    }
    val libraryComposerAt = compose.composerIndex(compose.libraryGroup)
    hook(compose.libraryGroup).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        val composer = chain.getArg(libraryComposerAt)
        compose.emitGroupDivider(composer, chain.args.last() as Int)
        compose.startReplaceGroup.invoke(composer, LIBRARY_ROWS_GROUP_KEY)
        LibraryRow.entries.forEach {
            compose.emitLibraryRow(
                it,
                chain.getArg(it.onClickAt),
                composer,
            )
        }
        compose.endReplaceGroup.invoke(composer)
        null
    }
    hook(compose.toolsGroup).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        try {
            withCheckboxBadges(true) { chain.proceed() }
        } finally {
            awaitingFirstGroup.set(false)
        }
    }
    hook(compose.expandable).intercept { chain ->
        if (switch.enabled) withCheckboxBadges(false) { chain.proceed() } else chain.proceed()
    }
    hook(compose.menuItem).intercept { chain ->
        val checkboxRow =
            switch.enabled &&
                badgeAsCheckbox.get() == true &&
                chain.getArg(MENU_ITEM_AFTER_CONTENT) != null &&
                (chain.getArg(MENU_ITEM_STATE) as? Enum<*>)?.name == ACTIVE_STATE
        if (!checkboxRow) return@intercept chain.proceed()
        chain.proceedReplacing(MENU_ITEM_STATE, compose.menuItemEnabled)
    }
    val badgeComposerAt = compose.composerIndex(compose.badge)
    hook(compose.badge).intercept { chain ->
        if (!switch.enabled || badgeAsCheckbox.get() != true) return@intercept chain.proceed()
        val checked = (chain.getArg(1) as? Enum<*>)?.name == ACTIVE_STATE
        compose.emitCheckbox(checked, chain.getArg(badgeComposerAt))
        null
    }
    hook(compose.spacedAligned).intercept { chain ->
        val spacing = chain.getArg(0) as Float
        val scope = if (switch.enabled) menuScope.get() else null
        val replacement =
            when (scope) {
                MenuScope.FRAME -> 0f.takeIf { spacing == FRAME_SPACING_DP }
                MenuScope.GROUP -> 0f.takeIf { spacing == GROUP_SPACING_DP }
                MenuScope.NAV -> 0f.takeIf { spacing == NAV_SPACING_DP }
                MenuScope.ROW -> ICON_LABEL_GAP_DP.takeIf { spacing == STOCK_ICON_LABEL_GAP_DP }
                MenuScope.ACCOUNT, null -> null
            } ?: return@intercept chain.proceed()
        chain.proceedReplacing(0, replacement)
    }
    hook(compose.iconListItem).intercept { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.ROW) return@intercept chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        args[LIST_ITEM_DESCRIPTION] = null
        if (args[LIST_ITEM_HEIGHT] in stockRowHeights) args[LIST_ITEM_HEIGHT] = ROW_HEIGHT_DP
        chain.proceed(args)
    }
    hook(compose.listItemContent).intercept { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.ROW) return@intercept chain.proceed()
        chain.proceedReplacing(LIST_ITEM_CONTENT_MAX_LINES, SINGLE_LINE)
    }
    listOf(compose.background to BACKGROUND_COLOR, compose.surfaceComposable to SURFACE_COLOR)
        .forEach { (method, colorAt) ->
            hook(method).intercept { chain ->
                val surface =
                    brightSurfaceFor(chain.getArg(colorAt)) ?: return@intercept chain.proceed()
                chain.proceedReplacing(colorAt, surface)
            }
        }
}
