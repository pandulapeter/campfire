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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The screenshots of campfire-songbook.com (`assets/screenshots` in the website's repository), each in the light and
 * the dark theme the page switches between, named as the page names them. They are written at the device's own size,
 * and scaled down to the page's sizes as they are copied there (the store-screenshots skill's `website_screenshots.py`).
 */
internal val websiteShots: List<Triple<String, Device, Shot>> by lazy {
    listOf(
        // The song list in English, sorted by artist and scrolled to Poison.
        Triple(
            "phone-songs",
            Device.IPHONE,
            Shot(
                id = "phone-songs",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { byArtist },
                prepare = {
                    val unfiltered = awaitSongGroups { it.groups.isNotEmpty() }.filterKey
                    viewModel.toggleLanguageFilter("en")
                    awaitSongGroups { it.filterKey != unfiltered }
                    scrollSongsTo("Poison")
                },
            ),
        ),
        // The setlists, from the top.
        Triple("phone-setlist", Device.IPHONE, setlists),
        // The Metronome tab at its default 120 in 4/4, not playing.
        Triple(
            "phone-metronome",
            Device.IPHONE,
            Shot(
                id = "phone-metronome",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { metronome(isSongPanelShown = false) },
                drive = { viewModel.selectTopLevelDestination(CampfireDestination.Metronome) },
            ),
        ),
        Triple("tablet-song", Device.IPAD, song("tablet-song", "jonathan_coulton-still_alive.cho", fontScale = 0.9f)),
        // A song with the metronome panel open under its title, not playing.
        Triple(
            "tablet-song-metronome",
            Device.IPAD,
            song("tablet-song-metronome", "counting_crows-accidentally_in_love.cho", fontScale = 0.9f, extra = metronome(isSongPanelShown = true)),
        ),
        // A song exported as a two-column A4 PDF, every option ticked.
        Triple(
            "tablet-export",
            Device.IPAD,
            Shot(
                id = "tablet-export",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("printSettings" to printSettings(isLandscape = false)) },
                drive = { viewModel.showDialog(CampfireViewModel.DialogType.Export(song = song("barenaked_ladies-big_bang_theory_theme.cho"))) },
            ),
        ),
        Triple("laptop-song", Device.LAPTOP, song("laptop-song", "the_rembrandts-ill_be_there_for_you.cho", fontScale = 1.05f)),
        Triple("laptop-import", Device.LAPTOP, editor("laptop-import", "ed_sheeran-thinking_out_loud.cho")),
        Triple("laptop-editor", Device.LAPTOP, editor("laptop-editor", "the_proclaimers-im_gonna_be_500_miles.cho")),
        Triple("laptop-setlists", Device.LAPTOP, setlists),
    ).flatMap { (name, device, shot) ->
        listOf(UserPreferences.UiMode.LIGHT, UserPreferences.UiMode.DARK).map { uiMode ->
            Triple("$name-${uiMode.id}", device, shot.inTheme(uiMode))
        }
    }
}

private val byArtist = mapOf("sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id))

private val setlists = Shot(
    id = "setlists",
    uiMode = UserPreferences.UiMode.DARK,
    preferences = { mapOf("setlistSortingMode" to JsonPrimitive(UserPreferences.SetlistSortingMode.BY_DATE.id)) },
    drive = { viewModel.selectTopLevelDestination(CampfireDestination.Setlists) },
)

private fun metronome(isSongPanelShown: Boolean): Map<String, JsonElement> = mapOf(
    "metronomeSettings" to buildJsonObject {
        put("bpm", 120)
        put("timeSignature", "4/4")
        put("isSongPanelShown", isSongPanelShown)
    },
)

/**
 * A song from the library at [fontScale], chosen per song by trying every size from 80% to 130% in steps of 2.5%: a
 * song longer than a page is cut wherever a column ends, so the size is one at which the page ends exactly where a
 * section does, never inside one (and, for Still Alive, Verse 2 stays in one column). A change to the song, the device
 * or the layout calls for trying again. The Chords section is never folded to make room.
 */
private fun song(id: String, fileName: String, fontScale: Float = 1f, extra: Map<String, JsonElement> = emptyMap()) = Shot(
    id = id,
    uiMode = UserPreferences.UiMode.DARK,
    preferences = { extra + ("fontScale" to JsonPrimitive(fontScale)) },
    drive = { openSong(fileName) },
)

private fun editor(id: String, fileName: String) = Shot(
    id = id,
    uiMode = UserPreferences.UiMode.DARK,
    drive = {
        viewModel.openEditor(fileName)
        settle()
        tapIfPresent(string("song_editor_show_shortcuts"))
    },
)
