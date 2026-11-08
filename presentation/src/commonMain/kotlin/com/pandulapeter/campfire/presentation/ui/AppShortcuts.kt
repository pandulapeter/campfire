/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.ui.input.key.Key
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.screenOf
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader

/** What an app-wide shortcut asks of the view model, see [appShortcutOf]. */
internal sealed interface AppShortcut {

    /** The shortcut of the table this is. */
    val keyShortcut: KeyShortcut

    /** Ctrl / Cmd + F, see `ScreenSearches.openCurrentSearch`. */
    data object OpenSearch : AppShortcut {
        override val keyShortcut = KeyShortcut.FIND
    }

    /** Ctrl / Cmd + plus, minus or zero, see `FontScaleController.zoomSongText`; null [steps] resets. */
    data class ZoomSongText(val steps: Int?) : AppShortcut {
        override val keyShortcut = when {
            steps == null -> KeyShortcut.ZOOM_RESET
            steps > 0 -> KeyShortcut.ZOOM_IN
            else -> KeyShortcut.ZOOM_OUT
        }
    }

    /** M, see `MetronomeController.toggleMetronomeByKey`. */
    data object ToggleMetronome : AppShortcut {
        override val keyShortcut = KeyShortcut.METRONOME
    }

    /** T on the Metronome tab, see `MetronomeController.tapTempoByKey`; song details hears its own. */
    data object TapTempo : AppShortcut {
        override val keyShortcut = KeyShortcut.TAP_TEMPO
    }

    /** Ctrl / Cmd + V on the song list, see `PastedSongController.newSongFromClipboard`. */
    data object PasteSong : AppShortcut {
        override val keyShortcut = KeyShortcut.PASTE
    }

    /** Ctrl / Cmd + N, the New menu's first entry on the songs and the setlists screen, see [newItemByKey]. */
    data object NewItem : AppShortcut {
        override val keyShortcut = KeyShortcut.NEW
    }

    /** Ctrl / Cmd + A in the songs screen's selection mode, see [selectAllSongsByKey]. */
    data object SelectAllSongs : AppShortcut {
        override val keyShortcut = KeyShortcut.SELECT_ALL
    }

    /** Ctrl / Cmd + S on the setlists screen, see [exportSetlistByKey]. */
    data object ExportSetlist : AppShortcut {
        override val keyShortcut = KeyShortcut.SAVE
    }

    /** Ctrl / Cmd + 1 to 6 and Ctrl / Cmd + comma, see [selectScreenByKey]. */
    data class SelectScreen(override val keyShortcut: KeyShortcut) : AppShortcut

    /** Ctrl / Cmd + slash, and `?` outside a field, see [showKeyboardShortcutsByKey]. */
    data class ShowKeyboardShortcuts(override val keyShortcut: KeyShortcut) : AppShortcut
}

/**
 * The app-wide shortcut a key-down of [key] is, or null: one of [KeyShortcut]'s, working here, that no screen, sheet or
 * field took first. The rest of the table (Edit, the editor's, the song's arrows) belongs to the surfaces that hear them.
 */
internal fun appShortcutOf(key: Key, isCtrlOrMeta: Boolean, isShift: Boolean, isAlt: Boolean): AppShortcut? = KeyShortcut.entries.firstNotNullOfOrNull { shortcut ->
    // Several entries share a key (Page Down is the song's and the print preview's), so only the app-wide ones are asked.
    appShortcutOf(shortcut)?.takeIf { shortcut.isAvailable && shortcut.chord.matches(key, isCtrlOrMeta = isCtrlOrMeta, isShift = isShift, isAlt = isAlt) }
}

/** The app-wide shortcut [shortcut] is, or null for one a surface hears itself. */
internal fun appShortcutOf(shortcut: KeyShortcut): AppShortcut? = when (shortcut) {
    KeyShortcut.FIND -> AppShortcut.OpenSearch
    KeyShortcut.ZOOM_IN -> AppShortcut.ZoomSongText(1)
    KeyShortcut.ZOOM_OUT -> AppShortcut.ZoomSongText(-1)
    KeyShortcut.ZOOM_RESET -> AppShortcut.ZoomSongText(null)
    KeyShortcut.METRONOME -> AppShortcut.ToggleMetronome
    KeyShortcut.TAP_TEMPO -> AppShortcut.TapTempo
    KeyShortcut.PASTE -> AppShortcut.PasteSong
    KeyShortcut.NEW -> AppShortcut.NewItem
    KeyShortcut.SELECT_ALL -> AppShortcut.SelectAllSongs
    KeyShortcut.SAVE -> AppShortcut.ExportSetlist
    KeyShortcut.SETTINGS -> AppShortcut.SelectScreen(shortcut)
    KeyShortcut.SHORTCUTS, KeyShortcut.SHORTCUTS_QUESTION_MARK -> AppShortcut.ShowKeyboardShortcuts(shortcut)
    else -> if (shortcut in KeyShortcut.screens) AppShortcut.SelectScreen(shortcut) else null
}

