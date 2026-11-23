/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pageLink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.page_link_app_offer
import com.pandulapeter.campfire.presentation.resources.page_link_get_app
import com.pandulapeter.campfire.presentation.resources.page_link_intro
import com.pandulapeter.campfire.presentation.resources.page_link_open
import com.pandulapeter.campfire.presentation.resources.page_link_paste
import com.pandulapeter.campfire.presentation.resources.page_link_step_copy
import com.pandulapeter.campfire.presentation.resources.page_link_step_open
import com.pandulapeter.campfire.presentation.resources.page_link_step_paste
import com.pandulapeter.campfire.presentation.resources.page_link_title
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.LocalIsSheetClosing
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor

/**
 * A web page Campfire cannot read itself, brought over by hand: the steps for opening [address] in the browser, copying
 * the page, and pasting it here, which becomes a new song as a pasted page does; on the web ([isAppOffered]) also the
 * app, which reads the song from the link alone. It is the one answer wherever a page's address goes unread: a platform
 * with no web view (the web, a desktop without one) and a page the web view could not read.
 *
 * Ctrl / Cmd + V presses Paste, since that is what a user coming back from the browser reaches for, and the app's own
 * paste shortcut is not heard under a sheet. Back and Escape close it as they close every sheet.
 *
 * @param onPaste Reads the clipboard and, where it holds the page, calls the close it is handed before making the song;
 *   where it does not, the sheet stays up and a message in it says why.
 */
@Composable
internal fun PageLinkSheet(
    address: String,
    isAppOffered: Boolean,
    onOpenPage: () -> Unit,
    onPaste: (close: () -> Unit) -> Unit,
    onGetApp: () -> Unit,
    onDismiss: () -> Unit,
) = CampfireBottomSheet(
    title = stringResource(Res.string.page_link_title),
    subtitle = address,
    titleMaxLines = Int.MAX_VALUE,
    onDismiss = onDismiss,
) { contentPadding ->
    val sheet = this
    val isClosing = LocalIsSheetClosing.current
    val paste = { if (!isClosing()) onPaste(sheet::close) }
    val scrollState = rememberScrollState()
    // Keys travel only along the focus path, so the shortcut is heard only once the focus is inside this column. Taken
    // at once, ahead of the sheet's own focus target, which then finds the focus inside it and leaves it there.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                val isPaste = KeyShortcut.PASTE.matches(keyEvent)
                // The key-up is taken too, so that nothing under this sees half a shortcut.
                if (isPaste && keyEvent.type == KeyEventType.KeyDown) paste()
                isPaste
            }
            .focusRequester(focusRequester)
            .focusTarget()
            .fadingVerticalEdges(scrollState, sheetContainerColor())
            .bounceVerticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.page_link_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        PageLinkStep(number = 1, text = stringResource(Res.string.page_link_step_open)) {
            FilledTonalButton(onClick = onOpenPage) { Text(stringResource(Res.string.page_link_open)) }
        }
        PageLinkStep(number = 2, text = stringResource(Res.string.page_link_step_copy))
        PageLinkStep(number = 3, text = stringResource(Res.string.page_link_step_paste)) {
            Button(onClick = paste) { Text(stringResource(Res.string.page_link_paste)) }
        }
        if (isAppOffered) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(Res.string.page_link_app_offer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onGetApp) { Text(stringResource(Res.string.page_link_get_app)) }
        }
    }
}
