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
import kotlin.math.roundToInt
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
        // Uptown Girl as the metronome shot shows it, with the tuner sheet open over it, the low E string heard a little sharp.
        Triple(
            "tablet-song-tuner",
            Device.IPAD,
            Shot(
                id = "tablet-song-tuner",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { tuner + metronome(isSongPanelShown = true) + ("fontScale" to JsonPrimitive(UPTOWN_GIRL_FONT_SCALE)) },
                drive = {
                    openSong(UPTOWN_GIRL)
                    viewModel.dialogHost.showDialog(DialogType.Tuner)
                    settle()
                    hear(reading(note = 40, cents = 9f))
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
        // I'm Gonna Be (500 Miles) on a guitar in standard tuning, as the scales shot shows it, with the Choose chord shape
        // sheet open over it for B, a barre chord with shapes all along the neck.
        Triple(
            "tablet-song-chord-shape",
            Device.IPAD,
            Shot(
                id = "tablet-song-chord-shape",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
                        "fontScale" to JsonPrimitive(FIVE_HUNDRED_MILES_FONT_SCALE),
                    )
                },
                drive = {
                    openSong(FIVE_HUNDRED_MILES)
                    val song = checkNotNull(song(FIVE_HUNDRED_MILES))
                    viewModel.dialogHost.showDialog(DialogType.ChordShape(song = song, setlistFileName = null, chordName = "B"))
                },
            ),
        ),
        // The Scales tab on a guitar in its standard tuning, the common scales of A, scrolled as the promo clip is so that the
        // family chips head the screen over the cells.
        Triple(
            "phone-scales",
            Device.IPHONE,
            Shot(
                id = "phone-scales",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id)) },
                prepare = { viewModel.scaleLibrary.keyIndex = KEY_A },
                drive = {
                    viewModel.selectTopLevelDestination(CampfireDestination.Scales)
                    settle()
                    scrollTo(string("scale_family_common"), SCALE_FAMILIES_Y)
                },
            ),
        ),
        // I'm Gonna Be (500 Miles) on a guitar in standard tuning, every chord of it in E major so that its Scales section
        // has a set for the whole song, with the scale sheet open over it on E major pentatonic.
        Triple(
            "tablet-song-scales",
            Device.IPAD,
            Shot(
                id = "tablet-song-scales",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
                        "fontScale" to JsonPrimitive(FIVE_HUNDRED_MILES_FONT_SCALE),
                    )
                },
                drive = {
                    openSong(FIVE_HUNDRED_MILES)
                    viewModel.dialogHost.showDialog(DialogType.Scale(scaleId = "major_pentatonic", rootName = "E"))
                },
            ),
        ),
        Triple("tablet-song", Device.IPAD, song("tablet-song", "jonathan_coulton-still_alive.cho", fontScale = 0.95f)),
        // Uptown Girl with the metronome panel open under its title, not playing, as the tuner shot shows it under its sheet.
        Triple(
            "tablet-song-metronome",
            Device.IPAD,
            song("tablet-song-metronome", UPTOWN_GIRL, fontScale = UPTOWN_GIRL_FONT_SCALE, extra = metronome(isSongPanelShown = true)),
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
        Triple("laptop-import", Device.LAPTOP, editor("laptop-import", "ed_sheeran-thinking_out_loud.cho", scroll = EDITOR_SCROLL)),
        Triple("laptop-setlists", Device.LAPTOP, setlists),
    ).plus(
        // One song at every text size the page's slider stops at, with its chords and without, for the demo that the
        // columns follow the text size. Named after the size as the app shows it; at each of them page one ends where a
        // section ends, chords or not, which is what websiteSweep is for finding.
        TEXT_SIZE_DEMO_FONT_SCALES.flatMap { fontScale ->
            listOf(true, false).map { areChordsEnabled ->
                val name = "laptop-song-${(fontScale * 100).roundToInt()}-${if (areChordsEnabled) "chords" else "lyrics"}"
                Triple(name, Device.LAPTOP, song(name, ILL_BE_THERE_FOR_YOU, fontScale = fontScale, extra = chords(areChordsEnabled)))
            }
        },
    ).flatMap { (name, device, shot) ->
        listOf(UserPreferences.UiMode.LIGHT, UserPreferences.UiMode.DARK).map { uiMode ->
            Triple("$name-${uiMode.id}", device, shot.inTheme(uiMode))
        }
    }
}