/**
 * Carries [shortcut] out, and answers whether it did anything, which is when the key is consumed. A paste reads the
 * clipboard with [clipboard]; a focused field took its own paste before this heard the key.
 */
internal fun CampfireViewModel.perform(shortcut: AppShortcut, clipboard: ClipboardTextReader): Boolean = when (shortcut) {
    AppShortcut.OpenSearch -> navigator.searches.openCurrentSearch()
    is AppShortcut.ZoomSongText -> fontScaleController.zoomSongText(shortcut.steps)
    AppShortcut.ToggleMetronome -> metronomeController.toggleMetronomeByKey()
    AppShortcut.TapTempo -> metronomeController.tapTempoByKey()
    AppShortcut.PasteSong -> pastedSongController.isNewSongPasteAvailable.also { isAvailable ->
        if (isAvailable) pastedSongController.newSongFromClipboard(clipboard)
    }
    AppShortcut.NewItem -> newItemByKey()
    AppShortcut.SelectAllSongs -> selectAllSongsByKey()
    AppShortcut.ExportSetlist -> exportSetlistByKey()
    is AppShortcut.SelectScreen -> selectScreenByKey(shortcut.keyShortcut)
    is AppShortcut.ShowKeyboardShortcuts -> showKeyboardShortcutsByKey()
}

/** Whether nothing is drawn over the screen on top, which the keys are otherwise meant for. */
private val CampfireViewModel.isUncovered get() = dialogHost.visibleDialog.value == null && !dialogHost.overlayState.isAnyMenuOpen

private val CampfireViewModel.top get() = navigator.backStack.lastOrNull()

/**
 * The New menu's New song or New setlist, wherever the menu is offered: not in read only mode, nor in the selection
 * mode, which the songs screen's menu gives way to, nor while a setlist is reordered, which disables the setlists' one.
 */
private fun CampfireViewModel.newItemByKey(): Boolean {
    if (!isUncovered || preferencesController.isPerformanceModeEnabled.value) return false
    val dialog = when (top) {
        CampfireDestination.Songs -> DialogType.NewSong.takeUnless { songSelection.isSongSelecting }
        CampfireDestination.Setlists -> DialogType.NewSetlist.takeUnless { setlistsController.isSetlistReordering }
        else -> null
    } ?: return false
    dialogHost.showDialog(dialog)
    return true
}

/** The selection menu's Select all, which ticks what the songs screen lists; the key is the mode's even with nothing left to tick. */
internal fun CampfireViewModel.selectAllSongsByKey(): Boolean {
    if (!isUncovered || !songSelection.isSongSelecting) return false
    val listed = songListState.songGroups.value.listedFileNames
    if (listed.any { it !in songSelection.selectedSongFileNames.orEmpty() }) songSelection.select(listed)
    return true
}

/** Share or export of the setlist the screen is about ([ListedSetlists.shortcutSetlist]), where its menu is offered. */
private fun CampfireViewModel.exportSetlistByKey(): Boolean {
    if (top != CampfireDestination.Setlists || !isUncovered || preferencesController.isPerformanceModeEnabled.value) return false
    val reordering = setlistsController.reorderingSetlistFileName?.takeIf { setlistsController.isSetlistReordering }
    val setlist = setlistsController.setlistsWithSongs.value.shortcutSetlist(reordering) ?: return false
    dialogHost.showDialog(DialogType.Export(setlist = setlist))
    return true
}

/**
 * The navigation item [shortcut] names ([screenOf]), pressed: a number only while a main screen is on top, where the
 * chrome is, Settings from a song or the editor too (an editor with unsaved text refuses, as its Close would ask). Not
 * from the onboarding or an import, which a new stack would drop. Answers whether that screen is on top now.
 */
internal fun CampfireViewModel.selectScreenByKey(shortcut: KeyShortcut): Boolean {
    val top = top
    val isAllowed = when (top) {
        is CampfireDestination.TopLevel -> true
        is CampfireDestination.SongDetails -> shortcut == KeyShortcut.SETTINGS && top.importPreview == null
        is CampfireDestination.SongEditor -> shortcut == KeyShortcut.SETTINGS
        else -> false
    }
    if (!isAllowed || !isUncovered) return false
    val destination = screenOf(shortcut, preferencesController.topLevelDestinations.value) ?: return false
    selectTopLevelDestination(destination)
    return navigator.backStack.lastOrNull() == destination
}

/**
 * The shortcuts sheet, over whatever screen is on top, though not over a dialog, a sheet or a menu, which it would
 * replace: those hold the focus, so the root never hears the key over them, and the web's shell, which does, then
 * leaves it to the browser.
 */
internal fun CampfireViewModel.showKeyboardShortcutsByKey(): Boolean {
    if (!isUncovered) return false
    dialogHost.showDialog(DialogType.KeyboardShortcuts)
    return true
}
