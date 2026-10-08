/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState

/** A value whose first read failed stands in as [default]; a value that is merely not read yet stays missing. */
internal fun <T> DataState<T>.orWhenUnreadable(default: T): DataState<T> =
    if (this is DataState.Failure && data == null) DataState.Failure(default) else this

/**
 * [data] in the state of the inputs it was built from: a failure anywhere beats a load anywhere, which beats every
 * input being idle. An idle state always carries data, so an idle input never leaves [data] without it.
 */
internal fun <T> List<DataState<*>>.combinedState(data: T?): DataState<T> = when {
    any { it is DataState.Failure } -> DataState.Failure(data)
    any { it is DataState.Loading } -> DataState.Loading(data)
    else -> DataState.Idle(data ?: throw IllegalStateException("No data available while all data states are idle."))
}

internal fun <T, R> DataState<T>.mapData(transform: (T) -> R): DataState<R> = when (this) {
    is DataState.Idle -> DataState.Idle(transform(data))
    is DataState.Loading -> DataState.Loading(data?.let(transform))
    is DataState.Failure -> DataState.Failure(data?.let(transform))
}
