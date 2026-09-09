package com.pandulapeter.campfire.data.model.domain

/**
 * What one import batch did, as the file names it produced. Skipped files are reported rather than hidden: a picker
 * lets the user choose anything, and silently ignoring half of it would look like the import lost them.
 */
data class ImportResult(
    val importedSongFileNames: List<String> = emptyList(),
    val importedSetlistFileNames: List<String> = emptyList(),
    val skippedFileNames: List<String> = emptyList()
)
