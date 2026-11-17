/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

/**
 * Every library file [text] names: the pictures of its image directives and the files of its attachment lines, both
 * read generously ([ChordProImages.referencedSources], [ChordProAttachments.referencedSources]). The one answer to which
 * files a song keeps, so that the pruning, sync, export, the shared copy, and the import all keep the same ones: a file
 * one of them missed would be deleted. `ChordProSummary.librarySources` holds the same set.
 */
public fun libraryFilesNamedBy(text: String): Set<String> = ChordProImages.referencedSources(text) + ChordProAttachments.referencedSources(text)
