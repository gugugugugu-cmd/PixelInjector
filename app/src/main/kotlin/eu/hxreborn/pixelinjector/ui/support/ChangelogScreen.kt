package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.LocalDateStyle
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHANGELOG_ASSET = "CHANGELOG.md"
private val CardRadius = 20.dp
private val CardPadding = 16.dp
private val ChipRadius = 8.dp
private val ScrollbarGutter = 12.dp

@Composable
fun rememberChangelog(): List<Release>? {
    val context = LocalContext.current
    return produceState<List<Release>?>(null) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    Release.changelog(
                        context.assets
                            .open(CHANGELOG_ASSET)
                            .bufferedReader()
                            .use { it.readText() },
                    )
                }.getOrDefault(emptyList())
            }
    }.value
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val loaded = rememberChangelog()
    val releases = loaded.orEmpty().distinctBy { it.versionName }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    DetailScaffold(
        title = stringResource(R.string.changelog),
        subtitle = loaded?.let { pluralStringResource(R.plurals.changelog_count, releases.size, releases.size) },
        onBack = onBack,
        contentPadding = PaddingValues(0.dp),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                contentPadding =
                    PaddingValues(
                        start = readableGutter(),
                        end = readableGutter() + ScrollbarGutter,
                        top = 8.dp,
                        bottom = navBottom + 88.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(releases, key = { it.versionName }) { ReleaseCard(it) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = listState.canScrollBackward || listState.canScrollForward,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = navBottom + 16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (atEnd) listState.scrollToItem(0) else listState.scrollToItem(releases.lastIndex, Int.MAX_VALUE)
                        }
                    },
                ) {
                    Icon(
                        if (atEnd) Icons.Outlined.VerticalAlignTop else Icons.Outlined.VerticalAlignBottom,
                        contentDescription = stringResource(if (atEnd) R.string.log_jump_top else R.string.log_jump_bottom),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(release.versionName, style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    LocalDateStyle.current.formatIso(release.publishedAt).orEmpty(),
                    style = AppText.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (release.versionName == BuildConfig.VERSION_NAME) InstalledChip()
            }
            ReleaseNotes(release)
        }
    }
}

@Composable
private fun InstalledChip() {
    Surface(shape = RoundedCornerShape(ChipRadius), color = MaterialTheme.colorScheme.primary) {
        Text(
            stringResource(R.string.changelog_installed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReleaseNotes(release: Release) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Notes(stringResource(R.string.notes_new), release.notesNew, emptyMap())
        Notes(stringResource(R.string.notes_fixed), release.notesFixed, emptyMap())
        Notes(stringResource(R.string.notes_other), release.notesOther, emptyMap())
    }
}

@Composable
fun WhatsNewSheet(
    onFullChangelog: () -> Unit,
    onClose: () -> Unit,
) {
    val releases = rememberChangelog()
    val installed = releases?.firstOrNull { it.versionName == BuildConfig.VERSION_NAME }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME),
            installed?.let { LocalDateStyle.current.formatIso(it.publishedAt) }?.let { stringResource(R.string.whats_new_released, it) },
        )
        when {
            installed != null -> {
                ReleaseNotes(installed)
            }

            releases != null -> {
                Text(
                    stringResource(R.string.whats_new_missing),
                    style = AppText.tileSupporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SheetActions {
            TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.close), style = AppText.button)
            }
            FilledTonalButton(onClick = onFullChangelog, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.changelog_see_full), style = AppText.button)
            }
        }
    }
}
