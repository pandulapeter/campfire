/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

/**
 * Which way a text is read: what a song's body is laid out in (see `ChordProSong.textDirection`), whatever the
 * direction of the interface around it. Its own type, since this module sees nothing of the UI's.
 */
public enum class WritingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}
