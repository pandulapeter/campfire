/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.image.PictureLinks
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_image
import com.pandulapeter.campfire.presentation.resources.song_image_add
import com.pandulapeter.campfire.presentation.resources.song_image_download
import com.pandulapeter.campfire.presentation.resources.song_image_not_found
import com.pandulapeter.campfire.presentation.ui.components.menu.PictureSourceMenu
import com.pandulapeter.campfire.presentation.ui.platform.PictureSource
import org.jetbrains.compose.resources.painterResource

/**
 * What stands where a song names a picture the library lacks: a box saying so, with Add image where [adder] is given,
 * which first asks where from in a [PictureSourceMenu] where the platform has a photo library apart from its files, and
 * Download before it where the song names the picture by its address ([PictureLinks]).
 * Its room never depends on [adder]: the button keeps its room where it is hidden (read only mode, a preview) and fades,
 * since a song is measured by what it holds and read only mode changes nothing it holds. Where [isShown] is false it only
 * takes its room, empty: a picture whose size is still being read.
 */
@Composable
internal fun MissingImage(
    modifier: Modifier = Modifier,
    name: String,
    adder: MissingImageAdder?,
    isShown: Boolean = true,
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .alpha(if (isShown) 1f else 0f)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Icon(
        modifier = Modifier.size(32.dp),
        painter = painterResource(Res.drawable.ic_image),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = textResource(Res.string.song_image_not_found, name),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    val buttonAlpha by animateFloatAsState(if (adder != null && isShown) 1f else 0f)
    val buttonModifier = Modifier
        .alpha(buttonAlpha)
        .then(if (adder == null) Modifier.semantics { hideFromAccessibility() } else Modifier)
    val isEnabled = adder != null && isShown
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Offered by the name alone, never by whether the adder is there, for the room it takes to stay the same.
        if (PictureLinks.isLink(name)) {
            FilledTonalButton(
                modifier = buttonModifier,
                onClick = { adder?.download(name) },
                enabled = isEnabled,
            ) {
                Text(stringResource(Res.string.song_image_download))
            }
        }
        val addButton = @Composable { onClick: () -> Unit ->
            FilledTonalButton(
                modifier = buttonModifier,
                onClick = onClick,
                enabled = isEnabled,
            ) {
                Text(stringResource(Res.string.song_image_add))
            }
        }
        if (adder?.canPickPhotos == true) {
            PictureSourceMenu(isMultiple = false, button = addButton, onPick = { source -> adder.add(name, source) })
        } else {
            addButton { adder?.add(name, PictureSource.FILES) }
        }
    }
}
