/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.import_retry
import com.pandulapeter.campfire.presentation.resources.import_review_confirm
import org.jetbrains.compose.resources.painterResource

/**
 * The way on from each stage: Import with the number of files it writes under review (dimmed and inert while nothing is
 * ticked, absent where there is nothing to tick), Try again after a failure, and Done once the import has an answer.
 * Nothing while the files are read or written: there is nothing to do then but wait, and Close is still there.
 *
 * @param importCount How many files Import writes; null where the review has nothing to tick.
 * @param isImportEnabled False while files are being added to the review, whose count is not final yet.
 * @param isKeyboardVisible Hides the button while the keyboard is up: that is when the rows it would cover are being
 *   searched, and in a window that leaves little height it sits over the tick boxes and the conflict buttons at their ends.
 *
 * Each button is described by its own label, since Material's extended floating button clears the semantics of the
 * text it draws, and would otherwise be read as a button with no name.
 */
@Composable
internal fun ImportReportButton(
    modifier: Modifier = Modifier,
    report: ImportReport,
    importCount: Int?,
    isImportEnabled: Boolean,
    isKeyboardVisible: Boolean,
    onImport: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) = AnimatedContent(
    modifier = modifier,
    targetState = if (isKeyboardVisible || (report is ImportReport.Review && importCount == null)) null else report::class,
    transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
    contentAlignment = Alignment.BottomEnd,
) { stage ->
    when (stage) {
        ImportReport.Review::class -> {
            val count = importCount ?: 0
            val isEnabled = count > 0 && isImportEnabled
            val label = pluralStringResource(Res.plurals.import_review_confirm, count, count)
            val containerColor by animateColorAsState(
                if (isEnabled) FloatingActionButtonDefaults.containerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            val contentColor by animateColorAsState(
                if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            // A floating button has no disabled state of its own, so it is drawn and announced as one and ignores taps.
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics {
                    contentDescription = label
                    if (!isEnabled) disabled()
                },
                onClick = { if (isEnabled) onImport() },
                containerColor = containerColor,
                contentColor = contentColor,
                icon = { Icon(painter = painterResource(Res.drawable.ic_import), contentDescription = null) },
                text = { Text(label) },
            )
        }

        ImportReport.Failed::class -> {
            val label = stringResource(Res.string.import_retry)
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = label },
                onClick = onRetry,
                icon = { Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = null) },
                text = { Text(label) },
            )
        }

        ImportReport.Finished::class -> {
            val label = stringResource(Res.string.done)
            ExtendedFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = label },
                onClick = onDone,
                icon = { Icon(painter = painterResource(Res.drawable.ic_check), contentDescription = null) },
                text = { Text(label) },
            )
        }

        else -> Box(Modifier)
    }
}
