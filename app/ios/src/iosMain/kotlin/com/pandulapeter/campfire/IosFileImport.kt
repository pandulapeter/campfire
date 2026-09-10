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

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.Foundation.NSURL

/**
 * Files iOS hands over, which arrive through Swift's `onOpenURL` rather than through anything Compose can see. The
 * channel is a top level one because the URL can arrive before the view controller (and with it the view model)
 * exists: a cold start caused by opening a file delivers it while the UI is still being built.
 */
private val pendingImports = Channel<List<ImportedFile>>(Channel.BUFFERED)

internal val filesToImport = pendingImports.receiveAsFlow()

/**
 * Called from Swift for every URL iOS hands the app, which in practice means a file opened with Campfire.
 *
 * A sync redirect is answered by the `ASWebAuthenticationSession` itself and never reaches this, but one is
 * recognised and ignored anyway: read as a song it would fail, and the user would be told a file could not be
 * imported that they never tried to import.
 */
@Suppress("unused")
fun openUrl(url: NSURL) {
    val absoluteString = url.absoluteString.orEmpty()
    if (!isSyncRedirect(absoluteString)) {
        url.readImportedFile()?.let { pendingImports.trySend(listOf(it)) }
    }
}
