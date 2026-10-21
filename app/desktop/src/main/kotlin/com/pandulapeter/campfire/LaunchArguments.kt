/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.share.ShareLinks
import java.io.File

/**
 * Whether a command line argument is an address rather than a file. Only one with a scheme is: `ShareLinks` also reads
 * a bare payload, which a relative file name such as `2.cho` would pass for (a link from a newer version).
 */
private fun isAddress(argument: String) = argument.startsWith("campfire:", ignoreCase = true) || argument.startsWith("https:", ignoreCase = true)

/**
 * The arguments as another process can take them: paths made absolute, since the two processes do not share a working
 * directory, and addresses untouched, which `File` would turn into a path under the working directory.
 */
internal fun List<String>.withAbsolutePaths() = map { argument -> if (isAddress(argument)) argument else File(argument).absolutePath }

/**
 * Sends the files among the arguments this process was started with, or another process handed over, to [OpenedFiles],
 * and the Campfire links to [SharedLinks]. An address that is not a Campfire link is dropped, never opened as a file.
 */
internal fun openLaunchArguments(arguments: List<String>) {
    val (addresses, paths) = arguments.partition(::isAddress)
    OpenedFiles.open(paths)
    addresses.forEach { address -> ShareLinks.parse(address)?.let(SharedLinks::open) }
}
