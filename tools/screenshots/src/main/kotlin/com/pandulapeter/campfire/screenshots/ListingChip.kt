/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import com.pandulapeter.campfire.presentation.ui.theme.interFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A feature named on a listing image beside the screen that shows it, as the promotional video's chips name them: one
 * of the app's own icons in its [accent] and a few words, on a pill that sits across the frame's edge at its [corner],
 * half outside the frame, so that it reads as a label on the picture rather than part of the app, and covers little of
 * the screen. A long text wraps onto a second line rather than reaching across the frame.
 */
internal class ListingChip(
    val text: String,
    val icon: DrawableResource,
    val accent: Accent,
    val corner: Corner,
) {
    enum class Accent { ORANGE, LILAC }

    enum class Corner(val isTop: Boolean, val isStart: Boolean) {
        TOP_START(isTop = true, isStart = true),
        TOP_END(isTop = true, isStart = false),
        BOTTOM_START(isTop = false, isStart = true),
        BOTTOM_END(isTop = false, isStart = false),
    }
}

/**
 * [chip] drawn with its text [fontSize] high, the measure everything else in it is a share of, light or dark ([isLight]),
 * its icon ahead of its text from the right where [isRightToLeft].
 */
@Composable
internal fun ListingChipPill(
    modifier: Modifier = Modifier,
    chip: ListingChip,
    fontSize: Dp,
    isLight: Boolean,
    isRightToLeft: Boolean,
) {
    val accent = when (chip.accent) {
        ListingChip.Accent.ORANGE -> if (isLight) Color(0xFFD9650B) else Color(0xFFFE851E)
        ListingChip.Accent.LILAC -> if (isLight) Color(0xFF6750A4) else Color(0xFFD0BCFF)
    }
    val shape = RoundedCornerShape(fontSize * 0.92f)
    // Only the pill's own row is mirrored: the chips are placed at absolute corners, whatever the language.
    CompositionLocalProvider(LocalLayoutDirection provides if (isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        ListingChipRow(modifier = modifier, chip = chip, fontSize = fontSize, isLight = isLight, isRightToLeft = isRightToLeft, accent = accent, shape = shape)
    }
}

@Composable
private fun ListingChipRow(
    modifier: Modifier,
    chip: ListingChip,
    fontSize: Dp,
    isLight: Boolean,
    isRightToLeft: Boolean,
    accent: Color,
    shape: Shape,
) {
    Row(
        modifier = modifier
            .rotate(if (chip.corner.isStart == chip.corner.isTop) -2f else 2f)
            .background(if (isLight) Color(0xE0FBF8FF) else Color(0xEB211E29), shape)
            .border(width = fontSize * 0.05f, color = accent.copy(alpha = 0.4f), shape = shape)
            .padding(horizontal = fontSize * 0.6f, vertical = fontSize * 0.32f),
        horizontalArrangement = Arrangement.spacedBy(fontSize * 0.35f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(chip.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accent),
            modifier = Modifier.size(fontSize * 1.05f),
        )
        BasicText(
            text = chip.text,
            style = TextStyle(
                color = if (isLight) Color(0xFF1D1B20) else Color(0xFFE3E1EA),
                fontFamily = interFontFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = with(LocalDensity.current) { fontSize.toSp() },
                letterSpacing = (-0.02).em,
                lineHeight = 1.15.em,
                // A Hebrew chip that starts with a Latin word ("Campfire …") would otherwise take its base direction from it.
                textDirection = if (isRightToLeft) TextDirection.Rtl else TextDirection.Ltr,
            ),
        )
    }
}
