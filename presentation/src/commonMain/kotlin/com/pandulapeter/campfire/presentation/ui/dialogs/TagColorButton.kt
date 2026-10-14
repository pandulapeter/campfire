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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_blue
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_gray
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_green
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_orange
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_pink
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_purple
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_red
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_teal
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_yellow
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_black
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_brown
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_cyan
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_indigo
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_lime
import com.pandulapeter.campfire.presentation.resources.song_details_tag_color_none
import com.pandulapeter.campfire.presentation.ui.components.controls.ColorChoice
import com.pandulapeter.campfire.presentation.ui.components.controls.ColorChoiceOption
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu
import com.pandulapeter.campfire.presentation.ui.components.labels.TagColor
import com.pandulapeter.campfire.presentation.ui.components.labels.TagIcon
import com.pandulapeter.campfire.presentation.ui.components.controls.colorChoiceWidth
import com.pandulapeter.campfire.presentation.ui.components.isDarkerMoreLegible
import com.pandulapeter.campfire.presentation.ui.components.textResource
import org.jetbrains.compose.resources.painterResource

/**
 * The mark of one tag in the tag sheet, in the color it has, which is also the way to give it another: a popup of the
 * colors as discs rather than a sheet over the sheet, since a choice of swatches has no text to type, and a second
 * sheet stacked on one that may have the keyboard up leaves a small phone nothing to show either of them in. The choice
 * is written as it is made rather than with the sheet's Done: a color belongs to the tag in the whole library rather
 * than to the songs the sheet is editing, so closing the sheet without saving its ticks does not take it back.
 */
@Composable
internal fun TagColorButton(
    modifier: Modifier = Modifier,
    tag: String,
    color: TagColor?,
    onColorSelected: (TagColor?) -> Unit,
) {
    val description = textResource(Res.string.song_details_tag_color, tag)
    OverflowMenu(
        modifier = modifier,
        button = { open ->
            IconButton(
                modifier = Modifier.semantics { contentDescription = description },
                onClick = open,
            ) {
                TagIcon(fill = animatedTagFill(color))
            }
        },
    ) { select ->
        ColorChoice(
            modifier = Modifier.width(colorChoiceWidth(SWATCHES_PER_ROW)),
            options = tagColorOptions(),
            selected = color,
            onSelected = { selected -> select { onColorSelected(selected) } },
        )
    }
}

/**
 * The fill of the button's mark, faded in and out rather than snapped, and moved from one hue to the next. A color
 * taken off fades out in the hue it had, since fading towards no color at all would pass through black.
 */
@Composable
private fun animatedTagFill(color: TagColor?): Color {
    val target = color?.color()
    var hue by remember { mutableStateOf(target ?: Color.Transparent) }
    LaunchedEffect(target) {
        if (target != null) hue = target
    }
    val animatedHue by animateColorAsState(hue)
    val alpha by animateFloatAsState(if (target != null) 1f else 0f)
    return animatedHue.copy(alpha = animatedHue.alpha * alpha)
}

/**
 * No color first, which is what every tag starts at, then the palette. A disc's check is drawn in black or in white,
 * whichever contrasts with it more, since the palette holds a yellow as well as a purple.
 */
@Composable
private fun tagColorOptions(): List<ColorChoiceOption<TagColor?>> {
    val noColor = MaterialTheme.colorScheme.outlineVariant
    return listOf<ColorChoiceOption<TagColor?>>(
        ColorChoiceOption(
            value = null,
            color = noColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            label = stringResource(Res.string.song_details_tag_color_none),
            icon = painterResource(Res.drawable.ic_label),
        ),
    ) + TagColor.entries.map { tagColor ->
        val color = tagColor.color()
        ColorChoiceOption(
            value = tagColor,
            color = color,
            contentColor = if (isDarkerMoreLegible(color)) Color.Black else Color.White,
            label = tagColor.label(),
        )
    }
}

/**
 * What a tag color is called, which only an accessibility service ever reads: the theme colors' own names for the ones
 * the theme has too.
 */
@Composable
private fun TagColor.label() = stringResource(
    when (this) {
        TagColor.RED -> Res.string.settings_user_interface_theme_color_red
        TagColor.ORANGE -> Res.string.settings_user_interface_theme_color_orange
        TagColor.YELLOW -> Res.string.settings_user_interface_theme_color_yellow
        TagColor.LIME -> Res.string.song_details_tag_color_lime
        TagColor.GREEN -> Res.string.settings_user_interface_theme_color_green
        TagColor.TEAL -> Res.string.settings_user_interface_theme_color_teal
        TagColor.CYAN -> Res.string.song_details_tag_color_cyan
        TagColor.BLUE -> Res.string.settings_user_interface_theme_color_blue
        TagColor.INDIGO -> Res.string.song_details_tag_color_indigo
        TagColor.PURPLE -> Res.string.settings_user_interface_theme_color_purple
        TagColor.PINK -> Res.string.settings_user_interface_theme_color_pink
        TagColor.BROWN -> Res.string.song_details_tag_color_brown
        TagColor.GRAY -> Res.string.settings_user_interface_theme_color_gray
        TagColor.BLACK -> Res.string.song_details_tag_color_black
    }
)

/** Even rows of the choices, no color included, five being as many as fit a 360dp phone. */
private const val SWATCHES_PER_ROW = 5
