/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * One ember of [OnboardingCelebration]. Every value is a fraction (of the window's width or height, or of the whole
 * celebration's time), so that one set of embers fits any window and is laid out before the window is measured.
 *
 * @param start Where across the window it rises from.
 * @param delay How far into the celebration it catches.
 * @param lifetime How much of the celebration it burns for; [delay] and this never add up to more than the whole, so
 *   every ember has burnt out by the time the celebration ends.
 * @param rise How far up the window it gets before it burns out.
 * @param sway How far it drifts to either side of [start] as it rises.
 * @param swayCount How many times it drifts from one side to the other.
 * @param radius Its size, in dp at the moment it catches.
 * @param colorIndex Which of the celebration's colors it burns in.
 */
internal data class OnboardingEmber(
    val start: Float,
    val delay: Float,
    val lifetime: Float,
    val rise: Float,
    val sway: Float,
    val swayCount: Float,
    val radius: Float,
    val colorIndex: Int,
)

/** Where an ember is in one frame: its center as fractions of the window, how much of its size it has left, and its opacity. */
internal data class OnboardingEmberFrame(
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
)

/**
 * The embers of one celebration, scattered by [random]: most catch in the first third so that the burst reads as one,
 * and the rest keep it going while the app settles under it.
 */
internal fun onboardingEmbers(random: Random, count: Int, colorCount: Int) = List(count) {
    val lifetime = random.nextFloat(0.45f, 0.7f)
    OnboardingEmber(
        start = random.nextFloat(),
        delay = random.nextFloat() * random.nextFloat() * (1f - lifetime),
        lifetime = lifetime,
        rise = random.nextFloat(0.45f, 0.95f),
        sway = random.nextFloat(0.01f, 0.05f),
        swayCount = random.nextFloat(0.5f, 2f),
        radius = random.nextFloat(2f, 5f),
        colorIndex = random.nextInt(colorCount),
    )
}

/**
 * Where [this] is at [progress] through the celebration, or null while it has not caught yet or once it has burnt out.
 * It starts just under the window's bottom edge, so that it rises into view rather than appearing, and slows as it goes,
 * the way a spark loses the heat that carries it; it flares up at once and dims and shrinks as it rises.
 */
internal fun OnboardingEmber.frameAt(progress: Float): OnboardingEmberFrame? {
    val age = (progress - delay) / lifetime
    if (age < 0f || age >= 1f) return null
    val risen = 1f - (1f - age) * (1f - age)
    return OnboardingEmberFrame(
        x = start + sway * sin(age * swayCount * 2f * PI.toFloat()),
        y = 1f + EMBER_START_BELOW - rise * risen,
        scale = 1f - EMBER_SHRINK * age,
        alpha = (age / EMBER_FLARE_UP).coerceAtMost(1f) * (1f - age),
    )
}

private fun Random.nextFloat(from: Float, until: Float) = from + nextFloat() * (until - from)

private const val EMBER_START_BELOW = 0.03f
private const val EMBER_SHRINK = 0.6f
private const val EMBER_FLARE_UP = 0.08f
