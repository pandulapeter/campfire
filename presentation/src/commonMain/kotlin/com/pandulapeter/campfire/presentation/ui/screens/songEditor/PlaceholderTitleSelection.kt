/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.text.TextRange

/**
 * Where a new song's editor puts its selection: over [placeholderTitle] in the song's `{title}` line, so that the first
 * thing typed replaces it, or at the top where the song declares a title of its own.
 */
internal fun placeholderTitleSelection(text: String, placeholderTitle: String): TextRange {
    val prefix = "{title: "
    val start = text.indexOf("$prefix$placeholderTitle}").takeIf { it >= 0 }?.plus(prefix.length) ?: return TextRange.Zero
    return TextRange(start, start + placeholderTitle.length)
}
