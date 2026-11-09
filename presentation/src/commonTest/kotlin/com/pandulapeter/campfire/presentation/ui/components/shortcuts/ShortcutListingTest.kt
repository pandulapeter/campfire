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

import com.pandulapeter.campfire.presentation.ui.platform.AppBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShortcutListingTest {

    private val everything = ShortcutFeatures(
        areSongsEnabled = true,
        isMetronomeEnabled = true,
        isReadOnly = false,
        isSongScrolledContinuously = true,
    )

    private fun listed(build: AppBuild, features: ShortcutFeatures = everything, isApple: Boolean = false, isPreviewWheelZoomed: Boolean = false) =
        shortcutGroups(build = build, features = features, isApple = isApple, isPreviewWheelZoomed = isPreviewWheelZoomed)

    @Test
    fun `the desktop lists every shortcut once, grouped in the scopes' order, each row led by a described one`() {
        val groups = listed(AppBuild.WINDOWS)
        assertEquals(ShortcutScope.entries, groups.map { it.scope })
        val shortcuts = groups.flatMap { group -> group.rows.flatMap { it.shortcuts } }
        assertEquals(KeyShortcut.entries.sorted(), shortcuts.sorted())
        groups.forEach { group ->
            group.rows.forEach { row ->
                assertNotNull(row.lead.description, row.lead.name)
                row.shortcuts.forEach { assertEquals(group.scope, it.scope, it.name) }
                row.shortcuts.drop(1).forEach { assertEquals(null, it.description, it.name) }
            }
        }
    }

    @Test
    fun `a pair is one row, Ctrl+Y beside redo except on an Apple keyboard`() {
        val editor = listed(AppBuild.LINUX).single { it.scope == ShortcutScope.EDITOR }
        assertEquals(listOf(KeyShortcut.REDO, KeyShortcut.REDO_ALTERNATE), editor.rows.single { it.lead == KeyShortcut.REDO }.shortcuts)
        assertEquals(listOf(KeyShortcut.FIND_NEXT, KeyShortcut.FIND_PREVIOUS), editor.rows.single { it.lead == KeyShortcut.FIND_NEXT }.shortcuts)
        val appleEditor = listed(AppBuild.MACOS, isApple = true).single { it.scope == ShortcutScope.EDITOR }
        assertEquals(listOf(KeyShortcut.REDO), appleEditor.rows.single { it.lead == KeyShortcut.REDO }.shortcuts)
        val preview = listed(AppBuild.WINDOWS).single { it.scope == ShortcutScope.PRINT_PREVIEW }
        assertEquals(
            listOf(KeyShortcut.PREVIEW_PAGE_UP, KeyShortcut.PREVIEW_PAGE_DOWN, KeyShortcut.PREVIEW_LEFT, KeyShortcut.PREVIEW_RIGHT),
            preview.rows.first().shortcuts,
        )
    }

    @Test
    fun `a phone or a tablet lists no screen numbers and no Escape, and the web no Ctrl+N`() {
        listOf(AppBuild.ANDROID, AppBuild.IOS).forEach { build ->
            val shortcuts = listed(build).flatMap { group -> group.rows.flatMap { it.shortcuts } }
            assertTrue(shortcuts.none { it in KeyShortcut.screens || it == KeyShortcut.BACK }, build.name)
            assertTrue(KeyShortcut.SHORTCUTS in shortcuts && KeyShortcut.SONG_PAGE_DOWN in shortcuts && KeyShortcut.NEW in shortcuts, build.name)
        }
        assertFalse(KeyShortcut.NEW in listed(AppBuild.WEB).flatMap { group -> group.rows.flatMap { it.shortcuts } })
    }

    @Test
    fun `the scroll wheel is listed on a song everywhere and in the print preview only where the app zooms it`() {
        assertTrue(listed(AppBuild.ANDROID).single { it.scope == ShortcutScope.SONG }.isCommandScrollListed)
        assertFalse(listed(AppBuild.WEB).single { it.scope == ShortcutScope.PRINT_PREVIEW }.isCommandScrollListed)
        assertTrue(listed(AppBuild.MACOS, isPreviewWheelZoomed = true).single { it.scope == ShortcutScope.PRINT_PREVIEW }.isCommandScrollListed)
    }

    @Test
    fun `a feature switched off takes its keys, and read only mode every key whose control it hides`() {
        val withoutSongs = listed(AppBuild.WINDOWS, everything.copy(areSongsEnabled = false))
        assertEquals(listOf(ShortcutScope.APP, ShortcutScope.MAIN_SCREENS, ShortcutScope.METRONOME), withoutSongs.map { it.scope })
        assertFalse(ShortcutScope.METRONOME in listed(AppBuild.WINDOWS, everything.copy(isMetronomeEnabled = false)).map { it.scope })
        val readOnly = listed(AppBuild.WINDOWS, everything.copy(isReadOnly = true))
        assertFalse(ShortcutScope.EDITOR in readOnly.map { it.scope })
        val readOnlyShortcuts = readOnly.flatMap { group -> group.rows.flatMap { it.shortcuts } }
        val hidden = listOf(KeyShortcut.EDIT, KeyShortcut.NEW, KeyShortcut.PASTE, KeyShortcut.TRANSPOSE_UP, KeyShortcut.TRANSPOSE_DOWN, KeyShortcut.SELECT_ALL)
        assertTrue(readOnlyShortcuts.none { it in hidden })
        assertTrue(KeyShortcut.SONG_DOWN in readOnlyShortcuts)
    }

    @Test
    fun `auto-scroll is listed only while songs are read in one scrolling column`() {
        fun songShortcuts(features: ShortcutFeatures) = listed(AppBuild.WINDOWS, features).single { it.scope == ShortcutScope.SONG }.rows.flatMap { it.shortcuts }
        assertTrue(KeyShortcut.PLAY in songShortcuts(everything))
        val paged = everything.copy(isSongScrolledContinuously = false)
        assertFalse(KeyShortcut.PLAY in songShortcuts(paged))
        assertFalse(KeyShortcut.PLAY in systemShortcutGroups(build = AppBuild.ANDROID, features = paged, isApple = false).flatMap { it.second })
    }

    @Test
    fun `the system lists name each chord apart, without the screen numbers or the scroll wheel`() {
        val android = systemShortcutGroups(build = AppBuild.ANDROID, features = everything, isApple = false)
        val song = android.single { it.first == ShortcutScope.SONG }.second
        assertTrue(KeyShortcut.SONG_UP in song && KeyShortcut.SONG_DOWN in song && KeyShortcut.TRANSPOSE_DOWN in song)
        assertTrue(android.single { it.first == ShortcutScope.APP }.second.containsAll(listOf(KeyShortcut.SHORTCUTS, KeyShortcut.SHORTCUTS_QUESTION_MARK)))
        val ios = systemShortcutGroups(build = AppBuild.IOS, features = everything, isApple = true).flatMap { it.second }
        assertFalse(KeyShortcut.REDO_ALTERNATE in ios)
        val desktop = systemShortcutGroups(build = AppBuild.WINDOWS, features = everything, isApple = false).flatMap { it.second }
        assertTrue(desktop.none { it in KeyShortcut.screens })
        assertTrue(KeyShortcut.BACK in desktop)
    }
}
