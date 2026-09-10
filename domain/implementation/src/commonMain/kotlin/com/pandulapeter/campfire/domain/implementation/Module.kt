package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
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
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.domain.implementation.useCases.CancelSynchronizationUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ConnectSyncProviderUseCaseImpl
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
import com.pandulapeter.campfire.domain.implementation.useCases.NormalizeTextUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.ParseChordProUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.RestoreSyncUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveSetlistUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveSongContentUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SaveUserPreferencesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.SynchronizeLibraryUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.TransposeChordProTextUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.useCases.TransposeChordProUseCaseImpl
import org.koin.dsl.module

val domainModule = module {
    factory<CancelSynchronizationUseCase> { CancelSynchronizationUseCaseImpl(get()) }
    factory<ConnectSyncProviderUseCase> { ConnectSyncProviderUseCaseImpl(get(), get()) }
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
    factory<ImportFilesUseCase> { ImportFilesUseCaseImpl(get(), get(), get()) }
    factory<LoadScreenDataUseCase> { LoadScreenDataUseCaseImpl(get(), get(), get()) }
    factory<NormalizeTextUseCase> { NormalizeTextUseCaseImpl() }
    factory<ParseChordProUseCase> { ParseChordProUseCaseImpl() }
    factory<RestoreSyncUseCase> { RestoreSyncUseCaseImpl(get(), get()) }
    factory<SaveSetlistUseCase> { SaveSetlistUseCaseImpl(get()) }
    factory<SaveSongContentUseCase> { SaveSongContentUseCaseImpl(get()) }
    factory<SaveUserPreferencesUseCase> { SaveUserPreferencesUseCaseImpl(get()) }
    factory<SynchronizeLibraryUseCase> { SynchronizeLibraryUseCaseImpl(get()) }
    factory<TransposeChordProTextUseCase> { TransposeChordProTextUseCaseImpl() }
    factory<TransposeChordProUseCase> { TransposeChordProUseCaseImpl() }
}
