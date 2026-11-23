/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.importing.ImportPlan

/**
 * What a new song made of this entry is suggested for the title and artist its text does not declare: the ones its
 * page's header lines name ([ImportPlan.SongEntry.pageTitle]), each before [subject]'s (a share's subject or the page's
 * title). Its title is blank where only an artist is known, which the draft reads as no title.
 */
internal fun ImportPlan.SongEntry.pageHeaderOver(subject: PageTitle?): PageTitle? {
    if (pageTitle == null && pageArtist == null) return subject
    return PageTitle(title = pageTitle ?: subject?.title.orEmpty(), artist = pageArtist ?: subject?.artist)
}
