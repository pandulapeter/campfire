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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform

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
                    viewModel.songListState.toggleLanguageFilter("en")
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
        // The Tuner tab on a guitar: the low E string already ticked off as in tune, the A string heard a little flat.
        Triple(
            "phone-tuner",
            Device.IPHONE,
            Shot(
                id = "phone-tuner",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { tuner },
                drive = {
                    viewModel.selectTopLevelDestination(CampfireDestination.Tuner)
                    settle()
                    hear(reading(note = 40, cents = 0.4f))
                    hear(reading(note = 45, cents = -14f))
                },
            ),
        ),
        // A song with the tuner sheet open over it, the low E string heard in tune.
        Triple(
            "tablet-song-tuner",
            Device.IPAD,
            Shot(
                id = "tablet-song-tuner",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { tuner + ("fontScale" to JsonPrimitive(0.9f)) },
                drive = {
                    openSong("the_proclaimers-im_gonna_be_500_miles.cho")
                    viewModel.dialogHost.showDialog(DialogType.Tuner)
                    settle()
                    hear(reading(note = 40, cents = 1.2f))
                },
            ),
        ),
        // The Chords tab on a ukulele in its standard tuning, as the store listing shows it: the chords of C in the shapes
        // the songs would draw them with.
        Triple(
            "phone-chords",
            Device.IPHONE,
            Shot(
                id = "phone-chords",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.UKULELE.id)) },
                drive = { viewModel.selectTopLevelDestination(CampfireDestination.Chords) },
            ),
        ),
        // Uptown Girl on a guitar in standard tuning at 100%, with the Choose chord shape sheet open over it for E.
        Triple(
            "tablet-song-chord-shape",
            Device.IPAD,
            Shot(
                id = "tablet-song-chord-shape",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
                        "fontScale" to JsonPrimitive(1f),
                    )
                },
                drive = {
                    openSong(UPTOWN_GIRL)
                    val song = checkNotNull(song(UPTOWN_GIRL))
                    viewModel.dialogHost.showDialog(DialogType.ChordShape(song = song, setlistFileName = null, chordName = "E"))
                },
            ),
        ),
        Triple("tablet-song", Device.IPAD, song("tablet-song", "jonathan_coulton-still_alive.cho", fontScale = 0.95f)),
        // A song with the metronome panel open under its title, not playing.
        Triple(
            "tablet-song-metronome",
            Device.IPAD,
            song("tablet-song-metronome", "counting_crows-accidentally_in_love.cho", fontScale = 1f, extra = metronome(isSongPanelShown = true)),
        ),
        // A song exported as a two-column A4 PDF, every option ticked.
        Triple(
            "tablet-export",
            Device.IPAD,
            Shot(
                id = "tablet-export",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("printSettings" to printSettings(isLandscape = false)) },
                drive = { viewModel.dialogHost.showDialog(DialogType.Export(song = song("barenaked_ladies-big_bang_theory_theme.cho"))) },
            ),
        ),
        // One song at three text sizes, which the page crossfades between to show that the columns follow the text size:
        // the whole song in four columns, the first page ending after Chorus 2 in three, and after Verse 1 in two.
        Triple("laptop-song-small", Device.LAPTOP, song("laptop-song-small", "the_rembrandts-ill_be_there_for_you.cho", fontScale = 1f)),
        Triple("laptop-song-medium", Device.LAPTOP, song("laptop-song-medium", "the_rembrandts-ill_be_there_for_you.cho", fontScale = 1.4f)),
        Triple("laptop-song-large", Device.LAPTOP, song("laptop-song-large", "the_rembrandts-ill_be_there_for_you.cho", fontScale = 2.3f)),
        Triple("laptop-import", Device.LAPTOP, editor("laptop-import", "ed_sheeran-thinking_out_loud.cho")),
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

private val tuner = mapOf(
    "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
    "tunerSettings" to buildJsonObject {
        put("referencePitch", 440)
        put("hasTurnedOnMicrophone", true)
    },
)

private const val UPTOWN_GIRL = "billy_joel-uptown_girl.cho"

/** Has the tuner hear [reading] and settle on it, the needle come to rest. */
private suspend fun PrepareScope.hear(reading: TunerReading) {
    (KoinPlatform.getKoin().get<Tuner>() as ScriptedTuner).hear(reading)
    settle()
}

private fun metronome(isSongPanelShown: Boolean): Map<String, JsonElement> = mapOf(
    "metronomeSettings" to buildJsonObject {
        put("bpm", 120)
        put("timeSignature", "4/4")
        put("isSongPanelShown", isSongPanelShown)
    },
)

/**
 * A song from the library at [fontScale], chosen per song so that its first page on the device ends where a section
 * ends (for Still Alive, after Bridge 2), since sections are cut across columns and pages wherever a column ends; a
 * change to the song, the device or the layout calls for looking again.
 * The Chords section is never folded to make room.
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
        viewModel.editorSession.openEditor(fileName)
        settle()
        tapIfPresent(string("song_editor_show_shortcuts"))
    },
)
