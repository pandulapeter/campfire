package com.pandulapeter.campfire.data.model.domain

/**
 * One `*.setlist.json` file in the library. The transposition of a song lives in the entry rather than in the user
 * preferences, so that it travels with the setlist when the library is exported.
 */
data class Setlist(
    /** The file name inside the setlists directory, extension included. Unique, and the identity of the setlist. */
    val fileName: String,
    val title: String,
    /** Higher first, so that the newest setlist is on top. */
    val priority: Int,
    val entries: List<Entry>
) {

    data class Entry(
        val songFileName: String,
        val transposition: Int = 0
    )
}
