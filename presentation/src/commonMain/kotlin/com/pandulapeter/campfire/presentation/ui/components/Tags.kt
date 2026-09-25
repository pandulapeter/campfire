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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.song_details_tag_add
import org.jetbrains.compose.resources.painterResource

/**
 * What a song is filed under, under its title in a song list. They stay on one line,
 * so that a row of a list cannot grow taller because somebody filed one song under a dozen labels, and whatever does
 * not fit is scrolled to sideways rather than cut off. The tags come before the languages, in the order the song
 * details header and the filters list them too, so a label is found in the same place wherever a song is shown.
 *
 * @param onTagClicked Null where the tags are only read. Otherwise a tag is a shortcut to its own chip in the song
 *   filters, and [selectedTags] (compared without regard to case, as the filter compares them) are drawn selected so
 *   that a second tap reads as what it is, taking the filter off again.
 * @param onLanguageClicked The same for the languages, against [selectedLanguages].
 * @param onAddTag Null where the song is not to be tagged from here. Otherwise the row ends in the song details
 *   header's own "Add tag" chip, so a song can be filed without being opened.
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
    onAddTag: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalFadingEdges(scrollState)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
    ) {
        tags.forEach { tag ->
            TagPill(
                text = tag,
                isSelected = onTagClicked != null && selectedTags.any { it.equals(tag, ignoreCase = true) },
                onClick = onTagClicked?.let { { it(tag) } },
            )
        }
        // A language carries the mark the song details header gives it, since it is the one label here that is not the
        // user's own word for the song: without it a pill reading "Magyar" is a tag somebody typed, and there is no
        // telling the two apart in a list.
        languages.forEach { code ->
            TagPill(
                text = languageLabel(code),
                isSelected = onLanguageClicked != null && code in selectedLanguages,
                onClick = onLanguageClicked?.let { { it(code) } },
                leadingIcon = painterResource(Res.drawable.ic_language),
            )
        }
        onAddTag?.let { onClick ->
            TagPill(
                text = stringResource(Res.string.song_details_tag_add),
                onClick = onClick,
                leadingIcon = painterResource(Res.drawable.ic_add),
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
 * would erase the card behind the row as well, and the fade would be to a hole instead of to the card's color.
 * It has to be applied outside [horizontalScroll], so that it masks the visible part of the row rather than the
 * ends of the content.
 */
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState) = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
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

/**
 * The layout every group of tags is laid out in where it may take as many lines as it needs, which is everywhere but
 * a song list's rows ([SongLabels]). A tag is a word of whatever length its author chose, so they are flowed rather
 * than put in a grid whose columns would all be as wide as the longest one.
 */
@Composable
internal fun TagFlowRow(
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    shouldUseDoublePadding: Boolean = false,
    content: @Composable FlowRowScope.() -> Unit,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(if (shouldUseDoublePadding) TAG_GAP * 2 else TAG_GAP),
    verticalArrangement = Arrangement.spacedBy(if (shouldUseDoublePadding) TAG_GAP * 2 else TAG_GAP),
    maxLines = maxLines,
    content = content,
)

/**
 * One tag as a tonal pill. Like a [SectionHeader] it is a label first and a control second, so it is laid out at its
 * own size rather than being grown to the 48dp touch target even where it can be clicked: a row of tags each
 * reserving that much would be taller than the song title above it.
 *
 * @param onClick Null where the pill is only read, which is what the editor's preview shows.
 * @param onTrailingIconClick Answers a click on [trailingIcon] alone - taking a tag off the song being played, which
 *   a tap that only meant to read the tag must not do.
 */
@Composable
internal fun TagPill(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: Painter? = null,
    trailingIcon: Painter? = null,
    trailingIconContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = @Composable {
        Row(
            // The tag dialog caps what is typed, but a tag that arrives in a file (written in the editor, imported or
            // synced) is as long as its author made it, and in the sideways scrolling row of a song list nothing else
            // would stop one from being wider than the screen.
            modifier = Modifier.widthIn(max = TAG_MAX_WIDTH).padding(
                start = if (leadingIcon == null) TAG_PADDING else TAG_ICON_INSET,
                end = if (trailingIcon == null) TAG_PADDING else TAG_ICON_INSET,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    modifier = Modifier.padding(end = TAG_ICON_INSET).size(TAG_ICON_SIZE),
                    painter = leadingIcon,
                    contentDescription = null,
                )
            }
            Text(
                // Weighted so that the remove button is measured first: a tag as long as the pill allows is ellipsized
                // rather than pushing it out. The pill's own maximum width is what gives the weight something to be
                // worked out of in the sideways scrolling row of a song list (SongLabels), whose width is unbounded.
                modifier = Modifier.weight(1f, fill = false).padding(vertical = TAG_TEXT_PADDING),
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                // The icon itself is as small as the pill's text, so what can be clicked is the box around it: a
                // tag is taken off by a tap on a 14dp glyph otherwise, which on a phone is a matter of luck.
                Box(
                    modifier = Modifier
                        .padding(start = TAG_ICON_INSET)
                        .clip(CircleShape)
                        .clickable(enabled = onTrailingIconClick != null) { onTrailingIconClick?.invoke() }
                        .size(TAG_TRAILING_ICON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(TAG_ICON_SIZE),
                        painter = trailingIcon,
                        contentDescription = trailingIconContentDescription,
                    )
                }
            }
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        if (onClick == null) {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                content = content,
            )
        } else {
            Surface(
                modifier = modifier,
                onClick = onClick,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                content = content,
            )
        }
    }
}

/** The gap between two tags, horizontally and between the rows they wrap onto. */
internal val TAG_GAP = 4.dp

private val HORIZONTAL_FADE_WIDTH = 24.dp

private val TAG_PADDING = 8.dp
private val TAG_TEXT_PADDING = 4.dp
private val TAG_ICON_INSET = 6.dp
private val TAG_ICON_SIZE = 14.dp
private val TAG_TRAILING_ICON_SIZE = 22.dp

/** Wide enough for the longest tag the tag dialog lets through to be shown whole in most scripts, ellipsized past it. */
private val TAG_MAX_WIDTH = 240.dp
