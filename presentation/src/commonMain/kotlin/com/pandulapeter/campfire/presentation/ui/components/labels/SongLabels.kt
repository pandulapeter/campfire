/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.labels

import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.data.model.song.tagKey
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.song.SongLanguage
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.songs_languages_unknown
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongCardPainters
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
 * @param fadeColor The color the row stands on, the card's, which its ends fade into.
 * @param painters The pills' marks, which a list passes once for all its cards ([SongCardPainters]); null builds the
 *   row's own.
 */
@Composable
internal fun SongLabels(
    modifier: Modifier = Modifier,
    tags: List<String>,
    languages: List<String>,
    fadeColor: Color,
    selectedTags: Set<String> = emptySet(),
    selectedLanguages: Set<String> = emptySet(),
    onTagClicked: ((String) -> Unit)? = null,
    onLanguageClicked: ((String) -> Unit)? = null,
    painters: SongCardPainters? = null,
) {
    val scrollState = rememberScrollState()
    val currentFadeColor by rememberUpdatedState(fadeColor)
    val sortedTags = remember(tags) { tags.sortedAlphabeticallyBy { it } }
    val appLanguageCode = currentLanguage.value.code
    val unknownLanguageLabel = stringResource(Res.string.songs_languages_unknown)
    val sortedLanguages = remember(languages, appLanguageCode, unknownLanguageLabel) {
        languages.map { code ->
            code to if (code == SongLanguage.UNKNOWN) unknownLanguageLabel else namedLanguageLabel(code = code, appLanguageCode = appLanguageCode)
        }.sortedAlphabeticallyBy { it.second }
    }
    // One painter for every pill of a kind, since each is a vector of its own to build and cache for every card scrolled in.
    val tagPainter = painters?.tag ?: painterResource(Res.drawable.ic_label)
    val languagePainter = painters?.language ?: painterResource(Res.drawable.ic_language)
    Row(
        modifier = modifier
            .horizontalFadingEdges(scrollState) { currentFadeColor }
            .bounceHorizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
    ) {
        sortedTags.forEach { tag ->
            TagPill(
                text = tag,
                isSelected = onTagClicked != null && selectedTags.any { tagKey(it) == tagKey(tag) },
                onClick = onTagClicked?.let { { it(tag) } },
                leadingIcon = tagPainter,
                leadingIconFill = tagColor(tag)?.color() ?: Color.Unspecified,
            )
        }
        // Each kind of label carries the mark the song details card and the overflow menu give it: without them a pill
        // reading "Magyar" could be a language or a tag somebody typed, and there is no telling the two apart in a list.
        sortedLanguages.forEach { (code, label) ->
            TagPill(
                text = label,
                isSelected = onLanguageClicked != null && code in selectedLanguages,
                onClick = onLanguageClicked?.let { { it(code) } },
                leadingIcon = languagePainter,
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
 * only read while drawing, so scrolling redraws the row without recomposing or measuring it again. The row stands on its
 * card, an opaque surface of [fadeColor], so the fade is that color painted over the row's ends, the same picture as
 * masking the pills away and with no offscreen layer per card; the gradients are built once for the full width and
 * squeezed to the width a fade has. The color is read while drawing, so a theme changing redraws the fade without
 * composing the row again.
 * It has to be applied outside [horizontalScroll], so that it covers the visible part of the row rather than the
 * ends of the content.
 */
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState, fadeColor: () -> Color) = drawWithCache {
    val fadeWidth = HORIZONTAL_FADE_WIDTH.toPx()
    val color = fadeColor()
    val intoLeft = Brush.horizontalGradient(0f to color, 1f to color.copy(alpha = 0f), startX = 0f, endX = fadeWidth)
    val intoRight = Brush.horizontalGradient(0f to color.copy(alpha = 0f), 1f to color, startX = 0f, endX = fadeWidth)
    onDrawWithContent {
        drawContent()
        val startFade = scrollState.value.toFloat().coerceAtMost(fadeWidth)
        val endFade = (scrollState.maxValue - scrollState.value).toFloat().coerceIn(0f, fadeWidth)
        // The scroll offset counts from the start of the row, which is its right edge in a right to left layout.
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val leftFade = if (isRtl) endFade else startFade
        val rightFade = if (isRtl) startFade else endFade
        if (leftFade > 0f) {
            scale(scaleX = leftFade / fadeWidth, scaleY = 1f, pivot = Offset.Zero) {
                drawRect(brush = intoLeft, size = Size(fadeWidth, size.height))
            }
        }
        if (rightFade > 0f) {
            translate(left = size.width - rightFade) {
                scale(scaleX = rightFade / fadeWidth, scaleY = 1f, pivot = Offset.Zero) {
                    drawRect(brush = intoRight, size = Size(fadeWidth, size.height))
                }
            }
        }
    }
}

private val HORIZONTAL_FADE_WIDTH = 24.dp
