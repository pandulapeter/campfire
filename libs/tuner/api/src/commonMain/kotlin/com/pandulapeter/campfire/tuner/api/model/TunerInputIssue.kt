/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api.model

/** What keeps an open input from hearing anything. */
public enum class TunerInputIssue {
    /**
     * Every sample has been zero, or a sample or two of dither, for a while: the desktop operating systems hand an app
     * that may not record silence rather than an error, a muted input looks the same, and so does one whose system
     * processing removes everything that is not speech. A live input can look the same too (a clean audio interface with
     * its gain turned down in a quiet room), so this is a reason to look, not proof that nothing can be heard.
     */
    SILENT,

    /** The web's audio waits for a tap or a key before it may run. */
    WAITING_FOR_GESTURE,
}
