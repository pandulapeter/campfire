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
 * Where the data and domain layers say what went wrong and was not worth an exception: a clean-up that failed, a file
 * a run could not read, a write nobody waits for. Injected rather than called as `println`, so that a test can check
 * that such a failure was noticed - and that a line written to leave out an exception's message, which may quote a
 * token, really does.
 */
fun interface Logger {

    fun log(message: String)

    companion object {

        /** Standard output, which is what the desktop app's campfire.log and the platforms' consoles collect. */
        val Standard = Logger { println(it) }
    }
}
