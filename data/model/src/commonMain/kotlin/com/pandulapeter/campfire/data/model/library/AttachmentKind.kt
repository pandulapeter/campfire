/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * What a file attached to a song holds, by the extension of its name (one of [LibraryFiles.ATTACHMENT_EXTENSIONS]): what
 * the song info sheet draws its icon and its unnamed label by, and the MIME type it is handed to another app under.
 */
enum class AttachmentKind {
    DOCUMENT,
    AUDIO,
    VIDEO,
    PICTURE,
    MUSIC_NOTATION,
    OTHER,
    ;

    companion object {

        /** The kind of a file called [name]; [OTHER] for an extension none of the kinds lists. */
        fun of(name: String): AttachmentKind = when (extensionOf(name)) {
            in DOCUMENT_EXTENSIONS -> DOCUMENT
            in AUDIO_EXTENSIONS -> AUDIO
            in VIDEO_EXTENSIONS -> VIDEO
            in PICTURE_EXTENSIONS -> PICTURE
            in MUSIC_NOTATION_EXTENSIONS -> MUSIC_NOTATION
            else -> OTHER
        }

        /**
         * The MIME type a file called [name] is handed to the platform under, which is what decides the apps offered to
         * open it: the registered type of its extension, else the generic binary type, which every platform hands to
         * whatever claims the extension.
         */
        fun mimeTypeOf(name: String): String = MIME_TYPES[extensionOf(name)] ?: BINARY_MIME_TYPE

        private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

        private val DOCUMENT_EXTENSIONS = setOf("pdf", "txt", "md", "rtf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "aif", "aiff", "mid", "midi")
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv")
        private val PICTURE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "tif", "tiff")
        private val MUSIC_NOTATION_EXTENSIONS = setOf("gp", "gp3", "gp4", "gp5", "gpx", "mscz", "musicxml", "mxl", "abc")

        private const val BINARY_MIME_TYPE = "application/octet-stream"

        private val MIME_TYPES = mapOf(
            "pdf" to "application/pdf",
            "txt" to "text/plain",
            "md" to "text/markdown",
            "rtf" to "application/rtf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "odt" to "application/vnd.oasis.opendocument.text",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "odp" to "application/vnd.oasis.opendocument.presentation",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "wav" to "audio/wav",
            "flac" to "audio/flac",
            "ogg" to "audio/ogg",
            "opus" to "audio/opus",
            "aif" to "audio/aiff",
            "aiff" to "audio/aiff",
            "mid" to "audio/midi",
            "midi" to "audio/midi",
            "mp4" to "video/mp4",
            "m4v" to "video/x-m4v",
            "mov" to "video/quicktime",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "heic" to "image/heic",
            "tif" to "image/tiff",
            "tiff" to "image/tiff",
            "musicxml" to "application/vnd.recordare.musicxml+xml",
            "mxl" to "application/vnd.recordare.musicxml",
            "abc" to "text/vnd.abc",
        )
    }
}
