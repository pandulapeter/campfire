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

import com.pandulapeter.campfire.data.repository.implementation.DataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.DataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.implementation.DataRemoteSourceModule
import com.pandulapeter.campfire.data.sync.implementation.DataSyncModule
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.implementation.DomainModule
import com.pandulapeter.campfire.metronome.implementation.MetronomeModule
import com.pandulapeter.campfire.presentation.PresentationModule
import com.pandulapeter.campfire.tuner.implementation.TunerModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        DataSyncModule::class,
        DomainModule::class,
        MetronomeModule::class,
        PresentationModule::class,
        TunerModule::class,
    ],
)
private object CampfireDependencyGraph

/**
 * Starts Koin with every module of the app, and the first read of the preferences and the library with it. Each
 * platform's entry point calls it once, before anything asks for the view model, which joins the read started here
 * rather than starting another one.
 *
 * @param configuration What only the platform can add: the Android shell's `androidContext`, which the file storage
 *   and the authenticator there are built from. The other three have nothing to add.
 */
fun startCampfireDependencyGraph(configuration: KoinAppDeclaration = {}) =
    startKoin<CampfireDependencyGraph>(configuration).also { koinApplication ->
        // The read the launch screen waits for, started before the platform has brought its window up rather than when
        // the first composition gets as far as the view model: the view model asks for the same read and joins this
        // one, since a repository holds its lock for as long as its read takes. The use case is resolved inside the
        // coroutine so that building the repositories, the local sources and the file storage leaves the main thread
        // too. It is asked for with getOrNull because the compiler plugin's check of a call in this module does not see
        // the definitions of the other modules on the iOS and web targets, and fails a plain get there; the graph check
        // above already guarantees the definition, which the view model's constructor asks for too.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                koinApplication.koin.getOrNull<LoadScreenDataUseCase>()?.invoke(isRescan = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // The view model asks again and reports what it finds; nothing here may keep the app from starting.
                println("Could not start reading the library: ${exception.message}")
            }
        }
    }
