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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.share_or_export
import com.pandulapeter.campfire.presentation.resources.shortcut_back
import com.pandulapeter.campfire.presentation.resources.shortcut_edit
import com.pandulapeter.campfire.presentation.resources.shortcut_export_setlist
import com.pandulapeter.campfire.presentation.resources.shortcut_find
import com.pandulapeter.campfire.presentation.resources.shortcut_find_step
import com.pandulapeter.campfire.presentation.resources.shortcut_find_step_from_query
import com.pandulapeter.campfire.presentation.resources.shortcut_group_app
import com.pandulapeter.campfire.presentation.resources.shortcut_group_editor
import com.pandulapeter.campfire.presentation.resources.shortcut_group_lists
import com.pandulapeter.campfire.presentation.resources.shortcut_group_main_screens
import com.pandulapeter.campfire.presentation.resources.shortcut_group_metronome
import com.pandulapeter.campfire.presentation.resources.shortcut_group_print_preview
import com.pandulapeter.campfire.presentation.resources.shortcut_group_song
import com.pandulapeter.campfire.presentation.resources.shortcut_metronome
import com.pandulapeter.campfire.presentation.resources.shortcut_new
import com.pandulapeter.campfire.presentation.resources.shortcut_paste
import com.pandulapeter.campfire.presentation.resources.shortcut_play
import com.pandulapeter.campfire.presentation.resources.shortcut_preview_ends
import com.pandulapeter.campfire.presentation.resources.shortcut_preview_turn
import com.pandulapeter.campfire.presentation.resources.shortcut_preview_zoom
import com.pandulapeter.campfire.presentation.resources.shortcut_save
import com.pandulapeter.campfire.presentation.resources.shortcut_screen
import com.pandulapeter.campfire.presentation.resources.shortcut_select_all
import com.pandulapeter.campfire.presentation.resources.shortcut_shortcuts
import com.pandulapeter.campfire.presentation.resources.shortcut_song_neighbours
import com.pandulapeter.campfire.presentation.resources.shortcut_song_step_column
import com.pandulapeter.campfire.presentation.resources.shortcut_song_step_nudge
import com.pandulapeter.campfire.presentation.resources.shortcut_song_step_page
import com.pandulapeter.campfire.presentation.resources.shortcut_tap_tempo
import com.pandulapeter.campfire.presentation.resources.shortcut_transpose
import com.pandulapeter.campfire.presentation.resources.shortcut_zoom
import com.pandulapeter.campfire.presentation.resources.shortcut_zoom_reset
import com.pandulapeter.campfire.presentation.resources.song_editor_find
import com.pandulapeter.campfire.presentation.resources.song_editor_find_and_replace
import com.pandulapeter.campfire.presentation.resources.song_editor_format_bold
import com.pandulapeter.campfire.presentation.resources.song_editor_format_italic
import com.pandulapeter.campfire.presentation.resources.song_editor_format_underline
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import org.jetbrains.compose.resources.StringResource

/**
 * What the row [this] leads does in the group of [scope], as the shortcuts sheet and the system's lists say it, or null
 * for the second half of a pair, which is listed in its first half's row ([rowLead]). Every entry is named here, so a
 * new one has to be described, and one listed in several groups says what it does in each. A number takes the name of
 * the screen it opens (`%1$s`), which only the sheet knows. A song's step keys say what they move it by in the setup
 * [features] describe ([ShortcutFeatures.songStep]).
 */
