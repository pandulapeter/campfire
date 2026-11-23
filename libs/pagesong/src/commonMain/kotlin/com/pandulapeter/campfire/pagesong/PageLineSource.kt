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

/** Where a page's lines came from, which the model reads as one of its inputs. */
public enum class PageLineSource {

    /** A web page's text (a share, a paste, a loaded link): no layout. */
    PAGE,

    /** A PDF's or a Word file's placed lines, each with its [PageLineLayout]. */
    DOCUMENT,
}
