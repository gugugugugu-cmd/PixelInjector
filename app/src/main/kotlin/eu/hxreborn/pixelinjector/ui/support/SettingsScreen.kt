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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
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
private val DotSize = 8.dp
private val ChipHeight = 28.dp

class SettingsActions(
    val onCheck: () -> Unit,
    val onUpdate: () -> Unit,
    val onSendReport: () -> Unit,
    val onReports: () -> Unit,
    val onShareHookStatus: (Boolean) -> Unit,
    val onHideUnavailable: (Boolean) -> Unit,
    val onModuleLog: () -> Unit,
    val onGitHub: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SupportState,
    hideUnavailable: Boolean,
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
            Group(stringResource(R.string.settings_support), 3) { index, shape ->
                when (index) {
                    0 -> {
                        VersionTile(state, shape, actions)
                    }

                    1 -> {
                        Tile(shape = shape, onClick = actions.onSendReport) {
                            TileLeading(Icons.Outlined.BugReport)
                            TileText(stringResource(R.string.send_report), stringResource(R.string.send_report_summary))
                            Chevron()
                        }
                    }

                    else -> {
                        ReportsTile(state, shape, actions.onReports)
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_dashboard), 1) { _, shape ->
                SwitchTile(
                    shape,
                    Icons.Outlined.VisibilityOff,
                    stringResource(R.string.hide_unavailable),
                    stringResource(R.string.hide_unavailable_summary),
                    hideUnavailable,
                    actions.onHideUnavailable,
                )
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
            Group(stringResource(R.string.settings_about), if (framework != null) 3 else 2) { index, shape ->
                if (index == 2 && framework != null) {
                    Tile(shape = shape) {
                        TileLeading(Icons.Outlined.Extension)
                        TileText(stringResource(R.string.settings_framework), framework, supportingStyle = AppText.meta)
                    }
                } else if (index == 0) {
                    Tile(shape = shape, onClick = actions.onModuleLog) {
                        TileLeading(Icons.AutoMirrored.Outlined.Notes)
                        TileText(stringResource(R.string.module_log), stringResource(R.string.module_log_summary))
                        Chevron()
                    }
                } else {
                    Tile(shape = shape, onClick = actions.onGitHub) {
                        TileLeading(ImageVector.vectorResource(R.drawable.ic_github))
                        TileText(stringResource(R.string.source_on_github), ReportConfig.repo, supportingStyle = AppText.meta)
                        Box(Modifier.width(ChevronSlot), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
        TileLeading(Icons.Outlined.Info)
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
            supportingColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
        TileLeading(Icons.Outlined.Forum)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.your_reports), style = AppText.tileTitle)
                if (state.needsReply) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(DotSize)) {}
                }
            }
            Text(
                if (state.conversations.isEmpty()) {
                    stringResource(
                        R.string.reports_none,
                    )
                } else {
                    pluralStringResource(R.plurals.reports_count, state.conversations.size, state.conversations.size)
                },
                style = AppText.tileSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val waiting = state.conversations.count { it.status == ReportStatus.NEED_INFO && !it.noteSeen }
            if (waiting > 0) {
                Text(
                    pluralStringResource(R.plurals.reports_need_reply, waiting, waiting),
                    style = AppText.tileSupporting,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Chevron()
    }
}

@Composable
fun StatusChip(report: SentReport) {
    val style = statusStyle(report)
    Surface(shape = RoundedCornerShape(8.dp), color = style.container, contentColor = style.content) {
        Row(
            Modifier.heightIn(min = ChipHeight).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(style.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
