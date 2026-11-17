/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * A file that goes with a song — a PDF of the sheet music, a recording, a video — carried as
 * `{meta: attachment 3fa9c2e81b7d4f06.pdf Rehearsal take.pdf}`. [source] is the file of the library it names, never an
 * address or a path; [name] is what it is shown as, null where the line gives none, whose label belongs to the caller.
 * [source] alone identifies an attachment, so two names for one file are one attachment.
 */
public data class ChordProAttachment(
    val source: String,
    val name: String? = null,
)
