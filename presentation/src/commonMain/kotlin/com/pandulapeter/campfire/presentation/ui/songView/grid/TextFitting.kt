/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

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

/**
 * Whether a song whose sections are [sectionHeights] tall where nothing in them wraps could take fewer than [pageCount]
 * pages of [pageCapacity] when set at [fitScale] - its text laid out that much wider can only wrap less, never less than
 * not at all - so that a scale which cannot save a page is not laid out on every frame of a pinch. A section [isPairable]
 * may be set beside another, as tall as the taller of the two, so it counts at half; and [unitCount] allows for each
 * unit's height being rounded on its own.
 */
internal fun couldSaveAPage(
    sectionHeights: LongArray,
    isPairable: (section: Int) -> Boolean,
    unitCount: Int,
    fitScale: Float,
    pageCount: Int,
    pageCapacity: Long,
): Boolean {
    val height = sectionHeights.indices.sumOf { if (isPairable(it)) sectionHeights[it] / 2.0 else sectionHeights[it].toDouble() }
    return height * fitScale - unitCount <= (pageCount - 1).toDouble() * pageCapacity
}

/** How much smaller than the reader's size a song may be set, in the order they are tried. */
private val FIT_SCALE_STEPS = listOf(0.975f, 0.95f)

/** What two font scales may differ by in the last digits of a float and still be the same scale. */
private const val FONT_SCALE_TOLERANCE = 0.0001f
