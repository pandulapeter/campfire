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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.SongSection
import com.pandulapeter.campfire.presentation.localization.StringsDefault
import com.pandulapeter.campfire.presentation.localization.StringsHu
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroupHeader
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import com.pandulapeter.campfire.tuner.api.Tuner
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.mp.KoinPlatform

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
        viewModel.navigator.openSong(song(fileName))
        settle()
    }

    /** Has the Songs screen open with the section of [artist] at its top, its header pinned under the app bar. */
    suspend fun scrollSongsTo(artist: String) {
        val groups = awaitSongGroups { it.groups.isNotEmpty() }.groups
        val section = groups.indexOfFirst { ((it.header as? SongGroupHeader.Section)?.header as? SongSection.Header.Artist)?.name == artist }
        check(section >= 0) { "The song list has no section for $artist." }
        // Every group is its header's item followed by one item per song.
        viewModel.songListState.songsScrollPosition.index = groups.take(section).sumOf { (if (it.header == null) 0 else 1) + it.songs.size }
        viewModel.songListState.songsScrollPosition.offset = 0
    }

    /** Waits until the song list the Songs screen would show answers [predicate], which the view model works out off the main thread. */
    suspend fun awaitSongGroups(predicate: (SongGroups) -> Boolean): SongGroups {
        withTimeoutOrNull(WAIT_TIMEOUT_MILLIS) {
            while (!predicate(viewModel.songListState.songGroups.value)) settle(FRAME_MILLIS)
        } ?: error("The song list never became what the shot waits for.")
        return viewModel.songListState.songGroups.value
    }
}

/**
 * What a [Shot] drives the app with once it is on screen: the view model, touches on what the screen shows, and the
 * [keyboard] a clip raises and types on.
 */
