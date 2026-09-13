/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.di

import com.pandulapeter.campfire.data.repository.DataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.DataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.implementation.DataRemoteSourceModule
import com.pandulapeter.campfire.domain.implementation.DomainModule
import com.pandulapeter.campfire.presentation.PresentationModule
import org.koin.core.annotation.KoinApplication
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin

/**
 * Every Koin module of the app, named once. The compiler plugin checks the whole graph at this declaration, so a
 * definition asking for something no module declares fails the build of this module rather than the first screen
 * that happens to need it.
 */
@KoinApplication(
    modules = [
        DataLocalSourceModule::class,
        DataRemoteSourceModule::class,
        DataRepositoryModule::class,
        DomainModule::class,
        PresentationModule::class,
    ],
)
private object CampfireDependencyGraph

/**
 * Starts Koin with every module of the app. Each platform's entry point calls it once, before anything asks for the
 * view model.
 *
 * @param configuration What only the platform can add: the Android shell's `androidContext`, which the file storage
 *   and the authenticator there are built from. The other three have nothing to add.
 */
fun startCampfireDependencyGraph(configuration: KoinAppDeclaration = {}) = startKoin<CampfireDependencyGraph>(configuration)
