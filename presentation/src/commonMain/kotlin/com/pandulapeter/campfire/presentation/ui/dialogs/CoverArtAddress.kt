/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_address
import com.pandulapeter.campfire.presentation.resources.cover_art_address_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_address_hint
import com.pandulapeter.campfire.presentation.ui.components.CoverArt
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The address of a cover typed in, and under it the image it names, so that a typo is seen before it is saved. The
 * image is asked for only once the typing has paused ([ADDRESS_PREVIEW_DELAY]), since every half-typed address that
 * happens to be a valid one would otherwise be a request of its own. One that does not load can still be saved: a
 * host may refuse the web build what it gives the other three, and the file is read on all of them.
 */
@Composable
internal fun CoverArtAddress(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    pinFields: Boolean,
    scrollingControls: (@Composable () -> Unit)?,
    address: String,
    usableAddress: String?,
    onAddressChange: (String) -> Unit,
    onDone: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            modifier = fieldModifier.fillMaxWidth(),
            value = address,
            onValueChange = { onAddressChange(it.replace("\n", "").take(MAX_ADDRESS_LENGTH)) },
            label = { Text(stringResource(Res.string.cover_art_address)) },
            trailingIcon = rememberClearTextButton(isVisible = address.isNotEmpty(), onClear = { onAddressChange("") }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
    var previewUrl by remember { mutableStateOf(usableAddress) }
    LaunchedEffect(usableAddress) {
        if (usableAddress != null) delay(ADDRESS_PREVIEW_DELAY)
        previewUrl = usableAddress
    }
    Column(modifier = modifier) {
        if (pinFields) {
            field(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            scrollingControls?.invoke()
            if (!pinFields) field(Modifier)
            CoverArtAddressPreview(url = previewUrl)
        }
    }
}

/** The image [url] names, the indicator while it loads, or a line saying why there is none. */
@Composable
private fun CoverArtAddressPreview(
    url: String?,
) = Surface(
    modifier = Modifier.size(ADDRESS_PREVIEW_SIZE),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val painter = rememberAsyncImagePainter(model = url?.let(::CoverArt), contentScale = ContentScale.Crop)
    val painterState by painter.state.collectAsState()
    AnimatedContent(
        targetState = when {
            url == null -> AddressPreviewContent.HINT
            painterState is AsyncImagePainter.State.Success -> AddressPreviewContent.IMAGE
            painterState is AsyncImagePainter.State.Error -> AddressPreviewContent.FAILED
            else -> AddressPreviewContent.LOADING
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.Center,
    ) { content ->
        when (content) {
            AddressPreviewContent.IMAGE -> Image(
                modifier = Modifier.fillMaxSize(),
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )

            AddressPreviewContent.LOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AddressPreviewContent.HINT, AddressPreviewContent.FAILED -> Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (content == AddressPreviewContent.HINT) Res.string.cover_art_address_hint else Res.string.cover_art_address_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private enum class AddressPreviewContent {
    HINT,
    LOADING,
    FAILED,
    IMAGE,
}

private val ADDRESS_PREVIEW_SIZE = 200.dp

private val ADDRESS_PREVIEW_DELAY = 500.milliseconds

/** Longer than any address a cover is found at, and short enough that the field's saved state stays small. */
private const val MAX_ADDRESS_LENGTH = 2048
