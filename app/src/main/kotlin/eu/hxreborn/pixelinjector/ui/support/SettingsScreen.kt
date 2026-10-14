package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.ReleaseCheck
import eu.hxreborn.pixelinjector.support.ReportConfig
import eu.hxreborn.pixelinjector.support.ReportStatus
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SectionHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.TileLeading
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText

private val ChevronSlot = 40.dp

class SettingsActions(
    val onCheck: () -> Unit,
    val onUpdate: () -> Unit,
    val onSendReport: () -> Unit,
    val onReports: () -> Unit,
    val onShareHookStatus: (Boolean) -> Unit,
    val onAutoCheckUpdates: (Boolean) -> Unit,
    val onHideUnavailable: (Boolean) -> Unit,
    val onShowPackages: (Boolean) -> Unit,
    val onModuleLog: () -> Unit,
    val onGitHub: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SupportState,
    hideUnavailable: Boolean,
    showPackages: Boolean,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    DetailScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = readableGutter(), vertical = 16.dp),
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Group(stringResource(R.string.settings_updates), 2) { index, shape ->
                if (index == 0) {
                    VersionTile(state, shape, actions)
                } else {
                    SwitchTile(
                        shape,
                        Icons.Outlined.Autorenew,
                        stringResource(R.string.auto_check_updates),
                        stringResource(R.string.auto_check_updates_summary),
                        state.autoCheckUpdates,
                        actions.onAutoCheckUpdates,
                    )
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_support), 3) { index, shape ->
                when (index) {
                    0 -> {
                        Tile(shape = shape, onClick = actions.onSendReport) {
                            TileLeading(Icons.Outlined.BugReport)
                            TileText(stringResource(R.string.send_report), stringResource(R.string.send_report_summary))
                            Chevron()
                        }
                    }

                    1 -> {
                        ReportsTile(state, shape, actions.onReports)
                    }

                    else -> {
                        Tile(shape = shape, onClick = actions.onModuleLog) {
                            TileLeading(Icons.AutoMirrored.Outlined.Notes)
                            TileText(stringResource(R.string.module_log), stringResource(R.string.module_log_summary))
                            Chevron()
                        }
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_dashboard), 2) { index, shape ->
                if (index == 0) {
                    SwitchTile(
                        shape,
                        Icons.Outlined.VisibilityOff,
                        stringResource(R.string.hide_unavailable),
                        stringResource(R.string.hide_unavailable_summary),
                        hideUnavailable,
                        actions.onHideUnavailable,
                    )
                } else {
                    SwitchTile(
                        shape,
                        Icons.Outlined.Code,
                        stringResource(R.string.show_packages),
                        stringResource(R.string.show_packages_summary),
                        showPackages,
                        actions.onShowPackages,
                    )
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_privacy), 1) { _, shape ->
                SwitchTile(
                    shape,
                    Icons.Outlined.Insights,
                    stringResource(R.string.share_hook_status),
                    stringResource(R.string.share_hook_status_summary),
                    state.shareHookStatus,
                    actions.onShareHookStatus,
                )
            }
            Spacer(Modifier.height(SectionGap))
            val framework = state.framework
            Group(stringResource(R.string.settings_about), if (framework != null) 2 else 1) { index, shape ->
                if (index == 0) {
                    Tile(shape = shape, onClick = actions.onGitHub) {
                        TileLeading(ImageVector.vectorResource(R.drawable.ic_github_inset))
                        TileText(stringResource(R.string.source_on_github), ReportConfig.repo, supportingStyle = AppText.meta)
                        Box(Modifier.width(ChevronSlot), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (framework != null) {
                    Tile(shape = shape) {
                        TileLeading(Icons.Outlined.Extension)
                        TileText(stringResource(R.string.settings_framework), framework, supportingStyle = AppText.meta)
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(
    title: String,
    count: Int,
    content: @Composable (Int, RoundedCornerShape) -> Unit,
) {
    SectionHeader(title)
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        repeat(count) { content(it, tileShape(count, it)) }
    }
}

@Composable
private fun SwitchTile(
    shape: RoundedCornerShape,
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Tile(
        shape = shape,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { role = Role.Switch },
    ) {
        TileLeading(icon)
        TileText(title, summary)
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun Chevron() {
    Box(Modifier.width(ChevronSlot), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionTile(
    state: SupportState,
    shape: RoundedCornerShape,
    actions: SettingsActions,
) {
    val release = state.release
    val available = state.available
    val onClick =
        when {
            available != null -> actions.onUpdate
            release is ReleaseCheck.Checking -> null
            else -> actions.onCheck
        }
    Tile(shape = shape, onClick = onClick) {
        TileLeading(Icons.Outlined.SystemUpdate)
        val supporting =
            when {
                available != null -> {
                    stringResource(R.string.update_available_short, available.versionName)
                }

                release is ReleaseCheck.Checking -> {
                    stringResource(R.string.update_checking)
                }

                release is ReleaseCheck.Failed -> {
                    release.lastCheckedAt?.let { stringResource(R.string.update_check_failed, shortTime(it)) }
                        ?: stringResource(R.string.update_check_failed_never)
                }

                release is ReleaseCheck.Done -> {
                    stringResource(R.string.update_up_to_date, shortTime(release.checkedAt))
                }

                else -> {
                    stringResource(R.string.update_never_checked)
                }
            }
        val failed = release is ReleaseCheck.Failed && available == null
        TileText(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            supporting,
            supportingColor =
                when {
                    failed -> MaterialTheme.colorScheme.error
                    available != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        when {
            available != null -> {
                FilledTonalButton(onClick = actions.onUpdate, modifier = Modifier.clearAndSetSemantics { }) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.update))
                }
            }

            release is ReleaseCheck.Checking -> {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
            }

            else -> {
                IconButton(onClick = actions.onCheck, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.update_check),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsTile(
    state: SupportState,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Tile(shape = shape, onClick = onClick) {
        TileLeading(Icons.Outlined.Inbox)
        val count =
            if (state.conversations.isEmpty()) {
                stringResource(R.string.reports_none)
            } else {
                pluralStringResource(R.plurals.reports_count, state.conversations.size, state.conversations.size)
            }
        val waiting = state.conversations.count { it.status == ReportStatus.NEED_INFO && !it.noteSeen }
        val reply = if (waiting > 0) pluralStringResource(R.plurals.reports_need_reply, waiting, waiting) else null
        val tertiary = MaterialTheme.colorScheme.tertiary
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.your_reports), style = AppText.tileTitle)
            Text(
                buildAnnotatedString {
                    append(count)
                    reply?.let {
                        append("  ")
                        withStyle(SpanStyle(color = tertiary, fontWeight = FontWeight.Medium)) { append(it) }
                    }
                },
                style = AppText.tileSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Chevron()
    }
}
