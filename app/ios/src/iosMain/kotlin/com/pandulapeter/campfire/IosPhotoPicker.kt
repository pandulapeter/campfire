/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.importing.ImportBudget
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.pictureFileName
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeGIF
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypePNG
import platform.UniformTypeIdentifiers.UTTypeWebP
import platform.UniformTypeIdentifiers.conformsToType
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * The Photos picker, which runs out of the app's process and so needs no permission to read the library: only what the
 * user picks is handed over. Asked for the most compatible representation of each photo, so that a HEIC photo arrives
 * as a JPEG where Photos can make one, and in the order the photos were picked. One at most unless [isMultiple].
 */
internal fun photoPickerController(isMultiple: Boolean) = PHPickerViewController(
    configuration = PHPickerConfiguration().apply {
        filter = PHPickerFilter.imagesFilter
        selectionLimit = if (isMultiple) 0 else 1
        selection = PHPickerConfigurationSelectionOrdered
        preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCompatible
    },
)

/**
 * Reads the photos picked one after another, within one [ImportBudget], in the order they were picked. A photo that
 * cannot be had - one kept only in iCloud with no connection, or one Photos fails to hand over - is left out and logged,
 * so the ones next to it still arrive.
 */
internal suspend fun List<PHPickerResult>.readPhotos(): List<ImportedFile> {
    val budget = ImportBudget()
    return mapNotNull { it.readPhoto(budget) }
}

/**
 * The file Photos hands over exists only until its completion handler returns, so it is read in there, blocking the
 * queue Photos calls it on. Named by the photo's own name where Photos says one, with the extension of the type read,
 * since the import knows a picture alone by its name.
 */
private suspend fun PHPickerResult.readPhoto(budget: ImportBudget): ImportedFile? {
    val provider = itemProvider
    val typeIdentifier = provider.registeredTypeIdentifiers.filterIsInstance<String>().let { identifiers ->
        identifiers.firstOrNull { identifier -> UTType.typeWithIdentifier(identifier)?.let { type -> SHOWN_TYPES.any { type.conformsToType(it) } } == true }
            ?: identifiers.firstOrNull { identifier -> UTType.typeWithIdentifier(identifier)?.conformsToType(UTTypeImage) == true }
    }
    if (typeIdentifier == null) {
        println("A picked photo offers no picture, only ${provider.registeredTypeIdentifiers}.")
        return null
    }
    val name = pictureFileName(provider.suggestedName.orEmpty(), UTType.typeWithIdentifier(typeIdentifier)?.preferredMIMEType)
    return suspendCancellableCoroutine { continuation ->
        provider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, error ->
            val file = url?.readImportedFile(budget, name = name)
            if (file == null) println("Could not load a picked photo as $typeIdentifier: ${error?.localizedDescription}")
            continuation.resume(file)
        }
    }
}

/** The kinds of picture a song shows, asked of a photo before any other it offers, so that nothing has to be converted. */
private val SHOWN_TYPES = listOf(UTTypeJPEG, UTTypePNG, UTTypeGIF, UTTypeWebP)

/**
 * Answers its picker's caller at most once, however many of the ways the picker can go away report it. Photos does not
 * take its picker down itself, so a finished pick dismisses it here.
 */
internal class PhotoPickerDelegate(
    private val onFinished: (List<PHPickerResult>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {

    private var isFinished = false

    private fun finish(results: List<PHPickerResult>) {
        if (isFinished) return
        isFinished = true
        onFinished(results)
    }

    /** Answers the caller with nothing picked, unless it was answered already. */
    fun finishWithNothing() = finish(emptyList())

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(didFinishPicking.filterIsInstance<PHPickerResult>())
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) = finishWithNothing()
}
