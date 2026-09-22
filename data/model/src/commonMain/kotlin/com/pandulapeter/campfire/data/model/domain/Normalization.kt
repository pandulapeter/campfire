/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * [this] in Unicode Normalization Form C: one code point per character wherever the standard has one.
 *
 * The same name arrives in two forms depending on where it was typed. macOS and iOS hand out file names decomposed -
 * `и` followed by U+0306 for `й` - while Android, Windows, Linux and the browser hand out the composed form, and
 * nothing about the two looks different anywhere in the app. Without this, one song imported from a Mac is filed next
 * to itself as `катюша_2.cho`, and one synced from an iPhone goes up a second time.
 *
 * Every platform has this in its standard library; none of them agrees on where.
 */
expect fun String.normalizedToNfc(): String