internal class ShotScope(
    viewModel: CampfireViewModel,
    device: Device,
    language: UserPreferences.Language,
    settle: suspend (milliseconds: Long) -> Unit,
    private val scene: ImageComposeScene,
    private val captureFrame: suspend () -> Unit,
    val keyboard: OnScreenKeyboard,
) : PrepareScope(viewModel, device, language, settle) {

    /**
     * Records [frames] frames of the app as a clip, a sixtieth of a second of the scene's clock apart whatever the time
     * each takes to draw, so that an animation plays at its own speed in the clip. [onFrame] runs before each frame is
     * drawn, with its index, for what the clip shows to happen at that moment.
     */
    suspend fun record(frames: Int, onFrame: suspend (Int) -> Unit = {}) = repeat(frames) { index ->
        onFrame(index)
        captureFrame()
    }

    /** Sends a touch of [type] at [position], in the scene's pixels, for a gesture a recording draws frame by frame. */
    fun touch(type: PointerEventType, position: Offset) = scene.sendPointerEvent(type, position, type = PointerType.Touch)

    /**
     * Taps the middle of whatever on screen is labelled [label] - its text, or what a screen reader calls an icon - with
     * a finger rather than a pointer, so that nothing in the shot is drawn hovered.
     */
    suspend fun tap(label: String) {
        if (!tapIfPresent(label)) error("Nothing on screen is labelled \"$label\".")
    }

    /** Taps what is labelled [label] the way [tap] does, where the screen shows it, and says whether it did. */
    suspend fun tapIfPresent(label: String): Boolean {
        val center = centerOf(label) ?: return false
        scene.sendPointerEvent(PointerEventType.Press, center, type = PointerType.Touch)
        settle(FRAME_MILLIS * 4)
        scene.sendPointerEvent(PointerEventType.Release, center, type = PointerType.Touch)
        settle()
        return true
    }

    /**
     * Drags whatever is labelled [label] with a finger to [y] of the screen's height, a frame at a time, and lets go only
     * once the finger has stopped, so that the list under it ends exactly there and no fling carries it on. The finger
     * moves from its first frame, since one that starts slowly is held long enough to be a long press.
     */
    suspend fun scrollTo(label: String, y: Float) {
        val from = checkNotNull(centerOf(label)) { "Nothing on screen is labelled \"$label\"." }
        drag(from, Offset(from.x, device.heightPx * y))
    }

    /** Scrolls the text field on screen the way [scrollTo] scrolls a list, its text moved up by [fraction] of the screen's height. */
    @OptIn(ExperimentalComposeUiApi::class)
    suspend fun scrollTextField(fraction: Float) {
        val from = checkNotNull(
            scene.semanticsOwners.firstNotNullOfOrNull { owner ->
                owner.unmergedRootSemanticsNode.find { it.config.getOrNull(SemanticsProperties.EditableText) != null }
            },
        ) { "There is no text field on screen." }.boundsInRoot.center
        drag(from, Offset(from.x, from.y - device.heightPx * fraction))
    }

    private suspend fun drag(from: Offset, to: Offset) {
        scene.sendPointerEvent(PointerEventType.Press, from, type = PointerType.Touch)
        repeat(SCROLL_FRAMES) { frame ->
            settle(FRAME_MILLIS)
            scene.sendPointerEvent(PointerEventType.Move, lerp(from, to, (frame + 1) / SCROLL_FRAMES.toFloat()), type = PointerType.Touch)
        }
        settle(FRAME_MILLIS * 12)
        scene.sendPointerEvent(PointerEventType.Release, to, type = PointerType.Touch)
        settle()
    }

    /**
     * The middle of whatever on screen is labelled [label], in the scene's pixels, for a recording to [touch] it on the
     * frames it chooses: [tap] settles between its press and its release, which a clip would skip as a jump.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun centerOf(label: String): Offset? = scene.semanticsOwners
        .firstNotNullOfOrNull { owner -> owner.unmergedRootSemanticsNode.find { it.isLabelled(label) } }
        ?.boundsInRoot
        ?.center

    /**
     * The middles of everything on screen whose label starts with [prefix], from the top of the screen down, for a label
     * several things share (every row's drag handle) or one that goes on past what a shot knows (a chord's name before
     * its shape is read out).
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun centersOf(prefix: String): List<Offset> = scene.semanticsOwners
        .flatMap { owner -> owner.unmergedRootSemanticsNode.findAll { node -> node.labels().any { it.startsWith(prefix) } } }
        // A node that takes no room (one a screen reader announces) is nothing a finger could touch.
        .filterNot { it.boundsInRoot.isEmpty }
        .map { it.boundsInRoot.center }
        .sortedWith(compareBy({ it.y }, { it.x }))

    private fun SemanticsNode.find(predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (predicate(this)) this else children.firstNotNullOfOrNull { it.find(predicate) }

    private fun SemanticsNode.findAll(predicate: (SemanticsNode) -> Boolean): List<SemanticsNode> =
        (if (predicate(this)) listOf(this) else emptyList()) + children.flatMap { it.findAll(predicate) }

    private fun SemanticsNode.labels() =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun SemanticsNode.isLabelled(label: String) =
        config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
            config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
}

/** The listing, in the order the stores show it. */
internal val shots = listOf(
    // The library: the songs in English only, sorted by artist, from the top of the list.
    Shot(
        id = "01-songs",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { mapOf("sortingMode" to JsonPrimitive(UserPreferences.SortingMode.BY_ARTIST.id)) },
        prepare = {
            val unfiltered = awaitSongGroups { it.groups.isNotEmpty() }.filterKey
            viewModel.songListState.toggleLanguageFilter("en")
            awaitSongGroups { it.filterKey != unfiltered }
        },
    ),
    // A song as it is played: the public domain House of the Rising Sun, opened from the library, its first section of
    // controls and its chord diagrams followed by the start of the lyrics. At least the first verse has to be in view,
    // so on a phone the picking pattern, which is a whole screen of tablature, is folded, and so is the Scales section.
    Shot(
        id = "02-song",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { device ->
            buildMap {
                // The library's own text size takes the first verse off a phone, and on the Chromebook it breaks the
                // picking pattern across two columns instead of keeping it whole in the first, as the iPad does. A
                // phone's first page ends after the first verse whatever the text size, the room it leaves spread
                // between its sections, so the phones take the largest text that still keeps the whole verse on it
                // (one step more and the chord diagrams take two rows and push the verse off), which leaves the least
                // room: 110% on the iPhone, 100% on the narrower Android phone.
                when (device) {
                    Device.IPHONE -> put("fontScale", JsonPrimitive(1.1f))
                    Device.ANDROID_PHONE, Device.CHROMEBOOK -> put("fontScale", JsonPrimitive(1f))
                    else -> Unit
                }
                if (device.isPhone) put("foldedSections", buildJsonObject { putJsonArray(HOUSE_OF_THE_RISING_SUN) { add("Picking pattern#1") } })
                put("isScaleSectionFolded", JsonPrimitive(true))
            }
        },
        drive = { openSong(HOUSE_OF_THE_RISING_SUN) },
    ),
    // The tools a song is played with: the public domain Home on the Range on a guitar, its metronome panel open under
    // the title at the song's 100 BPM in 3/4, and the tuner sheet over it, the A string heard a little flat. The click
    // is not playing, since opening the tuner sheet stops it.
    Shot(
        id = "03-metronome-tuner",
        uiMode = UserPreferences.UiMode.LIGHT,
        preferences = {
            mapOf(
                "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id),
                "tunerSettings" to buildJsonObject {
                    put("referencePitch", 440)
                    put("hasTurnedOnMicrophone", true)
                },
                "metronomeSettings" to buildJsonObject { put("isSongPanelShown", true) },
            )
        },
        drive = {
            openSong(HOME_ON_THE_RANGE)
            viewModel.dialogHost.showDialog(DialogType.Tuner)
            settle()
            (KoinPlatform.getKoin().get<Tuner>() as ScriptedTuner).hear(reading(note = 45, cents = -12f))
            settle()
        },
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
            viewModel.editorSession.openEditor(HOME_ON_THE_RANGE)
            settle()
            tapIfPresent(string("song_editor_show_shortcuts"))
        },
    ),
    // The scales: the Scales tab on a guitar in its standard tuning, in the key of A, with the sheet of A minor
    // pentatonic open over it, its shapes along the neck and the chords it stacks into.
    Shot(
        id = "06-scales",
        uiMode = UserPreferences.UiMode.DARK,
        preferences = { mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR.id)) },
        prepare = { viewModel.scaleLibrary.keyIndex = KEY_A },
        drive = {
            viewModel.selectTopLevelDestination(CampfireDestination.Scales)
            settle()
            viewModel.dialogHost.showDialog(DialogType.Scale(scaleId = "minor_pentatonic", rootName = "A"))
        },
    ),
    // The chord diagrams: the Chords tab on a ukulele in its standard tuning, so that the listing shows the app drawing
    // more than the guitar the other shots play, with the chords of C in the shapes the songs would draw them with.
    Shot(
        id = "07-chords",
        uiMode = UserPreferences.UiMode.LIGHT,
        preferences = { mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.UKULELE.id)) },
        drive = { viewModel.selectTopLevelDestination(CampfireDestination.Chords) },
    ),
    // Home on the Range exported as a PDF, from the library's own song menu: A4 in landscape (in portrait on the small
    // Android tablet, whose upright window shows a portrait page larger), two columns and every option ticked, so the
    // preview shows the chord diagrams, the key, the tempo, the comments and the details. Share stands next to Save on
    // the phones and tablets, as the platforms' file pickers offer it there and not on a desktop.
    Shot(
        id = "08-export",
        uiMode = UserPreferences.UiMode.LIGHT,
        preferences = { device ->
            mapOf(
                "printSettings" to printSettings(isLandscape = device != Device.ANDROID_SMALL_TABLET),
            )
        },
        drive = { viewModel.dialogHost.showDialog(DialogType.Export(song = song(HOME_ON_THE_RANGE))) },
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
        "showTabs",
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

internal const val HOUSE_OF_THE_RISING_SUN = "traditional_american-house_of_the_rising_sun.cho"
private const val HOME_ON_THE_RANGE = "traditional_american-home_on_the_range_my_western_home.cho"

internal const val FRAME_MILLIS = 16L
private const val SCROLL_FRAMES = 24
private const val WAIT_TIMEOUT_MILLIS = 30_000L
