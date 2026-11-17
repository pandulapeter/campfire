/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.repository.implementation.DataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.DataLocalSourceModule
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.remote.implementation.DataRemoteSourceModule
import com.pandulapeter.campfire.data.sync.implementation.DataSyncModule
import com.pandulapeter.campfire.domain.implementation.DomainModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.modules

/**
 * The app's data modules and the domain's use cases over [storage], as the app wires them, but for the network, whose
 * downloads [downloads] answers: one process of the app, its own staging session included.
 */
internal fun importGraph(storage: FileStorage, downloads: ImportDownloadRepository) = koinApplication {
    modules(
        DataLocalSourceModule::class,
        DataRemoteSourceModule::class,
        DataRepositoryModule::class,
        DataSyncModule::class,
        DomainModule::class,
        PresentationStandIns::class,
    )
    modules(
        module {
            single<FileStorage> { storage }
            single<ImportDownloadRepository> { downloads }
            // Every unpack prints what it leaves out; a few thousand imports would bury the test's own report.
            single<Logger> { Logger { } }
        },
    )
}
