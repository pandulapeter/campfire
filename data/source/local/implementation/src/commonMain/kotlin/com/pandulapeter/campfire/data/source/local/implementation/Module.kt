package com.pandulapeter.campfire.data.source.local.implementation

import org.koin.core.module.Module

/**
 * Room has no wasmJs target, so every platform except web is wired up by the shared Room implementation in `roomMain`,
 * while the web build gets a `localStorage`-backed one.
 */
expect val dataLocalSourceModule: Module
