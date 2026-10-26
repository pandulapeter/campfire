/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

/**
 * The place a dialog takes in the stack of open dialogs, which is also the composition slot it is drawn in: a sheet
 * about one song and one [SongEditTarget] keeps its place when it is put up again with other data — the metadata form
 * with what the song details lookup filled in — so that it is updated where it is rather than closed and opened again.
 * Every other dialog's place is its kind, as it was when one branch of a `when` drew each kind.
 *
 * The kind is its name rather than its class: the slot's key goes into the hash `rememberSaveable` saves a sheet's
 * typed values under, and a class hashes by identity, differently in every process, so a sheet reopened after Android
 * ended the app would find nothing saved under its new hash.
 */
internal val DialogType.slotKey: Any
    get() = when (this) {
        is DialogType.SongInfo -> kindName to target
        is DialogType.SongEdit -> kindName to target
        else -> kindName
    }

private val DialogType.kindName get() = this::class.simpleName.orEmpty()

/**
 * Whether this dialog is put over [parent], which then stays open under it, rather than replacing it: the editors the
 * About the song sheet opens over it, the cover sheet's Remove question over the cover sheet, and the song details
 * lookup over the metadata form it fills, each only over a parent about the same text; and a scale over the chord sheet
 * it is suggested in, and a chord over the scale sheet it is a chord of; and the notation's choice over the song display
 * sheet its chip is in.
 */
internal fun DialogType.stacksOver(parent: DialogType) = when (this) {
    is DialogType.SongMetadata, is DialogType.SongTags, is DialogType.SongLinks, is DialogType.SongLanguages,
    is DialogType.SongPlaying, is DialogType.CoverArtSearch,
    -> parent is DialogType.SongInfo && parent.target == target

    is DialogType.RemoveSongCoverArt -> parent is DialogType.CoverArtSearch && parent.target == target
    is DialogType.SongDetailsLookup -> parent is DialogType.SongMetadata && parent.target == target
    // A chord and a scale lead to each other, and Back goes the way it came, through what the player opened on the way.
    is DialogType.Scale -> parent is DialogType.ChordShape || parent is DialogType.ChordReference
    is DialogType.ChordReference -> parent is DialogType.Scale
    is DialogType.Notation -> parent is DialogType.SongDisplay
    else -> false
}

/**
 * What stays open under [next] when it is put up over [openDialogs] (the bottom first, the one on screen last): the
 * dialogs under the one whose place it takes, where one is open in its place ([slotKey]); else every dialog up to the
 * topmost one it [stacksOver]; else none.
 */
internal fun nextUnderlyingDialogs(openDialogs: List<DialogType>, next: DialogType?): List<DialogType> {
    if (next == null) return emptyList()
    val sameSlot = openDialogs.indexOfLast { it.slotKey == next.slotKey }
    if (sameSlot >= 0) return openDialogs.take(sameSlot)
    val parent = openDialogs.indexOfLast { next.stacksOver(it) }
    return if (parent >= 0) openDialogs.take(parent + 1) else emptyList()
}
