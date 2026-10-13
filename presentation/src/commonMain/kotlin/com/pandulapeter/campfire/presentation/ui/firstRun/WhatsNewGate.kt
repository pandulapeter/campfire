/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

/**
 * Whether What's new may open now. An import's conflicts question is not an import running, and is only put on the
 * back stack once no dialog is up, so a report waiting to be shown has to hold the dialog back as well; so does a
 * batch opened with the app that is still waiting in the queue, which no import has taken yet.
 */
internal fun canShowWhatsNew(
    hasDialog: Boolean,
    isImporting: Boolean,
    hasImportReport: Boolean,
    queuedImportCount: Int,
) = !hasDialog && !isImporting && !hasImportReport && queuedImportCount == 0

/**
 * Whether an owed onboarding may go up now: not over a dialog, and not over an import, whose screen is a question
 * too - one already reported, or one of a file the app was opened with that is still waiting to be read.
 */
internal fun canShowOnboarding(hasDialog: Boolean, hasImportReport: Boolean, hasQueuedImport: Boolean) = !hasDialog && !hasImportReport && !hasQueuedImport
