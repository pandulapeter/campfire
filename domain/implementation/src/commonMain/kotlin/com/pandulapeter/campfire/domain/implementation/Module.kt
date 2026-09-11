/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeLanguageCodeUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.domain.implementation.useCases.CancelSynchronizationUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ConnectSyncProviderUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ConvertChordProNotationUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.CreateSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.CreateSongUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.DeleteSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.DeleteSongUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.DisconnectSyncProviderUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ExportLibraryUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ExportSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ExportSongsUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetSongContentUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetSyncProvidersUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetSyncStateUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.GetUserPreferencesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ImportFilesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.LoadScreenDataUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.NormalizeLanguageCodeUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.NormalizeTextUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ParseChordProUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.PrepareImportUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.RestoreSyncUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.RenameSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.RenameSongFileUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveSongContentUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveUserPreferencesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SetChordProLanguagesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SetChordProTagUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SynchronizeLibraryUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.TransposeChordProTextUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.TransposeChordProUseCaseImpl
import org.koin.dsl.module

val domainModule = module {
    factory<CancelSynchronizationUseCase> { CancelSynchronizationUseCaseImpl(get()) }
    factory<ConnectSyncProviderUseCase> { ConnectSyncProviderUseCaseImpl(get(), get()) }
    factory<ConvertChordProNotationUseCase> { ConvertChordProNotationUseCaseImpl() }
    factory<CreateSetlistUseCase> { CreateSetlistUseCaseImpl(get()) }
    factory<CreateSongUseCase> { CreateSongUseCaseImpl(get()) }
    factory<DeleteSetlistUseCase> { DeleteSetlistUseCaseImpl(get()) }
    factory<DeleteSongUseCase> { DeleteSongUseCaseImpl(get(), get(), get()) }
    factory<DisconnectSyncProviderUseCase> { DisconnectSyncProviderUseCaseImpl(get()) }
    factory<ExportLibraryUseCase> { ExportLibraryUseCaseImpl(get(), get(), get(), get()) }
    factory<ExportSetlistUseCase> { ExportSetlistUseCaseImpl(get(), get(), get()) }
    factory<ExportSongsUseCase> { ExportSongsUseCaseImpl(get(), get()) }
    factory<GetScreenDataUseCase> { GetScreenDataUseCaseImpl(get(), get(), get(), get()) }
    factory<GetSongContentUseCase> { GetSongContentUseCaseImpl(get()) }
    factory<GetSyncProvidersUseCase> { GetSyncProvidersUseCaseImpl(get()) }
    factory<GetSyncStateUseCase> { GetSyncStateUseCaseImpl(get()) }
    factory<GetUserPreferencesUseCase> { GetUserPreferencesUseCaseImpl(get()) }
    factory<ImportFilesUseCase> { ImportFilesUseCaseImpl(get(), get()) }
    factory<LoadScreenDataUseCase> { LoadScreenDataUseCaseImpl(get(), get(), get()) }
    factory<NormalizeLanguageCodeUseCase> { NormalizeLanguageCodeUseCaseImpl() }
    factory<NormalizeTextUseCase> { NormalizeTextUseCaseImpl() }
    factory<ParseChordProUseCase> { ParseChordProUseCaseImpl() }
    factory<PrepareImportUseCase> { PrepareImportUseCaseImpl(get(), get(), get(), get()) }
    factory<RestoreSyncUseCase> { RestoreSyncUseCaseImpl(get(), get()) }
    factory<RenameSetlistUseCase> { RenameSetlistUseCaseImpl(get()) }
    factory<RenameSongFileUseCase> { RenameSongFileUseCaseImpl(get(), get(), get()) }
    factory<SaveSetlistUseCase> { SaveSetlistUseCaseImpl(get()) }
    factory<SaveSongContentUseCase> { SaveSongContentUseCaseImpl(get()) }
    factory<SaveUserPreferencesUseCase> { SaveUserPreferencesUseCaseImpl(get()) }
    factory<SetChordProLanguagesUseCase> { SetChordProLanguagesUseCaseImpl() }
    factory<SetChordProTagUseCase> { SetChordProTagUseCaseImpl() }
    factory<SynchronizeLibraryUseCase> { SynchronizeLibraryUseCaseImpl(get()) }
    factory<TransposeChordProTextUseCase> { TransposeChordProTextUseCaseImpl() }
    factory<TransposeChordProUseCase> { TransposeChordProUseCaseImpl() }
}