/**
 * I'll Be There for You on the laptop at every text size from 100% to 250% in steps of 2.5%, with its chords and
 * without, for choosing the sizes the page's text size slider stops at: the ones at which page one ends where a section
 * ends, in both modes. Rendered only with `--only sweep`, into `Sweep/`, in the dark theme alone, since the theme
 * changes nothing of the layout.
 */
internal val websiteSweep: List<Triple<String, Device, Shot>> by lazy {
    (0..60).map { 1f + it * 0.025f }.flatMap { fontScale ->
        listOf(true, false).map { areChordsEnabled ->
            val name = "sweep-%05.1f-%s".format(fontScale * 100, if (areChordsEnabled) "chords" else "lyrics")
            Triple(name, Device.LAPTOP, song(name, ILL_BE_THERE_FOR_YOU, fontScale = fontScale, extra = chords(areChordsEnabled)))
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
private const val ILL_BE_THERE_FOR_YOU = "the_rembrandts-ill_be_there_for_you.cho"

/** The text sizes the website's text size slider stops at, as the demo's columns are laid out: see `websiteShots`. */
private val TEXT_SIZE_DEMO_FONT_SCALES = listOf(1f, 1.2f, 1.4f, 1.75f, 2.1f, 2.3f)
private const val EDITOR_SCROLL = 0.3f
private const val FIVE_HUNDRED_MILES = "the_proclaimers-im_gonna_be_500_miles.cho"

/** The text size at which the iPad's first page of Uptown Girl, its metronome panel open, ends where a section ends. */
private const val UPTOWN_GIRL_FONT_SCALE = 1f

/** The text size at which the iPad's first page of I'm Gonna Be (500 Miles) ends where a section ends. */
private const val FIVE_HUNDRED_MILES_FONT_SCALE = 1.1f

/** Has the tuner hear [reading] and settle on it, the needle come to rest. */
private suspend fun PrepareScope.hear(reading: TunerReading) {
    (KoinPlatform.getKoin().get<Tuner>() as ScriptedTuner).hear(reading)
    settle()
}

/**
 * Whether the songs show their chords, or lyrics alone, as the Chords switch in Settings sets it: the document stores
 * the switch's old name, the inverse of `UserPreferences.areChordsEnabled`.
 */
private fun chords(areChordsEnabled: Boolean): Map<String, JsonElement> = mapOf("isLyricsOnlyModeEnabled" to JsonPrimitive(!areChordsEnabled))

private fun metronome(isSongPanelShown: Boolean): Map<String, JsonElement> = mapOf(
    "metronomeSettings" to buildJsonObject {
        put("bpm", 120)
        put("timeSignature", "4/4")
        put("isSongPanelShown", isSongPanelShown)
    },
)

/**
 * A song from the library at [fontScale], chosen per song so that its first page on the device ends where a section
 * ends (for Still Alive, after Verse 4), since sections are cut across columns and pages wherever a column ends; a
 * change to the song, the device or the layout calls for looking again.
 * The Chords section is never folded to make room.
 */
private fun song(id: String, fileName: String, fontScale: Float = 1f, extra: Map<String, JsonElement> = emptyMap()) = Shot(
    id = id,
    uiMode = UserPreferences.UiMode.DARK,
    preferences = { extra + ("fontScale" to JsonPrimitive(fontScale)) },
    drive = { openSong(fileName) },
)

/**
 * The editor on the song whose file is called [fileName], its Shortcuts unfolded and its text scrolled up by [scroll] of
 * the screen's height, past the directives every file starts with, so that the page shows the lyrics being written.
 */
private fun editor(id: String, fileName: String, scroll: Float) = Shot(
    id = id,
    uiMode = UserPreferences.UiMode.DARK,
    drive = {
        viewModel.editorSession.openEditor(fileName)
        settle()
        tapIfPresent(string("song_editor_show_shortcuts"))
        scrollTextField(scroll)
    },
)
