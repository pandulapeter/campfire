/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api.model

/**
 * Where a click was started, which decides what happens to it when the song it plays for is closed: a click started
 * on a song stops with it, while one started on the Metronome tab and lent to a song goes back to the tab's own
 * pattern, so that opening a song does not cost the user their practice click. Kept by the engine rather than the
 * screen, so that a screen built again (an activity recreated, or reopened from the notification) reads it instead of
 * guessing.
 */
sealed interface MetronomeOrigin {

    data object Standalone : MetronomeOrigin

    data class Song(
        val songFileName: String,
        val setlistFileName: String?,
    ) : MetronomeOrigin
}
