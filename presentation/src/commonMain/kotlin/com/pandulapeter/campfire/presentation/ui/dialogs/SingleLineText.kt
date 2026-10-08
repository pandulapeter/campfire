/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

/** A field whose value becomes one line of a song file: a pasted line break is the space between two words. */
internal fun String.asSingleLine() = replace(lineBreakRegex, " ")

private val lineBreakRegex = Regex("[\\r\\n]+")
