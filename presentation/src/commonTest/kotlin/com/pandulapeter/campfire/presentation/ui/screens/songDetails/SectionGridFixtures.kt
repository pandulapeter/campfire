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

/** The gap between two sections of a column in the grid, cutting and paging tests. */
internal const val SECTION_GAP = 20

/** The gap between two rows of sections in the grid, cutting and paging tests. */
internal const val ROW_GAP = 40

/** The section each unit of [sectionUnits] belongs to, as the grid takes it: one entry per unit, in order. */
internal fun unitSections(sectionUnits: List<List<Int>>) = sectionUnits.flatMapIndexed { section, units -> List(units.size) { section } }.toIntArray()
