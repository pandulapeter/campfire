/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour behind a section's lines: a chorus card's, or unspecified where the section stands on the page's surface.
 * What is drawn in the background's colour to cut a line (an engraved tablature's frets) reads it, or it would show as
 * a box on a card.
 */
internal val LocalSectionBackground = compositionLocalOf { Color.Unspecified }