internal fun KeyShortcut.descriptionIn(scope: ShortcutScope, features: ShortcutFeatures): StringResource? = when (this) {
    KeyShortcut.SHORTCUTS -> Res.string.shortcut_shortcuts
    KeyShortcut.BACK -> Res.string.shortcut_back
    KeyShortcut.FIND -> if (scope == ShortcutScope.EDITOR) Res.string.song_editor_find else Res.string.shortcut_find
    KeyShortcut.REPLACE -> Res.string.song_editor_find_and_replace
    KeyShortcut.SAVE -> when (scope) {
        ShortcutScope.LISTS -> Res.string.shortcut_export_setlist
        ShortcutScope.SONG -> Res.string.share_or_export
        ShortcutScope.EDITOR -> Res.string.save
        else -> Res.string.shortcut_save
    }
    KeyShortcut.EDIT -> Res.string.shortcut_edit
    KeyShortcut.PASTE -> Res.string.shortcut_paste
    KeyShortcut.ZOOM_IN -> Res.string.shortcut_zoom
    KeyShortcut.ZOOM_RESET -> Res.string.shortcut_zoom_reset
    KeyShortcut.BOLD -> Res.string.song_editor_format_bold
    KeyShortcut.ITALIC -> Res.string.song_editor_format_italic
    KeyShortcut.UNDERLINE -> Res.string.song_editor_format_underline
    KeyShortcut.NEW -> Res.string.shortcut_new
    KeyShortcut.SELECT_ALL -> Res.string.shortcut_select_all
    KeyShortcut.UNDO -> Res.string.song_editor_undo
    KeyShortcut.REDO -> Res.string.song_editor_redo
    KeyShortcut.FIND_NEXT -> Res.string.shortcut_find_step
    KeyShortcut.FIND_NEXT_FROM_QUERY -> Res.string.shortcut_find_step_from_query
    KeyShortcut.TRANSPOSE_UP -> Res.string.shortcut_transpose
    KeyShortcut.PLAY -> Res.string.shortcut_play
    KeyShortcut.SONG_UP -> when (features.songStep) {
        SongStep.PAGE -> Res.string.shortcut_song_step_page
        SongStep.COLUMN -> Res.string.shortcut_song_step_column
        SongStep.NUDGE -> Res.string.shortcut_song_step_nudge
    }
    KeyShortcut.SONG_SWITCH_UP -> Res.string.shortcut_song_neighbours
    KeyShortcut.METRONOME -> Res.string.shortcut_metronome
    KeyShortcut.TAP_TEMPO -> Res.string.shortcut_tap_tempo
    KeyShortcut.PREVIEW_PAGE_UP -> Res.string.shortcut_preview_turn
    KeyShortcut.PREVIEW_HOME -> Res.string.shortcut_preview_ends
    KeyShortcut.SCREEN_1,
    KeyShortcut.SCREEN_2,
    KeyShortcut.SCREEN_3,
    KeyShortcut.SCREEN_4,
    KeyShortcut.SCREEN_5,
    KeyShortcut.SCREEN_6,
    KeyShortcut.SCREEN_7 -> Res.string.shortcut_screen
    KeyShortcut.SHORTCUTS_QUESTION_MARK,
    KeyShortcut.ZOOM_OUT,
    KeyShortcut.REDO_ALTERNATE,
    KeyShortcut.FIND_PREVIOUS,
    KeyShortcut.FIND_PREVIOUS_FROM_QUERY,
    KeyShortcut.TRANSPOSE_DOWN,
    KeyShortcut.SONG_DOWN,
    KeyShortcut.SONG_PAGE_UP,
    KeyShortcut.SONG_PAGE_DOWN,
    KeyShortcut.SONG_LEFT,
    KeyShortcut.SONG_RIGHT,
    KeyShortcut.SONG_SWITCH_DOWN,
    KeyShortcut.SONG_SWITCH_LEFT,
    KeyShortcut.SONG_SWITCH_RIGHT,
    KeyShortcut.PREVIEW_PAGE_DOWN,
    KeyShortcut.PREVIEW_END,
    KeyShortcut.PREVIEW_LEFT,
    KeyShortcut.PREVIEW_RIGHT -> null
}

/** What a group of the shortcuts sheet is headed with. */
internal val ShortcutScope.title
    get() = when (this) {
        ShortcutScope.APP -> Res.string.shortcut_group_app
        ShortcutScope.MAIN_SCREENS -> Res.string.shortcut_group_main_screens
        ShortcutScope.LISTS -> Res.string.shortcut_group_lists
        ShortcutScope.SONG -> Res.string.shortcut_group_song
        ShortcutScope.METRONOME -> Res.string.shortcut_group_metronome
        ShortcutScope.EDITOR -> Res.string.shortcut_group_editor
        ShortcutScope.PRINT_PREVIEW -> Res.string.shortcut_group_print_preview
    }

/** What Ctrl / Cmd + the scroll wheel does in a group that lists it ([ShortcutGroup.isCommandScrollListed]). */
internal val ShortcutScope.commandScrollDescription
    get() = if (this == ShortcutScope.PRINT_PREVIEW) Res.string.shortcut_preview_zoom else Res.string.shortcut_zoom
