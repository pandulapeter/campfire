/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.data.model.library.ExportSink

/** Collects what a PDF is written into, so a test can read the file back. */
internal class MemoryExportSink : ExportSink {

    private var buffer = ByteArray(0)

    override val position get() = buffer.size.toLong()

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        buffer += bytes.copyOfRange(offset, offset + length)
    }

    fun bytes() = buffer.copyOf()
}

/** Finishes the PDF written into [sink] and reads it back. */
internal suspend fun PrintPdfWriter.finishedBytes(sink: MemoryExportSink): ByteArray {
    finish()
    return sink.bytes()
}

/** The PDF of [document], read back whole. */
internal suspend fun PrintRenderer.pdfBytes(document: PrintDocument, title: String) = MemoryExportSink().also { pdf(document, title, it) }.bytes()
