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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.localization.StringsDefault
import com.pandulapeter.campfire.presentation.localization.StringsHu
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One screenshot of the store listing, taken the same way on every [Device]. The rules each one follows are kept with
 * it, here, so that a retake is the same picture.
 *
 * @property id The file name the shot is written under in each device's folder, after its place in the listing.
 * @property preferences Stored preferences the shot needs on a device whatever the library's own say, by their key in
 *   `preferences.json`; the theme and the language are written from [uiMode] and [language].
 * @property prepare Runs once the library has been read and before the app is composed, which is where a state that a
 *   screen only reads as it is first composed has to be set (a list's scroll position), so that the screen opens on it
 *   rather than being moved there.
 * @property drive Brings the app to the state the shot shows. The app is on the Songs screen, with every animation
 *   settled, when it starts; it is given time to settle again after it returns.
 * @property beat The beat of the bar a playing click is caught on, which is heard a couple of frames before the shot
 *   is taken, so that it is drawn lit at its brightest; null where no click plays.
 */
internal class Shot(
    val id: String,
    val uiMode: UserPreferences.UiMode,
    val language: UserPreferences.Language = UserPreferences.Language.ENGLISH,
    val preferences: (Device) -> Map<String, JsonElement> = { emptyMap() },
    val prepare: suspend PrepareScope.() -> Unit = {},
    val drive: suspend ShotScope.() -> Unit = {},
    val beat: Int? = null,
) {

    /** The same shot in [uiMode] instead, for a place that needs it in the other theme (the README's banners). */
    fun inTheme(uiMode: UserPreferences.UiMode) = Shot(
        id = id,
        uiMode = uiMode,
        language = language,
        preferences = preferences,
        prepare = prepare,
        drive = drive,
        beat = beat,
    )
}

/** What a [Shot] prepares the app with: the view model, with the library read, before any screen is composed. */
internal open class PrepareScope(
    val viewModel: CampfireViewModel,
    val device: Device,
    val language: UserPreferences.Language,
    private val settle: suspend (milliseconds: Long) -> Unit,
) {

    /** Lets the app run for [milliseconds] of frames, for what was asked of it to arrive and finish animating. */
    suspend fun settle(milliseconds: Long = 1_500) = settle.invoke(milliseconds)

    /** The text of the string resource called [key] in the shot's language, for finding what a screen shows. */
    fun string(key: String) = checkNotNull(
        when (language) {
            UserPreferences.Language.HUNGARIAN -> StringsHu.strings[key]
            UserPreferences.Language.ENGLISH, UserPreferences.Language.SYSTEM_DEFAULT -> StringsDefault.strings[key]
        } ?: StringsDefault.strings[key],
    ) { "There is no string resource called $key." }

    /** The song whose file is called [fileName], as the library lists it. */
    suspend fun song(fileName: String) = awaitSongGroups { it.groups.isNotEmpty() }.groups.flatMap { it.songs }.firstOrNull { it.fileName == fileName }
        ?: error("The library has no song called $fileName.")

    /** Opens the song whose file is called [fileName] from the library, as a tap on its row does. */
    suspend fun openSong(fileName: String) {
        viewModel.openSong(song(fileName))
        settle()
    }

    /** Has the Songs screen open with the section of [artist] at its top, its header pinned under the app bar. */
    suspend fun scrollSongsTo(artist: String) {
        val groups = awaitSongGroups { it.groups.isNotEmpty() }.groups
        val section = groups.indexOfFirst { (it.header as? SongSection.Header.Artist)?.name == artist }
        check(section >= 0) { "The song list has no section for $artist." }
        // Every group is its header's item followed by one item per song.
        viewModel.songsScrollPosition.index = groups.take(section).sumOf { (if (it.header == null) 0 else 1) + it.songs.size }
        viewModel.songsScrollPosition.offset = 0
    }

    /** Waits until the song list the Songs screen would show answers [predicate], which the view model works out off the main thread. */
    suspend fun awaitSongGroups(predicate: (SongGroups) -> Boolean): SongGroups {
        withTimeoutOrNull(WAIT_TIMEOUT_MILLIS) {
            while (!predicate(viewModel.songGroups.value)) settle(FRAME_MILLIS)
        } ?: error("The song list never became what the shot waits for.")
        return viewModel.songGroups.value
    }
}

/** What a [Shot] drives the app with once it is on screen: the view model, and touches on what the screen shows. */
internal class ShotScope(
    viewModel: CampfireViewModel,
    device: Device,
    language: UserPreferences.Language,
    settle: suspend (milliseconds: Long) -> Unit,
    private val scene: ImageComposeScene,
) : PrepareScope(viewModel, device, language, settle) {

    /**
     * Taps the middle of whatever on screen is labelled [label] - its text, or what a screen reader calls an icon - with
     * a finger rather than a pointer, so that nothing in the shot is drawn hovered.
     */
    suspend fun tap(label: String) {
        if (!tapIfPresent(label)) error("Nothing on screen is labelled \"$label\".")
    }

    /** Taps what is labelled [label] the way [tap] does, where the screen shows it, and says whether it did. */
    @OptIn(ExperimentalComposeUiApi::class)
    suspend fun tapIfPresent(label: String): Boolean {
        val node = scene.semanticsOwners.firstNotNullOfOrNull { owner -> owner.unmergedRootSemanticsNode.find { it.isLabelled(label) } }
            ?: return false
        val center = node.boundsInRoot.center
        scene.sendPointerEvent(PointerEventType.Press, center, type = PointerType.Touch)
        settle(FRAME_MILLIS * 4)
        scene.sendPointerEvent(PointerEventType.Release, center, type = PointerType.Touch)
        settle()
        return true
    }

    private fun SemanticsNode.find(predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (predicate(this)) this else children.firstNotNullOfOrNull { it.find(predicate) }

    private fun SemanticsNode.isLabelled(label: String) =
        config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
            config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
}

/** The listing, in the order the stores show it. */
internal val shots = listOf(
    // The library: the songs in English, sorted by artist and scrolled down to Gotthard, whose header is pinned under
    // the app bar, with the "New song" menu open over the list, offering to create a song or import files.
    Shot(
        id = "01-songs",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { mapOf("sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id)) },
        prepare = {
            val unfiltered = awaitSongGroups { it.groups.isNotEmpty() }.filterKey
            viewModel.toggleLanguageFilter("en")
            awaitSongGroups { it.filterKey != unfiltered }
            scrollSongsTo(artist = "Gotthard")
        },
        drive = { tap(string("songs_new_song")) },
    ),
    // A song as it is played: the public domain House of the Rising Sun, opened from the library, its first section of
    // controls and its chord diagrams followed by the start of the lyrics. At least the first verse has to be in view,
    // so on a phone the picking pattern, which is a whole screen of tablature, is folded.
    Shot(
        id = "02-song",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { device ->
            buildMap {
                // The library's own text size takes the first verse off a phone, and on the Chromebook it breaks the
                // picking pattern across two columns instead of keeping it whole in the first, as the iPad does.
                if (device.isPhone || device == Device.CHROMEBOOK) put("fontScale", JsonPrimitive(1f))
                if (device.isPhone) put("foldedSections", buildJsonObject { putJsonArray(HOUSE_OF_THE_RISING_SUN) { add("Picking pattern#1") } })
            }
        },
        drive = { openSong(HOUSE_OF_THE_RISING_SUN) },
    ),
    // The metronome as an instrument: its own tab, playing 6/8 at 236 BPM (House of the Rising Sun's tempo), the bar
    // accented on its first and fourth beats as 6/8 is counted, and caught on the first, with the tempo, the time
    // signature and the sound below the pinned bar.
    Shot(
        id = "03-metronome",
        uiMode = UserPreferences.UiMode.LIGHT,
        preferences = {
            mapOf(
                "metronomeSettings" to buildJsonObject {
                    put("bpm", 236)
                    put("timeSignature", "6/8")
                    putJsonObject("beatLevels") {
                        putJsonArray("6/8") { listOf("accent", "normal", "normal", "accent", "normal", "normal").forEach(::add) }
                    }
                },
            )
        },
        drive = {
            viewModel.selectTopLevelDestination(CampfireDestination.Metronome)
            settle()
            viewModel.toggleMetronome()
        },
        beat = 0,
    ),
    // The setlists, latest day first: the Frey-Tully Nuptials in two days and Friday Night by the Lake tomorrow, each
    // counting down under its header next to its running time, and the rest of the gigs below.
    Shot(
        id = "04-setlists",
        uiMode = UserPreferences.UiMode.LIGHT,
        preferences = { mapOf("setlistSortingMode" to JsonPrimitive(UserPreferences.SetlistSortingMode.BY_DATE.id)) },
        drive = { viewModel.selectTopLevelDestination(CampfireDestination.Setlists) },
    ),
    // The ChordPro editor on the public domain Home on the Range: the text on its own where the window has room for one
    // pane, side by side with its live preview where it has room for two, and the Shortcuts always unfolded.
    Shot(
        id = "05-editor",
        uiMode = UserPreferences.UiMode.DARK,
        drive = {
            viewModel.openEditor(HOME_ON_THE_RANGE)
            settle()
            tapIfPresent(string("song_editor_show_shortcuts"))
        },
    ),
    // Home on the Range exported as a PDF, from the library's own song menu: A4 in landscape (in portrait on the small
    // Android tablet, whose upright window shows a portrait page larger), two columns and every option ticked, so the
    // preview shows the chord diagrams, the key, the tempo, the comments and the details. Share stands next to Save on
    // the phones and tablets, as the platforms' file pickers offer it there and not on a desktop.
    Shot(
        id = "06-export",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { device ->
            mapOf(
                "printSettings" to printSettings(isLandscape = device != Device.ANDROID_SMALL_TABLET),
            )
        },
        drive = { viewModel.showDialog(DialogType.Export(song = song(HOME_ON_THE_RANGE))) },
    ),
    // Sync and the library: Settings' Library tab, the Dropbox account connected and synchronized a few minutes ago,
    // what the library holds, the covers it keeps and the ways in and out of it.
    Shot(
        id = "07-sync",
        uiMode = UserPreferences.UiMode.LIGHT,
        prepare = { viewModel.settingsTab = SettingsTab.LIBRARY },
        drive = { viewModel.selectTopLevelDestination(CampfireDestination.Settings) },
    ),
    // Making it one's own: Settings' General tab, with the theme, the colors (Android's wallpaper colors among them) and
    // the switch that has the app icon follow the color.
    Shot(
        id = "08-settings",
        uiMode = UserPreferences.UiMode.LIGHT,
        prepare = { viewModel.settingsTab = SettingsTab.GENERAL },
        drive = { viewModel.selectTopLevelDestination(CampfireDestination.Settings) },
    ),
)

/** Every export option on, in the PDF format: A4, two columns, in portrait or [isLandscape]. */
internal fun printSettings(isLandscape: Boolean) = buildJsonObject {
    put("format", "pdf")
    put("paper", "a4")
    put("isLandscape", isLandscape)
    put("columns", 2)
    listOf(
        "showChords",
        "showChordDiagrams",
        "showKey",
        "showTempo",
        "showComments",
        "showMetadata",
        "showPageNumbers",
        "startSongsOnNewPage",
        "includeSetlistOverview",
    ).forEach { put(it, true) }
}

private const val HOUSE_OF_THE_RISING_SUN = "traditional_american-house_of_the_rising_sun.cho"
private const val HOME_ON_THE_RANGE = "traditional_american-home_on_the_range_my_western_home.cho"

internal const val FRAME_MILLIS = 16L
private const val WAIT_TIMEOUT_MILLIS = 30_000L
