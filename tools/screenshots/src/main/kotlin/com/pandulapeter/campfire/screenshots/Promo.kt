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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The footage of the promotional video: clips the app is recorded playing at 60 frames a second, and stills. The video
 * is cut at 160 BPM, a beat every 22.5 frames, so a click a clip shows lit falls on the soundtrack's beat when the clip
 * starts on a bar.
 *
 * @property isClip Whether the shot's drive records frames; a still is only the picture the shot ends on.
 */
internal class PromoShot(
    val name: String,
    val device: Device,
    val shot: Shot,
    val isClip: Boolean,
)

internal val promoShots: List<PromoShot> by lazy {
    listOf(
        // The library sorted by artist, its colored tags on the cards, dragged up slowly by a finger.
        PromoShot(
            name = "songs-scroll",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "songs-scroll",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id)) },
                prepare = { englishOnly() },
                drive = { recordDrag(frames = 160, from = 0.85f, to = 0.15f) },
            ),
        ),
        // House of the Rising Sun open on the iPad from the first frame, its picking pattern unfolded so that the song takes
        // a second page, and that page turned with the step button on the clip's fifth beat, where the video shows
        // "Pedal & tap to turn pages".
        PromoShot(
            name = "open-song",
            device = Device.IPAD,
            isClip = true,
            shot = Shot(
                id = "open-song",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("fontScale" to JsonPrimitive(1.2f)) },
                drive = {
                    openSong(HOUSE_OF_THE_RISING_SUN)
                    val nextPage = string("song_details_next_page")
                    record(frames = 200) { frame ->
                        when (frame) {
                            PAGE_TURN_FRAME -> touch(PointerEventType.Press, checkNotNull(centerOf(nextPage)) { "The song has a single page." })
                            PAGE_TURN_FRAME + 4 -> touch(PointerEventType.Release, checkNotNull(centerOf(nextPage)))
                        }
                    }
                },
            ),
        ),
        // Take Me Home, Country Roads, in A, transposed up a semitone on each of the four beats after the first, as the
        // soundtrack's A, A♯, B, C, and C♯ hits are, and then read in one notation after another on the next three: every
        // chord and diagram on the page follows.
        PromoShot(
            name = "transpose",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "transpose",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("fontScale" to JsonPrimitive(1f)) },
                drive = {
                    // Sharps, so that the fourth step is the C♯ the soundtrack plays rather than a D♭.
                    viewModel.preferencesController.setAccidentals(UserPreferences.Accidentals.SHARPS)
                    viewModel.preferencesController.setNotation(UserPreferences.Notation.STANDARD)
                    openSong(COUNTRY_ROADS)
                    val up = checkNotNull(centerOf(string("song_details_transpose_up"))) { "The song has no transposition stepper." }
                    record(frames = 200) { frame ->
                        // A tap lands on its release, so the press comes a few frames before the beat.
                        when (frame) {
                            in TRANSPOSE_FRAMES.map { it - 4 } -> touch(PointerEventType.Press, up)
                            in TRANSPOSE_FRAMES -> touch(PointerEventType.Release, up)
                        }
                        NOTATION_CHANGES[frame]?.let(viewModel.preferencesController::setNotation)
                    }
                },
            ),
        ),
        // The Metronome tab playing 160 BPM in 4/4, the soundtrack's own tempo, its beats lit on the beat for three bars.
        PromoShot(
            name = "metronome",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "metronome",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "metronomeSettings" to buildJsonObject {
                            put("bpm", VIDEO_BPM)
                            put("timeSignature", "4/4")
                            put("isSongPanelShown", false)
                        },
                    )
                },
                drive = {
                    viewModel.selectTopLevelDestination(CampfireDestination.Metronome)
                    settle()
                    viewModel.metronomeController.toggleMetronome()
                    settle()
                    val metronome = KoinPlatform.getKoin().get<Metronome>() as SilentMetronome
                    var lastBeat = -1
                    record(frames = 280) { frame ->
                        val beat = (frame / FRAMES_PER_BEAT).toInt()
                        if (beat != lastBeat) {
                            lastBeat = beat
                            metronome.beat(beat % 4)
                        }
                    }
                },
            ),
        ),
        // The Tuner tab on a guitar: the A string heard flat, the needle swinging in and the check closing, then the D
        // string heard sharp and brought down.
        PromoShot(
            name = "tuner",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "tuner",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
                        "tunerSettings" to buildJsonObject {
                            put("referencePitch", 440)
                            put("hasTurnedOnMicrophone", true)
                        },
                    )
                },
                drive = {
                    viewModel.selectTopLevelDestination(CampfireDestination.Tuner)
                    settle()
                    val tuner = KoinPlatform.getKoin().get<Tuner>() as ScriptedTuner
                    record(frames = 330) { frame ->
                        // Two readings a frame would be twice the platforms' rate; one every other frame is close to it.
                        if (frame % 2 == 0) {
                            tuner.hear(
                                when (frame) {
                                    in 12 until 160 -> reading(note = 45, cents = approach(frame - 12, start = -38f))
                                    in 182 until 320 -> reading(note = 50, cents = approach(frame - 182, start = 27f))
                                    else -> null
                                },
                            )
                        }
                    }
                },
            ),
        ),
        // The Chords tab in A, its diagrams redrawn for the ukulele and then the guitar after two beats on the keyboard,
        // then its first chord tapped half a beat later and a shape other than the app's first picked on the sixth beat.
        PromoShot(
            name = "chords",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "chords",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.KEYBOARD.id)) },
                prepare = { viewModel.chordLibrary.keyIndex = KEY_A },
                drive = {
                    // Where the guitar's A is drawn, read before the keyboard's wider cells move it.
                    viewModel.preferencesController.setInstrument(UserPreferences.Instrument.GUITAR)
                    viewModel.selectTopLevelDestination(CampfireDestination.Chords)
                    settle()
                    val cell = centersOf("A: ").first()
                    viewModel.preferencesController.setInstrument(UserPreferences.Instrument.KEYBOARD)
                    settle()
                    var shape = cell
                    record(frames = 160) { frame ->
                        INSTRUMENT_CHANGES[frame]?.let(viewModel.preferencesController::setInstrument)
                        when (frame) {
                            SHAPE_SHEET_FRAME - 4 -> touch(PointerEventType.Press, cell)
                            SHAPE_SHEET_FRAME -> touch(PointerEventType.Release, cell)
                            SHAPE_PICK_FRAME - 4 -> {
                                // The cell under the sheet is labelled as its shapes are, so it is told apart by place.
                                shape = centersOf("A: ").filter { (it - cell).getDistance() > 10f }[1]
                                touch(PointerEventType.Press, shape)
                            }
                            SHAPE_PICK_FRAME -> touch(PointerEventType.Release, shape)
                        }
                    }
                },
            ),
        ),
        // Friday Night by the Lake in its reorder mode, Ring of Fire dragged by its grip from fifth to second place over
        // three beats, dropped on the sixth.
        PromoShot(
            name = "setlist-reorder",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "setlist-reorder",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("setlistSortingMode" to JsonPrimitive(UserPreferences.SetlistSortingMode.BY_DATE.id)) },
                drive = {
                    viewModel.selectTopLevelDestination(CampfireDestination.Setlists)
                    settle()
                    viewModel.setlistsController.reorderingSetlistFileName = FRIDAY_NIGHT_BY_THE_LAKE
                    settle(3_000)
                    val handles = centersOf(string("setlists_reorder"))
                    check(handles.size > 4) { "The setlist shows ${handles.size} rows to reorder." }
                    val from = handles[4]
                    val to = handles[1]
                    record(frames = 160) { frame ->
                        val progress = ((frame - REORDER_START_FRAME) / (REORDER_END_FRAME - REORDER_START_FRAME).toFloat()).coerceIn(0f, 1f)
                        val eased = progress * progress * (3 - 2 * progress)
                        val position = Offset(from.x, from.y + (to.y - from.y) * eased)
                        when {
                            frame == REORDER_PRESS_FRAME -> touch(PointerEventType.Press, from)
                            frame in REORDER_PRESS_FRAME + 1..REORDER_END_FRAME + 8 -> touch(PointerEventType.Move, position)
                            frame == REORDER_END_FRAME + 9 -> touch(PointerEventType.Release, position)
                        }
                    }
                },
            ),
        ),
        // Home on the Range's PDF, its two pages swiped through by a finger on the preview: to the second on the
        // scene's second beat, and back to the first on its fourth.
        PromoShot(
            name = "export",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "export",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("printSettings" to printSettings(isLandscape = false)) },
                drive = {
                    viewModel.dialogHost.showDialog(DialogType.Export(song = song(HOME_ON_THE_RANGE)))
                    settle(3_000)
                    val y = device.heightPx * EXPORT_PAGE_Y
                    record(frames = 110) { frame ->
                        PAGE_SWIPES.forEach { (start, isForward) ->
                            val progress = (frame - start) / PAGE_SWIPE_FRAMES.toFloat()
                            val from = if (isForward) 0.8f else 0.2f
                            val to = if (isForward) 0.2f else 0.8f
                            val position = Offset(device.widthPx * (from + (to - from) * progress.coerceIn(0f, 1f)), y)
                            when (frame - start) {
                                0 -> touch(PointerEventType.Press, position)
                                in 1..PAGE_SWIPE_FRAMES -> touch(PointerEventType.Move, position)
                                PAGE_SWIPE_FRAMES + 1 -> touch(PointerEventType.Release, position)
                            }
                        }
                    }
                },
            ),
        ),
        // A song on the iPad held upright, in the light theme the laptop beside it turns to.
        PromoShot(
            name = "ipad-portrait-song-light",
            device = Device.IPAD_PORTRAIT,
            isClip = false,
            shot = Shot(
                id = "ipad-portrait-song-light",
                uiMode = UserPreferences.UiMode.LIGHT,
                preferences = { mapOf("fontScale" to JsonPrimitive(0.9f)) },
                drive = { openSong("jonathan_coulton-still_alive.cho") },
            ),
        ),
        // A song on the laptop in both themes, for the video to turn one into the other.
        laptopSong(UserPreferences.UiMode.DARK),
        laptopSong(UserPreferences.UiMode.LIGHT),
        PromoShot(
            name = "android-songs-light",
            device = Device.ANDROID_PHONE,
            isClip = false,
            shot = Shot(
                id = "android-songs-light",
                uiMode = UserPreferences.UiMode.LIGHT,
                preferences = { mapOf("sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id)) },
                prepare = {
                    englishOnly()
                    scrollSongsTo(artist = "Queen")
                },
            ),
        ),
    )
}

