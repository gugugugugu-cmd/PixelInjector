package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.theme.AppText

private const val LINES_PER_GROUP = 2
private val FixMarkIndent = 14.dp
private const val SHORT_ID_LENGTH = 8
private val FixMarkIconSize = 16.dp

@Composable
fun UpdateCard(
    release: Release,
    fixes: Map<String, List<SentReport>>,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val newLines = shownLines(release.notesNew, fixes)
    val fixedLines = shownLines(release.notesFixed, fixes)
    val otherLines = release.notesOther.filter { it in fixes }
    val more = release.notesNew.size + release.notesFixed.size + release.notesOther.size - newLines.size - fixedLines.size - otherLines.size
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.update_card_title, stringResource(R.string.app_name), release.versionName),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    NoteGroup(stringResource(R.string.notes_new), newLines, fixes)
                    NoteGroup(stringResource(R.string.notes_fixed), fixedLines, fixes)
                    NoteGroup(stringResource(R.string.notes_other), otherLines, fixes)
                    if (more > 0) Text(pluralStringResource(R.plurals.notes_more, more, more), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(
                    onClick = onLater,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.later), color = scheme.onPrimaryContainer) }
                Button(
                    onClick = onUpdate,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = scheme.onPrimaryContainer,
                            contentColor = scheme.primaryContainer,
                        ),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.update), style = AppText.button)
                }
            }
        }
    }
}

private fun shownLines(
    lines: List<String>,
    fixes: Map<String, List<SentReport>>,
): List<String> = lines.take(LINES_PER_GROUP) + lines.drop(LINES_PER_GROUP).filter { it in fixes }

@Composable
private fun NoteGroup(
    title: String,
    lines: List<String>,
    fixes: Map<String, List<SentReport>>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        lines.forEach { line ->
            Text("•  $line", style = MaterialTheme.typography.bodyMedium)
            fixes[line]?.let { FixMark(it) }
        }
    }
}

@Composable
fun FixMark(reports: List<SentReport>) {
    Row(
        Modifier.padding(start = FixMarkIndent),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(FixMarkIconSize))
        Text(
            stringResource(R.string.fixes_your_report, reports.joinToString(" ") { it.short ?: it.id.take(SHORT_ID_LENGTH) }),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
