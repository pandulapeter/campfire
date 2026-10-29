/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * A string resource with text somebody else wrote put into its `%1$s`, `%2$s`… placeholders: a title, a tag, a
 * value out of a song's header, the name of a file or of an account, a chord or a key. Each text goes in [isolated],
 * so that a title in another direction than the sentence's keeps its own order and leaves the sentence's alone.
 *
 * The localization plugin's own formatter must not be given such text. It fills the positional placeholders and
 * then scans what it has produced for sequential ones, by which time the text is part of the sentence - and its
 * pattern takes a space for a flag, so the `% s` in "100% sure" is a specifier to it and the title is put into
 * itself. So the template is asked for as it is written, which is what the formatted lookup answers when it is
 * given no arguments, and filled in here in a single pass, where nothing that has been put in is read again.
 *
 * Only `%N$s` and `%N$d` are understood, which is all a sentence carrying text needs; a number that has to go into
 * the same sentence is passed as the text it should read as. Strings that only take numbers, or text the app itself
 * wrote (a store's name, the version), keep using `stringResource`.
 */
@Composable
internal fun textResource(key: StringResource, vararg texts: String) =
    LocalizedStrings.getFormatted(key, locale = currentLanguage.value).withTexts(*texts)

/**
 * [textResource] for a sentence that does not stay on the screen: a field's first value, a line of the PDF, a template
 * filled later. Its texts go in as they are, since the isolating marks would be written, printed, or typed over.
 */
@Composable
internal fun plainTextResource(key: StringResource, vararg texts: String) =
    LocalizedStrings.getFormatted(key, locale = currentLanguage.value).withPlainTexts(*texts)

/**
 * The one way a `<plurals>` is read: the item for [quantity] by the app language's rule ([pluralTemplate]), filled
 * like [textResource]. The count goes in as the text it reads as, like any other number in such a sentence, usually
 * `count.toString()` as the first text.
 */
@Composable
internal fun pluralTextResource(key: PluralStringResource, quantity: Int, vararg texts: String) =
    pluralTemplate(key, quantity, currentLanguage.value).withTexts(*texts)

/** [pluralTextResource] for a sentence that does not stay on the screen, as [plainTextResource] is. */
@Composable
internal fun plainPluralTextResource(key: PluralStringResource, quantity: Int, vararg texts: String) =
    pluralTemplate(key, quantity, currentLanguage.value).withPlainTexts(*texts)

/** Fills the `%N$s` and `%N$d` placeholders of a template with [texts], each [isolated], as [textResource] does. */
internal fun String.withTexts(vararg texts: String) = withPlainTexts(*Array(texts.size) { texts[it].isolated() })

/** Fills the `%N$s` and `%N$d` placeholders of a template with [texts], leaving one that names no text as it is written. */
internal fun String.withPlainTexts(vararg texts: String) = TEXT_PLACEHOLDER.replace(this) { match ->
    match.groupValues[1].toIntOrNull()?.let { texts.getOrNull(it - 1) } ?: match.value
}

/** Compiled once for the process: this runs for every song row that names a key. */
private val TEXT_PLACEHOLDER = Regex("%(\\d+)\\\$[sd]")