private fun laptopSong(uiMode: UserPreferences.UiMode) = PromoShot(
    name = "laptop-song-${uiMode.id}",
    device = Device.LAPTOP,
    isClip = false,
    shot = Shot(
        id = "laptop-song-${uiMode.id}",
        uiMode = uiMode,
        preferences = { mapOf("fontScale" to JsonPrimitive(1.05f)) },
        drive = { openSong("the_rembrandts-ill_be_there_for_you.cho") },
    ),
)

private suspend fun PrepareScope.englishOnly() {
    val unfiltered = awaitSongGroups { it.groups.isNotEmpty() }.filterKey
    viewModel.songListState.toggleLanguageFilter("en")
    awaitSongGroups { it.filterKey != unfiltered }
}

/**
 * Records a finger put on the middle of the screen and moved from [from] to [to] of its height over [frames], easing
 * out, and lifted only after the last one, so that the list follows the finger and no fling carries it on. It moves at
 * its fastest from the first frame, since a finger that starts slowly is held long enough to be a long press.
 */
private suspend fun ShotScope.recordDrag(frames: Int, from: Float, to: Float) {
    val x = device.widthPx / 2f
    fun position(frame: Int): Offset {
        val progress = 1 - (1 - frame / (frames - 1f)).pow(3)
        return Offset(x, device.heightPx * (from + (to - from) * progress))
    }
    record(frames) { frame ->
        touch(if (frame == 0) PointerEventType.Press else PointerEventType.Move, position(frame))
    }
    touch(PointerEventType.Release, position(frames - 1))
}

