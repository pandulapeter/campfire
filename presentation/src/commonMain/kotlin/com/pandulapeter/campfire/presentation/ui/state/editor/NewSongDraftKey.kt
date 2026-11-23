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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field

/**
 * [draft], a new song's text read from a paste, a drop, a share, or a page, with the key [keyOfChords] finds its chords in
 * filled into its `{key}` line (the empty one the draft's header is given, else a new one where the header's lines go),
 * so that the song starts in the key song details would name for it anyway. Unchanged where the draft declares a key, a
 * page's "Key: G" included, or its chords make none clear (no chords, several keys, a songbook).
 *
 * @param keyOfChords The key a song file's text is written in, as its `{key}` would name it, the shapes above its
 *   `{capo}` included; null where none can be told. The scale walk, so never called on the main thread.
 */
internal fun withInferredKey(draft: String, keyOfChords: (text: String) -> String?): String {
    if (!ChordProParser.parseMetadata(draft).key.isNullOrBlank()) return draft
    val key = keyOfChords(draft) ?: return draft
    return ChordProMetadataFields.set(text = draft, values = mapOf(Field.KEY to key))
}
