package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single choice between a handful of [options], rendered as a segmented button row.
 */
@Composable
internal fun <T> SegmentedChoice(
    modifier: Modifier = Modifier,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit
) = SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)
) {
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = value == selected,
            onClick = { onSelected(value) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}
