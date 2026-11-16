/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * How often songs have been opened on this device, which decides when the app asks a regular user to tell others
 * about it. Counting stops once that question is settled, so these are a floor rather than a tally.
 *
 * @param count Every song details screen opened, all songs together.
 * @param dayCount The different days among those opens, by the device's local date.
 * @param lastEpochDay The local date of the latest open, in days since 1970-01-01, null before the first one.
 */
data class SongOpens(
    val count: Int = 0,
    val dayCount: Int = 0,
    val lastEpochDay: Long? = null,
)
