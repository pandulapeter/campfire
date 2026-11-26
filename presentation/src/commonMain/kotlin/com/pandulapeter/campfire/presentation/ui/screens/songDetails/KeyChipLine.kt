/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

/** The first line of the key chip ([KeyChip]), see [KeyChipText.keyLine]. */
internal enum class KeyChipLine {

    /** The key and the small muted words naming it ("Key of G (+1)", "Em shapes"), see [keyLabelOf]. */
    LABELLED_KEY,

    /** The label alone, muted at the key's size: a song with no key whose chip opens the Playing sheet. */
    LABEL_ALONE,

    /** No first line: read only mode's song with a capo and no key, whose capo is the chip's only line. */
    NONE,
}
