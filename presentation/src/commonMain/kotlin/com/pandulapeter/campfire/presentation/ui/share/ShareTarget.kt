/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlinx.serialization.Serializable

/**
 * A song or a setlist that can be shared as a link, by its library file. Serializable, since a consent page asked for
 * from the export screen carries it across the start up that answers it (`SyncConsentOrigin.Export`).
 */
@Serializable
internal data class ShareTarget(
    val kind: SharedLinkKind,
    val fileName: String,
)

/**
 * The share of [target] among [shares], if it is shared, under any case or Unicode form of its name, as the account's
 * list matches it.
 */
internal fun shareOf(shares: List<Share>, target: ShareTarget) =
    shares.firstOrNull { it.kind == target.kind && LibraryFiles.isSameLibraryName(it.fileName, target.fileName) }
