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

/** One listing image as proposed: its shot, its headline, whether the shot is in the light theme, and its chips. */
internal data class ListingPreview(
    val id: String,
    val headline: String,
    val isLight: Boolean,
    val chips: List<ListingChip>,
)

/**
 * The listing with the chips proposed for it: two or three to a shot, each naming what the shot shows but its headline
 * does not, on the frame's corners, the first orange and the others alternating. Where there are three, the longest is
 * alone on its edge, since a phone's edge has room for two short ones at most.
 */
internal val listingPreviews = listOf(
    ListingPreview(
        id = "01-songs",
        headline = "Your songbook,\nsynced to every screen",
        isLight = false,
        chips = listOf(
            ListingChip("Import from other apps, PDF, and Word", Res.drawable.ic_import, Accent.ORANGE, Corner.TOP_END),
            ListingChip("Syncs through your own Dropbox", Res.drawable.ic_cloud, Accent.LILAC, Corner.BOTTOM_START),
        ),
    ),
    ListingPreview(
        id = "02-song",
        headline = "Laid out for\nthe way you play",
        isLight = false,
        chips = listOf(
            ListingChip("Turn pages by tap, pedal, or auto scroll", Res.drawable.ic_next, Accent.ORANGE, Corner.TOP_START),
            ListingChip("Every chord, with diagrams", Res.drawable.ic_chords, Accent.LILAC, Corner.BOTTOM_START),
            ListingChip("Tabs and sheet music", Res.drawable.ic_scale_notes, Accent.ORANGE, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "03-metronome-tuner",
        headline = "A metronome and tuner\nin every song",
        isLight = true,
        chips = listOf(
            ListingChip("Tap tempo", Res.drawable.ic_tap_tempo, Accent.ORANGE, Corner.TOP_START),
            ListingChip("Any instrument & tuning", Res.drawable.ic_tuner, Accent.LILAC, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "04-setlists",
        headline = "Setlists\nready for the gig",
        isLight = true,
        chips = listOf(
            ListingChip("Adds up the running time", Res.drawable.ic_clock, Accent.ORANGE, Corner.TOP_END),
            ListingChip("Counts down to the gig", Res.drawable.ic_calendar, Accent.LILAC, Corner.BOTTOM_START),
            ListingChip("Share as a link", Res.drawable.ic_link, Accent.ORANGE, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "05-editor",
        headline = "ChordPro editor\nwith live preview",
        isLight = false,
        chips = listOf(
            ListingChip("Paste from any website", Res.drawable.ic_content_paste, Accent.ORANGE, Corner.TOP_START),
            ListingChip("Syntax highlighting", Res.drawable.ic_palette, Accent.LILAC, Corner.BOTTOM_START),
            ListingChip("Prettify in one go", Res.drawable.ic_prettify, Accent.ORANGE, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "06-scales",
        headline = "Every scale and mode,\non your instrument",
        isLight = false,
        chips = listOf(
            ListingChip("Scale suggestions for each song", Res.drawable.ic_scales, Accent.ORANGE, Corner.TOP_END),
            ListingChip("Hear any scale played", Res.drawable.ic_sound, Accent.LILAC, Corner.BOTTOM_START),
            ListingChip("Notes & intervals", Res.drawable.ic_scale_degrees, Accent.ORANGE, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "07-chords",
        headline = "Chords for guitar,\nukulele, and more",
        isLight = true,
        chips = listOf(
            ListingChip("Pick your own shapes", Res.drawable.ic_chords, Accent.ORANGE, Corner.TOP_START),
            ListingChip("German, Latin, and Nashville names", Res.drawable.ic_language, Accent.LILAC, Corner.BOTTOM_END),
        ),
    ),
    ListingPreview(
        id = "08-export",
        headline = "Print a song\nor the whole set",
        isLight = true,
        chips = listOf(
            ListingChip("Chord diagrams on paper", Res.drawable.ic_chords, Accent.ORANGE, Corner.TOP_END),
            ListingChip("Share as PDF", Res.drawable.ic_share, Accent.LILAC, Corner.BOTTOM_START),
        ),
    ),
)
