package eu.hxreborn.pixelinjector.ui.betterfox

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.Console
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.reload.ButtonLabel
import eu.hxreborn.pixelinjector.ui.reload.ReloadStatus
import eu.hxreborn.pixelinjector.ui.reload.ResultBadge
import eu.hxreborn.pixelinjector.ui.reload.formatReloadLog
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar

private val ConsoleHeight = 240.dp
private val StepStart = 12.dp
private val StepEnd = 12.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxRunSheet(
    run: BetterfoxRun,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val done by rememberUpdatedState(run.done)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || done },
        )
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val seen = remember { mutableStateSetOf<Int>() }
    LaunchedEffect(run.finished, run.done) {
        run.steps.forEachIndexed { index, step ->
            if (step.status != null && step.status != ReloadStatus.RELOADING && seen.add(index)) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
        if (run.done) haptic.performHapticFeedback(if (run.failed) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
    }
    val title = stringResource(if (run.kind == RunKind.BACKUP) R.string.betterfox_backing_up else R.string.betterfox_restoring)
    ModalBottomSheet(
        onDismissRequest = { if (run.done) onClose() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = run.done),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.statusBarsPadding().fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxHeight().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(title, style = AppText.progressTitle)
                    Text(
                        "[${run.current}/${run.total}]",
                        style = AppText.progressCounter,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val progress by animateFloatAsState(
                    targetValue = if (run.total == 0) 0f else run.finished.toFloat() / run.total,
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    label = "betterfox-progress",
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            val scroll = rememberScrollState()
            LaunchedEffect(run.done) { if (run.done) scroll.animateScrollTo(scroll.maxValue) }
            Column(Modifier.weight(1f).verticalScrollbar(scroll).verticalScroll(scroll)) {
                Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                    run.steps.forEachIndexed { index, step -> StepTile(step, tileShape(run.steps.size, index)) }
                }
                Console(run.lines, Modifier.height(ConsoleHeight).padding(top = SectionGap))
            }
            SheetActions {
                if (!run.done) {
                    TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                        ButtonLabel(Icons.Outlined.Stop, stringResource(R.string.cancel))
                    }
                }
                TextButton(onClick = onClose, shapes = ButtonDefaults.shapes(), enabled = run.done) {
                    ButtonLabel(Icons.Outlined.Close, stringResource(R.string.close))
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                    val clip = ClipData.newPlainText("PixelInjector $title", formatReloadLog(run.lines))
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                }) { ButtonLabel(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
            }
        }
    }
}

private fun StepIcon.vector(): ImageVector =
    when (this) {
        StepIcon.GROUP -> Icons.Outlined.Tune
        StepIcon.OVERRIDES -> Icons.Outlined.Edit
        StepIcon.FILE -> Icons.Outlined.Description
        StepIcon.RESTART -> Icons.Outlined.Sync
    }

@Composable
private fun StepTile(
    step: RunStep,
    shape: RoundedCornerShape,
) {
    val scheme = MaterialTheme.colorScheme
    val transition = updateTransition(targetState = step.status, label = "step")
    Tile(shape = shape, startPadding = StepStart, endPadding = StepEnd) {
        LeadIcon(step.icon.vector())
        Column(Modifier.weight(1f)) {
            Text(step.title, style = AppText.tileTitle, color = if (step.status == null) scheme.onSurfaceVariant else scheme.onSurface)
            Text(
                step.detail,
                style = AppText.meta,
                color = if (step.status == ReloadStatus.FAILED) scheme.error else scheme.onSurfaceVariant,
            )
        }
        ResultBadge(transition)
    }
}
