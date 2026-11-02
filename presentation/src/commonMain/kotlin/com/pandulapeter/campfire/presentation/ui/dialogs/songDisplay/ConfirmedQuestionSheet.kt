/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.confirm
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetConfirmButton
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor

/**
 * A question asked over a song until it is answered ([SongLayoutSheet], [ChordNamesSheet]): not dismissible, so that a
 * stray tap does not put it away, and closed by its Confirm, which [onConfirm] answers the question with. The title may
 * wrap to as many lines as it needs, since the header holds nothing else and a question cut short is no question, and
 * Confirm sits in a row pinned under the scrolling [content] rather than in the header, where it would take the
 * title's width on a narrow phone and could not be missed at the end of a long one.
 */
@Composable
internal fun ConfirmedQuestionSheet(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = CampfireBottomSheet(
    title = title,
    titleMaxLines = Int.MAX_VALUE,
    isDismissible = false,
    onDismiss = onDismiss,
) { contentPadding ->
    val sheet = this
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .fadingVerticalEdges(scrollState, sheetContainerColor())
            .bounceVerticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        content = content,
    )
    // The confirm button pads itself 8 dp from the end, which with this row's makes the sheet's 16.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        BottomSheetConfirmButton(
            onClick = {
                onConfirm()
                sheet.close()
            },
        ) { Text(stringResource(Res.string.confirm)) }
    }
}
