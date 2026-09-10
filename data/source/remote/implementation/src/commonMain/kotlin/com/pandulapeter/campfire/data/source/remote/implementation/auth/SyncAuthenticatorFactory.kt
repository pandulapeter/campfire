package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import org.koin.core.scope.Scope

internal expect fun Scope.createSyncAuthenticator(): SyncAuthenticator

/**
 * The scheme every platform that can be redirected back to registers. Kept here rather than in each platform's own
 * file so that the four of them, the Android manifest and the iOS `Info.plist` cannot drift apart.
 */
internal const val REDIRECT_SCHEME = "campfire"
internal const val REDIRECT_HOST = "oauth"
