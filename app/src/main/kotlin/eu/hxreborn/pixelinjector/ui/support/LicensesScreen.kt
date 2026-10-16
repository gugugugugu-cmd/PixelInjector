package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val listState = rememberLazyListState()
    DetailScaffold(
        title = stringResource(R.string.licenses),
        onBack = onBack,
        contentPadding = PaddingValues(0.dp),
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize().padding(padding).verticalScrollbar(listState),
            lazyListState = listState,
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        )
    }
}
