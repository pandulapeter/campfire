/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.ui.platform.bounceHorizontalScroll
import org.jetbrains.compose.resources.painterResource

/**
 * What a song is filed under, under its title in a song list. They stay on one line,
 * so that a row of a list cannot grow taller because somebody filed one song under a dozen labels, and whatever does
 * not fit is scrolled to sideways rather than cut off. The tags come before the languages, in the order the song
 * details header and the filters list them too, so a label is found in the same place wherever a song is shown, and
 * each kind is in alphabetical order rather than the file's, as it is in the song details card.
 *
 * @param onTagClicked Null where the tags are only read. Otherwise a tag is a shortcut to its own chip in the song
 *   filters, and [selectedTags] (compared without regard to case, as the filter compares them) are drawn selected so
 *   that a second tap reads as what it is, taking the filter off again.
 * @param onLanguageClicked The same for the languages, against [selectedLanguages].
 */
@Composable
internal fun SongLabels(
    modifier: Modifier = Modifier,
    tags: List<String>,
    languages: List<String>,
    selectedTags: Set<String> = emptySet(),
    selectedLanguages: Set<String> = emptySet(),
    onTagClicked: ((String) -> Unit)? = null,
    onLanguageClicked: ((String) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val sortedTags = remember(tags) { tags.sortedAlphabeticallyBy { it } }
    val sortedLanguages = languages.map { code -> code to languageLabel(code) }.sortedAlphabeticallyBy { it.second }
    Row(
        modifier = modifier
            .horizontalFadingEdges(scrollState)
            .bounceHorizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
    ) {
        sortedTags.forEach { tag ->
            TagPill(
                text = tag,
                isSelected = onTagClicked != null && selectedTags.any { it.equals(tag, ignoreCase = true) },
                onClick = onTagClicked?.let { { it(tag) } },
                leadingIcon = painterResource(Res.drawable.ic_label),
            )
        }
        // Each kind of label carries the mark the song details card and the overflow menu give it: without them a pill
        // reading "Magyar" could be a language or a tag somebody typed, and there is no telling the two apart in a list.
        sortedLanguages.forEach { (code, label) ->
            TagPill(
                text = label,
                isSelected = onLanguageClicked != null && code in selectedLanguages,
                onClick = onLanguageClicked?.let { { it(code) } },
                leadingIcon = painterResource(Res.drawable.ic_language),
            )
        }
    }
}

/**
 * Fades out the content of a sideways scrolling row towards whichever edge it continues past, which is what tells the
 * reader that there is more of it: a pill cut off by the edge of the card reads as the end of the row.
 *
 * Each fade is as wide as the distance left to scroll in its direction, up to [HORIZONTAL_FADE_WIDTH], so it grows in
 * and shrinks away with the scroll itself instead of switching on as the row leaves its end. The scroll position is
 * only read while drawing, so scrolling redraws the row without recomposing or measuring it again. The mask is drawn
 * with [BlendMode.DstIn] over the row's own pixels, which needs the offscreen layer: drawn straight into the card, it
 * would erase the card behind the row as well, and the fade would be to a hole instead of to the card's color. The layer
 * is only taken while the row overflows, since a row that fits draws no fade, and a layer per row of a list is memory
 * and a pass of its own for every one of them.
 * It has to be applied outside [horizontalScroll], so that it masks the visible part of the row rather than the
 * ends of the content.
 */
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState) = graphicsLayer {
    compositingStrategy = if (scrollState.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}.drawWithContent {
    drawContent()
    val fadeWidth = HORIZONTAL_FADE_WIDTH.toPx()
    val startFade = scrollState.value.toFloat().coerceAtMost(fadeWidth)
    val endFade = (scrollState.maxValue - scrollState.value).toFloat().coerceIn(0f, fadeWidth)
    // The scroll offset counts from the start of the row, which is its right edge in a right to left layout.
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val leftFade = if (isRtl) endFade else startFade
    val rightFade = if (isRtl) startFade else endFade
    if (leftFade > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = leftFade),
            size = Size(leftFade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
    if (rightFade > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - rightFade, endX = size.width),
            topLeft = Offset(size.width - rightFade, 0f),
            size = Size(rightFade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val HORIZONTAL_FADE_WIDTH = 24.dp
