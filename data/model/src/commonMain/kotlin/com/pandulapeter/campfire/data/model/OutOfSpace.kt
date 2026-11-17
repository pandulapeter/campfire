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

/**
 * A write refused because the device has no room left (on the web, the origin's quota), set as the cause of the storage
 * failure that reports it, so that the user is told to free some space rather than that something went wrong. Each
 * platform's storage recognizes its own sign of it: `ENOSPC` or its message on the JVM and Android, the Cocoa or POSIX
 * error on iOS, a `QuotaExceededError` on the web.
 */
class OutOfSpaceException(cause: Throwable? = null) : Exception("There is no space left to write to.", cause)

/** Whether this or any of its causes is an [OutOfSpaceException]. */
fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH).any { it is OutOfSpaceException }

private const val MAX_CAUSE_DEPTH = 16
