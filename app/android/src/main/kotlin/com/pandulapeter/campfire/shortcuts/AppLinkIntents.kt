/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The app's own `campfire://` links the activity was handed, kept outside it like the files to import, for the same
 * reason: an intent is acted on once, and one that starts the app arrives before its composition does.
 */
private val pendingAppLinks = Channel<String>(Channel.BUFFERED)

internal val appLinksToOpen = pendingAppLinks.receiveAsFlow()

/** Whether [address] is one of the app's own links, asked after the sign-in redirect and the shared links of its scheme. */
internal fun isAppLink(address: Uri) = address.scheme.equals(APP_LINK_SCHEME, ignoreCase = true)

/**
 * Queues the app link [intent] carries, and tells the launcher where it was a shortcut's ([LauncherShortcutPublisher]),
 * so that it can learn which ones are used.
 */
internal fun Intent.openAppLink(address: Uri, onShortcutUsed: (String) -> Unit) {
    getStringExtra(LauncherShortcutPublisher.EXTRA_SHORTCUT_ID)?.let(onShortcutUsed)
    pendingAppLinks.trySend(address.toString())
}

/**
 * The intent that opens [link], one of the app's own, from the launcher (a shortcut, a widget): an `ACTION_VIEW` aimed at
 * `.CampfireIntentActivity`, whose filter it has to match, since from Android 15 on an explicit intent from another app,
 * which the launcher is, must match one of the target's filters. No flags are added, so the running app's task is kept
 * and the link arrives in `onNewIntent`.
 */
internal fun appLinkIntent(context: Context, link: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    .setComponent(ComponentName(context.packageName, INTENT_ACTIVITY))

private const val APP_LINK_SCHEME = "campfire"

/**
 * The alias's name under the namespace, spelled out for the reason `AppIconSwitcher` spells its own out: a release
 * build's classes are moved out of this package.
 */
private const val INTENT_ACTIVITY = "com.pandulapeter.campfire.CampfireIntentActivity"
