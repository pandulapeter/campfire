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

import com.pandulapeter.campfire.data.model.library.ExportedFile

/**
 * Everything the process printed lately, oldest line first, each one stamped with the time it was written at: the
 * diagnostics of the shared code and the shells, which are plain `println`s, the platform's own error output, and every
 * exception nobody caught. Campfire has no crash reporting by design, so this, shared by the user from Settings, is what a
 * bug report can go on. Null where the shell installed no log, or it could not be read.
 *
 * Each shell installs it as early as it can (`installProcessLog`), into a place that is never exported, synced, or backed
 * up. It is bounded on every platform: [ProcessLogWriter] keeps the current file and the one before it.
 */
internal expect fun readProcessLog(): String?

/**
 * Whether the session before this one ended in an exception nobody caught, as the shell found it when it installed the
 * log: a crash on a phone ends the process, so the user can only be asked about it the next time. Never on the web, whose
 * uncaught errors come as often from a browser extension or a request nobody waited for as from the app, and where the
 * page goes on running after them.
 */
internal expect fun hasPendingCrashReport(): Boolean

/** Forgets the crash [hasPendingCrashReport] reported, once the user has answered about it either way. */
internal expect fun forgetPendingCrashReport()

/** What the log goes out as: a plain `.txt`, which is also a kind of file every browser that shares files shares. */
internal const val PROCESS_LOG_FILE_NAME = "campfire-log.txt"

/** Whether the log is handed to the share sheet where it cannot be mailed, which also decides what sending it is called. */
internal val FilePicker.canShareProcessLog get() = canShareFile(PROCESS_LOG_FILE_NAME, ExportedFile.TEXT_MIME_TYPE)

/** Where the log goes, from Settings and after a crash, attached to a mail where the platform can. */
internal const val DEVELOPER_EMAIL = "pandulapeter@gmail.com"
