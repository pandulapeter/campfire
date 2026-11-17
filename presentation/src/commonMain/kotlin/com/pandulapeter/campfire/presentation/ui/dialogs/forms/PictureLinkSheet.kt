/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.image.PictureLinks
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.picture_link_address
import com.pandulapeter.campfire.presentation.resources.picture_link_hint
import com.pandulapeter.campfire.presentation.resources.picture_link_invalid
import com.pandulapeter.campfire.presentation.resources.picture_source_link
import com.pandulapeter.campfire.presentation.resources.song_image_download
import com.pandulapeter.campfire.presentation.ui.components.AddressTextField
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.dialogs.rememberSingleConfirmation
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetConfirmButton
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.LocalIsSheetClosing
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.TextFieldBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.rememberFirstFieldFocusRequester
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor

/**
 * The editor's From a link: asks for the address of a picture on the web, which Download hands to [onDownload] and closes
 * the sheet, the download going on under the window's progress bar. Only an `https` address is taken
 * ([PictureLinks.isLink]); anything else typed says so under the field, which keeps one line for it whether or not it is
 * shown, so that the sheet does not change height as the address is typed.
 *
 * @param initialAddress What the field starts with: the address of the last download that did not arrive, else nothing.
 */
@Composable
internal fun PictureLinkSheet(
    initialAddress: String,
    onDismiss: () -> Unit,
    onDownload: (url: String) -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    val url = address.trim()
    val isValid = PictureLinks.isLink(url)
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val download = { close: () -> Unit ->
        if (isValid) {
            confirmOnce {
                onDownload(url)
                close()
            }
        } else {
            keyboardController?.hide()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.picture_source_link),
        text = { contentPadding ->
            val closeSheet = { close() }
            val isClosing = LocalIsSheetClosing.current
            Column(
                modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.picture_link_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val isInvalid = url.isNotEmpty() && !isValid
                AddressTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = address,
                    onValueChange = { value -> address = value.filterNot { it == '\n' || it == '\r' } },
                    label = { Text(stringResource(Res.string.picture_link_address)) },
                    trailingIcon = rememberClearTextButton(isVisible = address.isNotEmpty(), onClear = { address = "" }),
                    isError = isInvalid,
                    supportingText = { Crossfade(isInvalid) { if (it) Text(stringResource(Res.string.picture_link_invalid)) else Text("") } },
                    imeAction = ImeAction.Done,
                    // The close button leaves the keyboard up, so a Done during the slide that follows it is dropped.
                    keyboardActions = KeyboardActions(onDone = { if (!isClosing()) download(closeSheet) }),
                )
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(enabled = isValid, onClick = { download(close) }) { Text(stringResource(Res.string.song_image_download)) }
        },
    )
}
