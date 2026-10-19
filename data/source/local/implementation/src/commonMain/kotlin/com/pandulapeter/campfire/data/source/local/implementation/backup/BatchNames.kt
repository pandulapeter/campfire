/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

/**
 * The names [ForeignSongHeader.uniqueName] has handed out in one batch, in lower case, and the number each name tries
 * next. A backup can hold tens of thousands of songs under one title, and counting up from 2 for every one of them
 * would take as many steps as their square.
 */
internal class BatchNames {
    val used = mutableSetOf<String>()
    val nextNumbers = mutableMapOf<String, Int>()
}
