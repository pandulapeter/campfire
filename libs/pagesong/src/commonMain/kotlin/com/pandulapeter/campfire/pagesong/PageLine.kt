/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/**
 * One line of a page or a document as it was read, before anything is converted.
 *
 * @param text the line exactly as the source gave it; invisible format characters (a word joiner, a zero-width space) are
 * ignored by the model, never removed from what the caller keeps.
 * @param layout how a document set the line; null for a web page's text, and read only when the source is
 * [PageLineSource.DOCUMENT] (a document line without one is read as body text in no column on the first page).
 */
public data class PageLine(
    val text: String,
    val layout: PageLineLayout? = null,
)
