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

/** A key of an app-wide shortcut as every shell can name it: a physical key on the desktop, a `code` or `key` on the web. */
internal enum class ShortcutKey {
    F,
    PLUS,
    MINUS,
    ZERO,
    SPACE,
    M,
}

/** What an app-wide shortcut asks of the view model, see [appShortcutOf]. */
internal sealed interface AppShortcut {

    /** Ctrl / Cmd + F, see [CampfireViewModel.openCurrentSearch]. */
    data object OpenSearch : AppShortcut

    /** Ctrl / Cmd + plus, minus or zero, see [CampfireViewModel.zoomSongText]; null [steps] resets. */
    data class ZoomSongText(val steps: Int?) : AppShortcut

    /** Space or M, see [CampfireViewModel.toggleMetronomeByKey]. */
    data class ToggleMetronome(val isSpace: Boolean) : AppShortcut
}

/**
 * The shortcut a key-down of [key] is, or null. F and the zoom keys want Ctrl or Cmd and never Alt: AltGr arrives as
 * Ctrl + Alt on Windows, and AltGr with these keys types a character on some layouts. Shift is not looked at, since the
 * plus of a US layout is Shift + equals. Space and M want none of Ctrl, Cmd and Alt, and Shift is not looked at for
 * them either.
 */
internal fun appShortcutOf(key: ShortcutKey, isCtrlOrMeta: Boolean, isAlt: Boolean): AppShortcut? = when (key) {
    ShortcutKey.F -> AppShortcut.OpenSearch.takeIf { isCtrlOrMeta && !isAlt }
    ShortcutKey.PLUS -> AppShortcut.ZoomSongText(1).takeIf { isCtrlOrMeta && !isAlt }
    ShortcutKey.MINUS -> AppShortcut.ZoomSongText(-1).takeIf { isCtrlOrMeta && !isAlt }
    ShortcutKey.ZERO -> AppShortcut.ZoomSongText(null).takeIf { isCtrlOrMeta && !isAlt }
    ShortcutKey.SPACE -> AppShortcut.ToggleMetronome(isSpace = true).takeIf { !isCtrlOrMeta && !isAlt }
    ShortcutKey.M -> AppShortcut.ToggleMetronome(isSpace = false).takeIf { !isCtrlOrMeta && !isAlt }
}

/** Carries [shortcut] out, and answers whether it did anything, which is when the shells consume the key. */
internal fun CampfireViewModel.perform(shortcut: AppShortcut): Boolean = when (shortcut) {
    AppShortcut.OpenSearch -> openCurrentSearch()
    is AppShortcut.ZoomSongText -> zoomSongText(shortcut.steps)
    is AppShortcut.ToggleMetronome -> toggleMetronomeByKey(isSpace = shortcut.isSpace)
}
