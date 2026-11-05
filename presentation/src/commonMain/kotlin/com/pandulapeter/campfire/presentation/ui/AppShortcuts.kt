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

    /** Space or M, see `MetronomeController.toggleMetronomeByKey`. */
    data class ToggleMetronome(val isSpace: Boolean) : AppShortcut {
        override val keyShortcut = if (isSpace) KeyShortcut.PLAY else KeyShortcut.METRONOME
    }

    /** Ctrl / Cmd + V on the song list, see `PastedSongController.newSongFromClipboard`. */
    data object PasteSong : AppShortcut {
        override val keyShortcut = KeyShortcut.PASTE
    }
}

/**
 * The app-wide shortcut a key-down of [key] is, or null: one of [KeyShortcut]'s that no screen, sheet or field took
 * first. The rest of the table (Save, Edit, the editor's) belongs to the surfaces that hear them.
 */
internal fun appShortcutOf(key: Key, isCtrlOrMeta: Boolean, isShift: Boolean, isAlt: Boolean): AppShortcut? =
    when (KeyShortcut.entries.firstOrNull { it.chord.matches(key, isCtrlOrMeta = isCtrlOrMeta, isShift = isShift, isAlt = isAlt) }) {
        KeyShortcut.FIND -> AppShortcut.OpenSearch
        KeyShortcut.ZOOM_IN -> AppShortcut.ZoomSongText(1)
        KeyShortcut.ZOOM_OUT -> AppShortcut.ZoomSongText(-1)
        KeyShortcut.ZOOM_RESET -> AppShortcut.ZoomSongText(null)
        KeyShortcut.PLAY -> AppShortcut.ToggleMetronome(isSpace = true)
        KeyShortcut.METRONOME -> AppShortcut.ToggleMetronome(isSpace = false)
        KeyShortcut.PASTE -> AppShortcut.PasteSong
        else -> null
    }

/**
 * Carries [shortcut] out, and answers whether it did anything, which is when the key is consumed. A paste reads the
 * clipboard with [clipboard]; a focused field took its own paste before this heard the key.
 */
internal fun CampfireViewModel.perform(shortcut: AppShortcut, clipboard: ClipboardTextReader): Boolean = when (shortcut) {
    AppShortcut.OpenSearch -> navigator.searches.openCurrentSearch()
    is AppShortcut.ZoomSongText -> fontScaleController.zoomSongText(shortcut.steps)
    is AppShortcut.ToggleMetronome -> metronomeController.toggleMetronomeByKey(isSpace = shortcut.isSpace)
    AppShortcut.PasteSong -> pastedSongController.isNewSongPasteAvailable.also { isAvailable ->
        if (isAvailable) pastedSongController.newSongFromClipboard(clipboard)
    }
}
