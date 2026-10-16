/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

/**
 * The groups of an open search: the sections run together under no header, so that the cards of one fill the columns the
 * one before it left open instead of each section ending in a short row. The Selection group loses its header too and
 * keeps the divider after it ([SongGroup.isSelection]), which is what tells its songs apart from the rest.
 *
 * With no section to run together, the list is the one it was given, the same instance.
 */
internal fun List<SongGroup>.withoutSectionHeaders(): List<SongGroup> {
    if (none { it.header != null }) return this
    val selection = filter { it.isSelection }.map { it.copy(header = null) }
    val sections = filter { it.header is SongGroupHeader.Section }
    return if (sections.isEmpty()) selection else selection + SongGroup(header = null, songs = sections.flatMap { it.songs })
}
