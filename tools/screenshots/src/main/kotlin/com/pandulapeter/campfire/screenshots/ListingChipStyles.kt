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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_calendar
import com.pandulapeter.campfire.presentation.resources.ic_chords
import com.pandulapeter.campfire.presentation.resources.ic_clock
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_content_paste
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_palette
import com.pandulapeter.campfire.presentation.resources.ic_prettify
import com.pandulapeter.campfire.presentation.resources.ic_scale_degrees
import com.pandulapeter.campfire.presentation.resources.ic_scale_notes
import com.pandulapeter.campfire.presentation.resources.ic_scales
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.ic_sound
import com.pandulapeter.campfire.presentation.resources.ic_tap_tempo
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.screenshots.ListingChip.Accent
import com.pandulapeter.campfire.screenshots.ListingChip.Corner
import org.jetbrains.compose.resources.DrawableResource

/** How a listing chip looks and where it sits, its text being the shot's in each language. */
internal class ChipStyle(
    val icon: DrawableResource,
    val accent: Accent,
    val corner: Corner,
)

/**
 * The chips of every listing shot, by its id, in the order of their texts in `screenshot-texts`: each names what the
 * shot shows but its headline does not, on the frame's corners, the first orange and the others alternating. Where
 * there are three, the longest is alone on its edge, since a phone's edge has room for two short ones at most.
 */
internal val listingChipStyles = mapOf(
    "01-songs" to listOf(
        ChipStyle(Res.drawable.ic_import, Accent.ORANGE, Corner.TOP_END),
        ChipStyle(Res.drawable.ic_cloud, Accent.LILAC, Corner.BOTTOM_START),
    ),
    "02-song" to listOf(
        ChipStyle(Res.drawable.ic_next, Accent.ORANGE, Corner.TOP_START),
        ChipStyle(Res.drawable.ic_chords, Accent.LILAC, Corner.BOTTOM_START),
        ChipStyle(Res.drawable.ic_scale_notes, Accent.ORANGE, Corner.BOTTOM_END),
    ),
    "03-metronome-tuner" to listOf(
        ChipStyle(Res.drawable.ic_tap_tempo, Accent.ORANGE, Corner.TOP_START),
        ChipStyle(Res.drawable.ic_tuner, Accent.LILAC, Corner.BOTTOM_END),
    ),
    "04-setlists" to listOf(
        ChipStyle(Res.drawable.ic_clock, Accent.ORANGE, Corner.TOP_END),
        ChipStyle(Res.drawable.ic_calendar, Accent.LILAC, Corner.BOTTOM_START),
        ChipStyle(Res.drawable.ic_link, Accent.ORANGE, Corner.BOTTOM_END),
    ),
    "05-editor" to listOf(
        ChipStyle(Res.drawable.ic_content_paste, Accent.ORANGE, Corner.TOP_START),
        ChipStyle(Res.drawable.ic_palette, Accent.LILAC, Corner.BOTTOM_START),
        ChipStyle(Res.drawable.ic_prettify, Accent.ORANGE, Corner.BOTTOM_END),
    ),
    "06-scales" to listOf(
        ChipStyle(Res.drawable.ic_scales, Accent.ORANGE, Corner.TOP_END),
        ChipStyle(Res.drawable.ic_sound, Accent.LILAC, Corner.BOTTOM_START),
        ChipStyle(Res.drawable.ic_scale_degrees, Accent.ORANGE, Corner.BOTTOM_END),
    ),
    "07-chords" to listOf(
        ChipStyle(Res.drawable.ic_chords, Accent.ORANGE, Corner.TOP_START),
        ChipStyle(Res.drawable.ic_language, Accent.LILAC, Corner.BOTTOM_END),
    ),
    "08-export" to listOf(
        ChipStyle(Res.drawable.ic_chords, Accent.ORANGE, Corner.TOP_END),
        ChipStyle(Res.drawable.ic_share, Accent.LILAC, Corner.BOTTOM_START),
    ),
)
