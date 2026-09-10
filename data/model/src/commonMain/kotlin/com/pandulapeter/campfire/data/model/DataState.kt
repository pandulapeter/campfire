/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model

sealed class DataState<T> {

    abstract val data: T?

    data class Failure<T>(override val data: T?) : DataState<T>()

    data class Idle<T>(override val data: T) : DataState<T>()

    data class Loading<T>(override val data: T?) : DataState<T>()
}