/** A string being tuned [frame]s after it was first heard: a damped swing from [start] cents into tune, with a tremble. */
private fun approach(frame: Int, start: Float): Float {
    val seconds = frame / 60f
    val swing = start * exp(-1.2f * seconds) * cos(1.25f * seconds)
    val tremble = 0.9f * sin(frame * 1.7f) + 0.5f * sin(frame * 0.63f)
    return ((swing + tremble) * 10).roundToInt() / 10f
}

/** What the microphone hears of [note] played [cents] off, at the frequency that would be. */
internal fun reading(note: Int, cents: Float) = TunerReading(
    note = note,
    cents = cents,
    frequency = (440 * 2.0.pow((note - 69 + cents / 100.0) / 12)).toFloat(),
    isInTune = abs(cents) <= IN_TUNE_CENTS,
)

private const val VIDEO_BPM = 160
private const val FRAMES_PER_BEAT = 60 * 60f / VIDEO_BPM
private const val IN_TUNE_CENTS = 3f

/** The song scene's fifth beat, 22.5 frames each, where the video shows its "Pedal & tap to turn pages" chip. */
private const val PAGE_TURN_FRAME = 90
/**
 * The beats the stepper's + is released on: the second to the fourth of the transposition's bar and the first of the
 * next, 22.5 frames each, rounded.
 */
