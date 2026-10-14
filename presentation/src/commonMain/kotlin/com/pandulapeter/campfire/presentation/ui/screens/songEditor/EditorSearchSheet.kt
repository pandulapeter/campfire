/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_find_replace
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.song_editor_find
import com.pandulapeter.campfire.presentation.resources.song_editor_find_and_replace
import com.pandulapeter.campfire.presentation.resources.song_editor_find_match_case
import com.pandulapeter.campfire.presentation.resources.song_editor_find_match_count
import com.pandulapeter.campfire.presentation.resources.song_editor_find_next
import com.pandulapeter.campfire.presentation.resources.song_editor_find_no_matches
import com.pandulapeter.campfire.presentation.resources.song_editor_find_previous
import com.pandulapeter.campfire.presentation.resources.song_editor_find_query
import com.pandulapeter.campfire.presentation.resources.song_editor_find_replacement
import com.pandulapeter.campfire.presentation.resources.song_editor_replace
import com.pandulapeter.campfire.presentation.resources.song_editor_replace_all
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.appBar.TruncateSearchQuery
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.dialogs.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import org.jetbrains.compose.resources.painterResource

/**
 * The editor's Find and Find and replace, one sheet with the replacement's field and buttons coming and going, so that
 * Ctrl / Cmd + F and R switch between the two without the sheet sliding away and back.
 *
 * The previous and next match are the header's actions, since they are what the sheet is reached for every time and
 * the one control that has to stay in sight over a long song; everything else scrolls. The matches are drawn in the
 * field behind the sheet and the current one is scrolled into view there (see [ChordProTextField]), into the part of the
 * pane above the sheet's top edge, which the sheet reports as [EditorSearch.sheetTop].
 */
@Composable
internal fun EditorSearchSheet(
    search: EditorSearch,
    onDismiss: () -> Unit,
) {
    // The sheet is the editor's own rather than a dialog of the view model's, so it counts itself in as an overlay for as
    // long as it is up: that is what keeps Escape on the desktop and the browser's back button from going back from the
    // editor behind it, leaving the key to the sheet, which closes itself.
    val overlayState = LocalOverlayState.current
    DisposableEffect(overlayState) {
        overlayState.onMenuOpened()
        onDispose { overlayState.onMenuClosed() }
    }
    val isReplacing = search.mode == EditorSearchMode.REPLACE
    val hasMatches = search.matchCount > 0
    CampfireBottomSheet(
        title = stringResource(if (isReplacing) Res.string.song_editor_find_and_replace else Res.string.song_editor_find),
        actions = {
            IconButton(
                enabled = hasMatches,
                onClick = search::selectPrevious,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_move_up),
                    contentDescription = stringResource(Res.string.song_editor_find_previous),
                )
            }
            IconButton(
                modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                enabled = hasMatches,
                onClick = search::selectNext,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_move_down),
                    contentDescription = stringResource(Res.string.song_editor_find_next),
                )
            }
        },
        // Only while the search is open: the sheet's window can be laid out once more after the close, and a top left behind
        // by it would keep the room the field makes after its last line for a sheet that is gone.
        onTopMoved = { if (search.mode != null) search.sheetTop = it },
        onDismiss = onDismiss,
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        val keyboardController = LocalSoftwareKeyboardController.current
        val queryFocusRequester = remember { FocusRequester() }
        val replacementFocusRequester = remember { FocusRequester() }
        // A sheet opened to replace what was selected in the text has its query already, so there the caret goes to
        // what it is replaced with; everywhere else it goes to the query, since that is what is typed first.
        LaunchedEffect(isReplacing) {
            (if (isReplacing && search.query.text.isNotEmpty()) replacementFocusRequester else queryFocusRequester).requestFocus()
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(top = 8.dp)
                .editorSearchShortcuts(
                    onFind = { search.open(EditorSearchMode.FIND) },
                    onReplace = { search.open(EditorSearchMode.REPLACE) },
                ),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .focusRequester(queryFocusRequester),
                state = search.query,
                label = { Text(stringResource(Res.string.song_editor_find_query)) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null) },
                trailingIcon = rememberClearTextButton(isVisible = search.query.text.isNotEmpty(), onClear = { search.query.clearText() }),
                // Always there, empty or not, so that the fields under it do not move as the first letter is typed.
                supportingText = {
                    Text(
                        when {
                            hasMatches -> stringResource(Res.string.song_editor_find_match_count, search.currentNumber, search.matchCount)
                            search.query.text.isNotEmpty() -> stringResource(Res.string.song_editor_find_no_matches)
                            else -> ""
                        },
                    )
                },
                inputTransformation = TruncateSearchQuery,
                lineLimits = TextFieldLineLimits.SingleLine,
                // Autocorrect has no dictionary for "[Am]" and "{start_of_verse}", see ChordProTextField.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = if (isReplacing) ImeAction.Next else ImeAction.Search),
                onKeyboardAction = if (isReplacing) null else KeyboardActionHandler { search.selectNext() },
            )
            AnimatedVisibility(visible = isReplacing) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 8.dp)
                        .focusRequester(replacementFocusRequester),
                    state = search.replacement,
                    label = { Text(stringResource(Res.string.song_editor_find_replacement)) },
                    leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_find_replace), contentDescription = null) },
                    trailingIcon = rememberClearTextButton(isVisible = search.replacement.text.isNotEmpty(), onClear = { search.replacement.clearText() }),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    onKeyboardAction = KeyboardActionHandler { keyboardController?.hide() },
                )
            }
            CheckboxListItem(
                title = stringResource(Res.string.song_editor_find_match_case),
                isChecked = search.isMatchCase,
                horizontalInset = 8.dp,
                onCheckedChange = { search.isMatchCase = it },
            )
            AnimatedVisibility(visible = isReplacing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        enabled = hasMatches,
                        onClick = search::replaceCurrent,
                    ) {
                        Text(stringResource(Res.string.song_editor_replace))
                    }
                    Button(
                        enabled = hasMatches,
                        onClick = search::replaceAll,
                    ) {
                        Text(stringResource(Res.string.song_editor_replace_all))
                    }
                }
            }
        }
    }
}
