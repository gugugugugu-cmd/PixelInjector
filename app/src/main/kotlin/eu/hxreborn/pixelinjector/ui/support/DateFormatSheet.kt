package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.DateStyle
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar
import java.time.LocalDate

private val radioRole = Modifier.semantics { role = Role.RadioButton }

@Composable
fun DateFormatSheet(
    selected: DateStyle,
    onSelect: (DateStyle) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sample = remember { LocalDate.now() }
    val listState = rememberLazyListState()
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
        SheetHeader(stringResource(R.string.date_format))
        LazyColumn(state = listState, modifier = Modifier.verticalScrollbar(listState)) {
            tileGroup(DateStyle.entries, key = { it.name }) { style, shape ->
                val checked = style == selected
                Tile(
                    shape = shape,
                    checked = checked,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSelect(style)
                    },
                    modifier = radioRole,
                ) {
                    RadioButton(selected = checked, onClick = null)
                    TileText(stringResource(style.label), style.format(sample), supportingStyle = AppText.meta)
                }
            }
        }
    }
}
