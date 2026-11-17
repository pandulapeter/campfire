/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSFileWriteOutOfSpaceError
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSUnderlyingErrorKey
import platform.posix.ENOSPC

/**
 * The cause a failed Foundation write is reported with: an [OutOfSpaceException] where the volume is full, which Cocoa
 * says as `NSFileWriteOutOfSpaceError` or passes on as POSIX's `ENOSPC`, else nothing, the error's description being in
 * the message.
 */
internal fun NSError?.asStorageCause(): Throwable? = if (isOutOfSpace()) OutOfSpaceException() else null

private fun NSError?.isOutOfSpace(): Boolean = this != null && (
    (domain == NSCocoaErrorDomain && code == NSFileWriteOutOfSpaceError) ||
        (domain == NSPOSIXErrorDomain && code == ENOSPC.toLong()) ||
        (userInfo[NSUnderlyingErrorKey] as? NSError).isOutOfSpace()
    )
