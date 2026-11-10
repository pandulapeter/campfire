/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.presentation.ui.platform.AppBuild

/**
 * The shortcuts listed under one [scope]: by the shortcuts sheet, and by the system's own lists (Android's Meta + /,
 * iOS's Command-hold overlay).
 *
 * @param isCommandScrollListed Whether the group ends in Ctrl / Cmd + the scroll wheel, which zooms a song's text and,
 *   on the desktop, the print preview's page: no key, so not in the table, and listed only by the sheet.
 */
@Immutable
internal data class ShortcutGroup(
    val scope: ShortcutScope,
    val rows: List<ShortcutRow>,
    val isCommandScrollListed: Boolean,
)

/** The keys that do one thing, [shortcuts], the first one leading ([rowLead]) and naming the row ([descriptionIn]). */
@Immutable
internal data class ShortcutRow(val shortcuts: List<KeyShortcut>) {
    val lead get() = shortcuts.first()
}

/**
 * What the features switched on leave of the table, since the sheet lists only keys that do something: no song's keys
 * with the songs off, no click's with the metronome off, nothing that changes the library, a song's transposition, or
 * the selection in read only mode, which hides their controls, and no auto-scroll unless a song is read in one
 * scrolling column ([SongStep.NUDGE]), the only layout it plays in.
 *
 * @param songStep What the song's step keys move it by, which their row of the table says.
 */
@Immutable
internal data class ShortcutFeatures(
    val areSongsEnabled: Boolean,
    val isMetronomeEnabled: Boolean,
    val isReadOnly: Boolean,
    val songStep: SongStep,
)

/**
 * Every shortcut that works on [build] with [features], grouped by [KeyShortcut.scopes] in their order and within a
 * group in the table's, the halves of a pair in one row ([rowLead]); one heard in several places is in each group. Ctrl / Cmd + Y is Windows' and Linux's redo, so on an
 * Apple keyboard ([isApple]) it is not listed, though it works there too. [isPreviewWheelZoomed] says whether the print
 * preview's page zooms with Ctrl / Cmd + the scroll wheel here, which only the desktop answers itself.
 */
internal fun shortcutGroups(
    build: AppBuild,
    features: ShortcutFeatures,
    isApple: Boolean,
    isPreviewWheelZoomed: Boolean,
): List<ShortcutGroup> {
    val listed = KeyShortcut.entries.filter { shortcut ->
        shortcut.platforms.includes(build) &&
            !(features.songStep != SongStep.NUDGE && shortcut == KeyShortcut.PLAY) &&
            !(isApple && shortcut == KeyShortcut.REDO_ALTERNATE)
    }
    return ShortcutScope.entries.filter { features.lists(it) }.mapNotNull { scope ->
        val rows = listed.filter { scope in it.scopes && !(features.isReadOnly && it.isHiddenInReadOnly(scope)) }
            .groupBy { it.rowLead }
            .map { (_, shortcuts) -> ShortcutRow(shortcuts) }
        val isCommandScrollListed = when (scope) {
            ShortcutScope.SONG -> true
            ShortcutScope.PRINT_PREVIEW -> isPreviewWheelZoomed
            else -> false
        } && rows.isNotEmpty()
        if (rows.isEmpty()) null else ShortcutGroup(scope = scope, rows = rows, isCommandScrollListed = isCommandScrollListed)
    }
}

/**
 * The shortcut whose row [this] is listed in: the first of the keys that do one thing (a pair's halves, a song's and the
 * print preview's arrows beside their Page Up and Page Down), itself otherwise.
 */
internal val KeyShortcut.rowLead: KeyShortcut
    get() = when (this) {
        KeyShortcut.SHORTCUTS_QUESTION_MARK -> KeyShortcut.SHORTCUTS
        KeyShortcut.ZOOM_OUT -> KeyShortcut.ZOOM_IN
        KeyShortcut.REDO_ALTERNATE -> KeyShortcut.REDO
        KeyShortcut.FIND_PREVIOUS -> KeyShortcut.FIND_NEXT
        KeyShortcut.FIND_PREVIOUS_FROM_QUERY -> KeyShortcut.FIND_NEXT_FROM_QUERY
        KeyShortcut.TRANSPOSE_DOWN -> KeyShortcut.TRANSPOSE_UP
        KeyShortcut.SONG_DOWN,
        KeyShortcut.SONG_LEFT,
        KeyShortcut.SONG_RIGHT,
        KeyShortcut.SONG_PAGE_UP,
        KeyShortcut.SONG_PAGE_DOWN -> KeyShortcut.SONG_UP
        KeyShortcut.SONG_SWITCH_DOWN, KeyShortcut.SONG_SWITCH_LEFT, KeyShortcut.SONG_SWITCH_RIGHT -> KeyShortcut.SONG_SWITCH_UP
        KeyShortcut.PREVIEW_PAGE_DOWN -> KeyShortcut.PREVIEW_PAGE_UP
        KeyShortcut.PREVIEW_END -> KeyShortcut.PREVIEW_HOME
        KeyShortcut.PREVIEW_LEFT, KeyShortcut.PREVIEW_RIGHT -> KeyShortcut.PREVIEW_PAGE_UP
        else -> this
    }

private fun ShortcutFeatures.lists(scope: ShortcutScope) = when (scope) {
    ShortcutScope.APP, ShortcutScope.MAIN_SCREENS -> true
    ShortcutScope.LISTS, ShortcutScope.SONG, ShortcutScope.PRINT_PREVIEW -> areSongsEnabled
    ShortcutScope.METRONOME -> isMetronomeEnabled
    ShortcutScope.EDITOR -> areSongsEnabled && !isReadOnly
}

/** Whether read only mode takes away what this does in [scope]: a setlist's Share or export goes, a song's stays. */
private fun KeyShortcut.isHiddenInReadOnly(scope: ShortcutScope) = this in READ_ONLY_HIDDEN || (this == KeyShortcut.SAVE && scope == ShortcutScope.LISTS)

/**
 * What read only mode takes away the way into, outside the editor, whose whole group goes with it: the library's
 * changes, a song's transposition (its playing controls), and the selection (started from a song's menu).
 */
private val READ_ONLY_HIDDEN = setOf(
    KeyShortcut.EDIT,
    KeyShortcut.NEW,
    KeyShortcut.PASTE,
    KeyShortcut.TRANSPOSE_UP,
    KeyShortcut.TRANSPOSE_DOWN,
    KeyShortcut.SELECT_ALL,
)
