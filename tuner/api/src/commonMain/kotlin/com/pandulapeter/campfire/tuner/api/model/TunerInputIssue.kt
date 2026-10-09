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
     * Every sample has been exactly zero for a while, which a live microphone never is: the desktop operating systems
     * hand an app that may not record silence rather than an error, and a muted input looks the same.
     */
    SILENT,

    /** The web's audio waits for a tap or a key before it may run. */
    WAITING_FOR_GESTURE,
}
