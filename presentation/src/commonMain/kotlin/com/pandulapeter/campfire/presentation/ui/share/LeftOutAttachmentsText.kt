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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.share_attachments_left_out

/**
 * How many attachments [share]'s copy leaves out for their size, as the export screen's share section and Settings →
 * Sharing both say it, or null where it carries every one.
 */
@Composable
internal fun leftOutAttachmentsText(share: Share): String? = share.leftOutAttachmentCount.takeIf { it > 0 }?.let { count ->
    pluralTextResource(Res.plurals.share_attachments_left_out, count, count.toString())
}
