/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * What the page the browser lands on after consent should say.
 *
 * It exists because of the desktop, which has no custom scheme to be redirected to and so answers the browser with
 * a page of its own. That page is the only piece of Campfire's own text that is rendered outside the app, and the
 * data layer can see neither the translations nor the language the user picked - so the words are handed down from
 * the UI, like any other string the user reads.
 *
 * Every other platform ignores this: their browser closes itself.
 */
data class AuthorizationCompletionPage(
    val title: String,
    val message: String
)
