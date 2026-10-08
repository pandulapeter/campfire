/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.repository.implementation.RemainingFiles

/**
 * A [BaseLocalDataRepository] of library files, one item per file, whose changes all replace or drop the cached entries
 * of the files they name. An item that is put in goes to the end of the list, which is in no particular order anyway.
 */
internal abstract class LibraryListRepository<T> : BaseLocalDataRepository<List<T>>() {

    /** The file name, which identifies an item in the library. */
    protected abstract fun fileNameOf(item: T): String

    /** [item] in place of the cached entry of its file, and of [replacing]'s, the name it was moved from, if any. */
    protected fun putInCache(item: T, replacing: String? = null) = updateData { current ->
        current.orEmpty().filterNot { fileNameOf(it) == replacing || fileNameOf(it) == fileNameOf(item) } + item
    }

    /** [items] in place of the cached entries of [fileNames], which drops every one of those files [items] lacks. */
    protected fun replaceInCache(fileNames: Set<String>, items: Collection<T>) = updateData { current ->
        current.orEmpty().filterNot { fileNameOf(it) in fileNames } + items
    }

    protected fun dropFromCache(fileName: String) = updateData { current ->
        current.orEmpty().filterNot { fileNameOf(it) == fileName }
    }

    /** Only the cached entries of the files a deletion of every file left behind. */
    protected fun keepOnlyInCache(remaining: RemainingFiles) = updateData { current ->
        current.orEmpty().filter { fileNameOf(it) in remaining }
    }
}
