/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * A staged file an import planned with ([StagedFile]) is no longer where the staging keeps it, or no longer of its size:
 * the staging folder emptied behind the app's back, or a file it held deleted. Thrown before anything is written, so the
 * import fails with the library as it was, and reading the files again is what can bring it back.
 */
class StagedFileMissingException(val name: String) : Exception("The staged file \"$name\" is gone.")
