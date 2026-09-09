package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.domain.ImportedFile
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

/** Called from Swift when a file is opened with Campfire. */
@Suppress("unused")
fun importFile(url: NSURL) {
    url.readImportedFile()?.let { pendingImports.trySend(listOf(it)) }
}
