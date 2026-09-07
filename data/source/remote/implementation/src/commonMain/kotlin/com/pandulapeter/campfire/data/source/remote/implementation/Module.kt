package com.pandulapeter.campfire.data.source.remote.implementation

import org.koin.core.module.Module

/**
 * The web build talks to Google Sheets without Retrosheet (see [networking.NetworkManager]), so the two variants also
 * differ in how the shared [io.ktor.client.HttpClient] is set up.
 */
expect val dataRemoteSourceModule: Module
