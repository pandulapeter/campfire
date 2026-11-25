/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.PageLineMarks

/**
 * The texts of one share, handed over by a phone's shell as they arrived: Android's `EXTRA_TEXT`, or the texts the iOS
 * share extension left in its inbox. Kept as text rather than made into files by the shell, since what one text comes
 * to - the New song form, an import, or a sentence about a link - is the app's to decide (see
 * `PastedSongController.importSharedText`).
 *
 * @param subject What the sender called the share (`EXTRA_SUBJECT`, an item's `attributedTitle`), which names a text
 *   that declares no title.
 * @param marks The structure marks of the lines of a share's one text where it is a page the system's browser read
 *   itself (the iOS share extension's Safari page, `ShareInboxManifest.marks`); null for any other share.
 */
data class SharedTextPayload(
    val texts: List<String>,
    val subject: String?,
    val marks: List<PageLineMarks?>? = null,
)
