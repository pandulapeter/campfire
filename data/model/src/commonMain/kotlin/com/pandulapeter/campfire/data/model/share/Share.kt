/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

import kotlinx.datetime.LocalDate

/**
 * A song or setlist shared as a link from the connected account, as the account's list of shares names it: every
 * device connected to the account sees the same ones.
 *
 * @param shareId The shared copy's name in the cloud folder, fixed for the life of the share.
 * @param fileName The library file the copy is built from. It follows a rename made in Campfire, so the link survives.
 * @param title The item's title, for display only.
 * @param link The link people are given. It carries [title], which may differ from the title in a link handed out
 *   before a rename; both open the same copy.
 * @param sharedAt The day it was shared, for display only; null where the list does not say.
 */
data class Share(
    val shareId: String,
    val kind: SharedLinkKind,
    val fileName: String,
    val title: String,
    val link: SharedLink.Readable,
    val sharedAt: LocalDate?,
    val state: ShareState,
)