private val TRANSPOSE_FRAMES = listOf(23, 45, 68, 90)

/** The notations the song is read in on the second to the fourth beats of the transposition's second bar. */
private val NOTATION_CHANGES = mapOf(
    113 to UserPreferences.Notation.LATIN,
    135 to UserPreferences.Notation.ROMAN,
    158 to UserPreferences.Notation.NASHVILLE,
)

/** Where A is among the Chords tab's keys, from C, in every spelling of the accidentals. */
private const val KEY_A = 9

/** The instruments the Chords tab is switched to on its third and fourth beats, after two on the keyboard's. */
private val INSTRUMENT_CHANGES = mapOf(
    45 to UserPreferences.Instrument.UKULELE,
    68 to UserPreferences.Instrument.GUITAR,
)

/** The sheet takes a pick only once it has slid into place, about half a second after the tap that opens it. */
private const val SHAPE_SHEET_FRAME = 79
private const val SHAPE_PICK_FRAME = 113

/** Where the PDF preview's page is, down the phone's screen, for a finger to swipe it. */
private const val EXPORT_PAGE_Y = 0.4f

/**
 * The export's swipes, each a finger moved across the page over [PAGE_SWIPE_FRAMES] from its frame, so that the page
 * settles on the scene's second beat, and back on its fourth: whether each goes to the next page.
 */
private val PAGE_SWIPES = listOf(16 to true, 60 to false)
private const val PAGE_SWIPE_FRAMES = 10

private const val REORDER_PRESS_FRAME = 20
private const val REORDER_START_FRAME = 26
private const val REORDER_END_FRAME = 104

private const val HOUSE_OF_THE_RISING_SUN = "traditional_american-house_of_the_rising_sun.cho"
private const val HOME_ON_THE_RANGE = "traditional_american-home_on_the_range_my_western_home.cho"
private const val COUNTRY_ROADS = "john_denver-take_me_home_country_roads.cho"
private const val FRIDAY_NIGHT_BY_THE_LAKE = "friday_night_by_the_lake.setlist.json"
