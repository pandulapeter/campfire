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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
                drive = { recordDrag(frames = 180, from = 0.85f, to = 0.15f) },
            ),
        ),
        // House of the Rising Sun opened from the library, then read down past its chord diagrams.
        PromoShot(
            name = "open-song",
            device = Device.IPHONE,
            isClip = true,
            shot = Shot(
                id = "open-song",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = {
                    mapOf(
                        "sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id),
                        "fontScale" to JsonPrimitive(1f),
                        "foldedSections" to buildJsonObject { putJsonArray(HOUSE_OF_THE_RISING_SUN) { add("Picking pattern#1") } },
                    )
                },
                prepare = {
                    englishOnly()
                    scrollSongsTo(artist = "Traditional American")
                },
                drive = {
                    val song = song(HOUSE_OF_THE_RISING_SUN)
                    record(frames = 120) { if (it == 20) viewModel.openSong(song) }
                    recordDrag(frames = 120, from = 0.8f, to = 0.35f)
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
                    viewModel.toggleMetronome()
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
                        "tunerSettings" to buildJsonObject {
                            put("instrument", "guitar")
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
        // A song on the iPad in both themes, for the video to turn one into the other.
        ipadSong(UserPreferences.UiMode.DARK),
        ipadSong(UserPreferences.UiMode.LIGHT),
        PromoShot(
            name = "setlists",
            device = Device.IPHONE,
            isClip = false,
            shot = Shot(
                id = "setlists",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("setlistSortingMode" to JsonPrimitive(UserPreferences.SetlistSortingMode.BY_DATE.id)) },
                drive = { viewModel.selectTopLevelDestination(CampfireDestination.Setlists) },
            ),
        ),
        PromoShot(
            name = "export",
            device = Device.IPHONE,
            isClip = false,
            shot = Shot(
                id = "export",
                uiMode = UserPreferences.UiMode.DARK,
                preferences = { mapOf("printSettings" to printSettings(isLandscape = false)) },
                drive = { viewModel.showDialog(DialogType.Export(song = song(HOME_ON_THE_RANGE))) },
            ),
        ),
        PromoShot(
            name = "editor",
            device = Device.IPHONE,
            isClip = false,
            shot = Shot(
                id = "editor",
                uiMode = UserPreferences.UiMode.DARK,
                drive = {
                    viewModel.openEditor(HOME_ON_THE_RANGE)
                    settle()
                    tapIfPresent(string("song_editor_show_shortcuts"))
                },
            ),
        ),
        PromoShot(
            name = "laptop-song-light",
            device = Device.LAPTOP,
            isClip = false,
            shot = Shot(
                id = "laptop-song-light",
                uiMode = UserPreferences.UiMode.LIGHT,
                preferences = { mapOf("fontScale" to JsonPrimitive(1.05f)) },
                drive = { openSong("the_rembrandts-ill_be_there_for_you.cho") },
            ),
        ),
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

private fun ipadSong(uiMode: UserPreferences.UiMode) = PromoShot(
    name = "ipad-song-${uiMode.id}",
    device = Device.IPAD,
    isClip = false,
    shot = Shot(
        id = "ipad-song-${uiMode.id}",
        uiMode = uiMode,
        preferences = { mapOf("fontScale" to JsonPrimitive(0.9f)) },
        drive = { openSong("jonathan_coulton-still_alive.cho") },
    ),
)

private suspend fun PrepareScope.englishOnly() {
    val unfiltered = awaitSongGroups { it.groups.isNotEmpty() }.filterKey
    viewModel.toggleLanguageFilter("en")
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
private const val HOUSE_OF_THE_RISING_SUN = "traditional_american-house_of_the_rising_sun.cho"
private const val HOME_ON_THE_RANGE = "traditional_american-home_on_the_range_my_western_home.cho"
