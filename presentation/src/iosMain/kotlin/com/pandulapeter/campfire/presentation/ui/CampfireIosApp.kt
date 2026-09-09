package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * iOS shell of the shared UI.
 *
 * @param urlOpener Opens the given URL in Safari.
 * @param filePicker The document picker. Handed in for the same reason as [urlOpener]: both are UIKit, which this
 *   module stays free of.
 * @param filesToImport Files opened with Campfire or shared to it, which reach the app module through `onOpenURL`.
 */
@Composable
fun CampfireIosApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (String) -> Unit,
    filePicker: FilePicker,
    filesToImport: Flow<List<ImportedFile>> = emptyFlow()
) = CompositionLocalProvider(
    LocalFilePicker provides filePicker
) {
    CampfireApp(
        viewModel = viewModel,
        urlOpener = urlOpener,
        filesToImport = filesToImport
    )
}
