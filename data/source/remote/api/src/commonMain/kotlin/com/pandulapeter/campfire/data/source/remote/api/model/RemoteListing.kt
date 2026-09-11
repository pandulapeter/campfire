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
 * Everything in the remote library folder.
 *
 * A wrapper rather than a bare list because this is where a "what changed since" token would go if live updates are
 * ever wanted - Dropbox calls it a `list_folder` cursor, Drive a `startPageToken`. Sync does not need one: it lists
 * the whole folder every run, which for a library of songs is a single request and is right even after a run that
 * was interrupted half way.
 */
data class RemoteListing(
    val files: List<RemoteFile>,
)
