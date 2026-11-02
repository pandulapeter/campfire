/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.sharedLinkSetlistFileNamesWrittenBy
import com.pandulapeter.campfire.data.model.importing.sharedLinkSongFileNamesWrittenBy
import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.implementation.library.RememberDemoLibraryFilesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.library.RememberSharedLinkImportUseCaseImpl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class ApplyImportUseCaseImpl(
    private val importFiles: ImportFilesUseCase,
    private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCaseImpl,
    private val rememberSharedLinkImport: RememberSharedLinkImportUseCaseImpl,
) : ApplyImportUseCase {

    override suspend operator fun invoke(
        plan: ImportPlan,
        choices: ImportChoices,
        bookkeeping: ImportBookkeeping,
        onProgress: (ImportProgress) -> Unit,
    ) =
        // Not cancellable once it has started writing: the view model going away with the Android activity is no
        // reason to leave an archive half imported - its setlists come after all of its songs - and the
        // repositories the files go into outlive it, so whatever screen comes back finds the whole import.
        withContext(NonCancellable) {
            importFiles.invoke(
                plan = plan,
                choices = choices,
                onProgress = onProgress,
            ).also { result ->
                val demoSongFileNames = bookkeeping.demoSongFileNames
                val demoSetlistFileNames = bookkeeping.demoSetlistFileNames
                if (demoSongFileNames != null && demoSetlistFileNames != null) {
                    rememberDemoLibraryFiles(
                        songFileNames = result.importedSongFileNames.filter { it in demoSongFileNames },
                        setlistFileNames = result.importedSetlistFileNames.filter { it in demoSetlistFileNames },
                    )
                }
                bookkeeping.sharedLinkParts.forEach { part ->
                    val linkPlan = part.of(plan)
                    rememberSharedLinkImport(
                        linkFileId = part.fileId,
                        songFileNames = sharedLinkSongFileNamesWrittenBy(linkPlan, result),
                        setlistFileNames = sharedLinkSetlistFileNamesWrittenBy(linkPlan, result),
                    )
                }
            }
        }
}
