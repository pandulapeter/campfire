/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

/**
 * What Android's answers to the microphone request say about the next one, kept on the device by itself.
 *
 * @param isForGood Whether the system no longer shows its question, so that only its settings can allow the microphone.
 * @param silentDenials Denials in a row that came with no rationale owed before or after: the question dismissed by a
 *   tap outside, or the system answering without showing it.
 */
internal data class MicrophoneRefusal(
    val isForGood: Boolean = false,
    val silentDenials: Int = 0,
)

/**
 * The [MicrophoneRefusal] an answer to the request leaves, from whether a rationale was owed just before it
 * ([rationaleBefore]) and just after it ([rationaleAfter]). Android owes no rationale for a question never asked, one
 * dismissed, and one refused for good alike, so only the change tells: a rationale that goes with a denial is the
 * second refusal (or the old "Don't ask again" box). A denial with none before or after is a dismissal the first time,
 * but a second in a row is taken as the system answering silently, so that it costs one dead press rather than one per
 * launch.
 */
internal fun microphoneRefusalAfter(
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
    isGranted: Boolean,
    earlierSilentDenials: Int,
): MicrophoneRefusal = when {
    isGranted || rationaleAfter -> MicrophoneRefusal()
    rationaleBefore || earlierSilentDenials > 0 -> MicrophoneRefusal(isForGood = true)
    else -> MicrophoneRefusal(silentDenials = earlierSilentDenials + 1)
}

/**
 * The [MicrophoneRefusal] left on returning from the app's page of the system settings, opened by Open settings, with
 * the microphone still off. Android's "Ask every time" there shows the question again yet owes no rationale, which a
 * refusal for good kept as it was would answer with Open settings alone, endlessly; so it becomes one silent denial,
 * offering the question once more, and a system that still refuses silently costs that one press.
 */
internal fun MicrophoneRefusal.afterSettings(isGranted: Boolean) = when {
    isGranted -> MicrophoneRefusal()
    isForGood -> MicrophoneRefusal(silentDenials = 1)
    else -> this
}

/** The microphone's status as Android reports it, and whether its question can still be shown. */
internal data class AndroidMicrophoneState(
    val status: MicrophoneStatus,
    val canAskAgain: Boolean,
)

/**
 * Android's [AndroidMicrophoneState]: no rationale owed is a question never asked (a grant that lapsed, was reset, or
 * did not come back with a restore included) unless this device stored a refusal for good ([isRefusedForGood]).
 */
internal fun androidMicrophoneStateOf(isGranted: Boolean, rationale: Boolean, isRefusedForGood: Boolean): AndroidMicrophoneState {
    val isRefusedForGoodNow = !isGranted && !rationale && isRefusedForGood
    return AndroidMicrophoneState(
        status = when {
            isGranted -> MicrophoneStatus.GRANTED
            rationale || isRefusedForGoodNow -> MicrophoneStatus.DENIED
            else -> MicrophoneStatus.NOT_ASKED
        },
        canAskAgain = !isRefusedForGoodNow,
    )
}
