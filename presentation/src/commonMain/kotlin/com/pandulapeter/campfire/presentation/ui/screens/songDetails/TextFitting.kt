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

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The scales a song stepped through may be set at, relative to the reader's [fontScale], to take its last page away -
 * which is what a designer does with a song a few lines too long for its pages: the smaller step first, since it is
 * the one that changes the page least, and none that would set the text below [UserPreferences.MIN_FONT_SCALE], the
 * floor the reader was promised. The reader's own size is what the stepper and the menu keep saying.
 */
internal fun fitScalesFor(fontScale: Float) = FIT_SCALE_STEPS.filter { fontScale * it >= UserPreferences.MIN_FONT_SCALE - FONT_SCALE_TOLERANCE }

/**
 * The first of [fitScales] at which the song takes fewer pages than the [pageCount] it takes at its own size, as
 * [pageCountAt] lays it out there - null where it was not laid out, since it could not have saved one - or 1 where none
 * does: a smaller text is only ever worth a page.
 */
internal fun chooseFitScale(pageCount: Int, fitScales: List<Float>, pageCountAt: (fitScale: Float) -> Int?) =
    fitScales.firstOrNull { scale -> pageCountAt(scale)?.let { it < pageCount } == true } ?: 1f

/** How much smaller than the reader's size a song may be set, in the order they are tried. */
private val FIT_SCALE_STEPS = listOf(0.975f, 0.95f)

/** What two font scales may differ by in the last digits of a float and still be the same scale. */
private const val FONT_SCALE_TOLERANCE = 0.0001f
