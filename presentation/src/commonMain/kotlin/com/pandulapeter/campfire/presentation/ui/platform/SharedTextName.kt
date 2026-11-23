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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * The name a shared text arrives under where the sender gave one: a browser's is the page's title, read as the song it
 * names ([PageTitle.of]), since the name only ever titles a song that declares no title of its own. Null where nothing
 * usable is left.
 */
internal fun sharedTextName(subject: String?): String? =
    PageTitle.of(subject)?.let { listOfNotNull(it.title, it.artist).joinToString(" by ") }?.cleanedForFileName()

/**
 * Where a pasted chord sheet has its title, for a text that declares none: the first line that is neither a directive,
 * a section, a comment, a row of chords nor an address - the lines a chord sheet copied from a page most often starts
 * with, none of which names a song. Null where there is none.
 */
internal fun String.firstPlainLine() = lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotEmpty() && it.first() !in "{[#" && !ChordSheetConverter.isChordLine(it) && !it.isLink() }
    ?.cleanedForFileName()

/**
 * A name for a file made of what arrived, by the rule every name the library writes follows
 * ([LibraryFiles.normalizedName]): lowercase words joined by underscores, accents folded, nothing that would break a
 * file name or hide in one, and a length capped by whole words. Null for a text with no letter or digit in it, which the
 * rule would otherwise answer with its own fallback name rather than with nothing.
 */
internal fun String.cleanedForFileName() = takeIf { it.any(Char::isLetterOrDigit) }?.let(LibraryFiles::normalizedName)
