# Move AuthorizationCompletionPage and SystemBrowser to :data:model and drop the api(":data:source:remote:api") lines of :domain:api and :data:repository:api

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `domain/api/build.gradle.kts`, `data/repository/api/build.gradle.kts` (the `api(project(":data:source:remote:api"))` lines and their comments); `data/source/remote/api/src/commonMain/.../remote/api/model/AuthorizationCompletionPage.kt` and `.../remote/api/SystemBrowser.kt` (move); new `data/model/src/commonMain/.../data/model/domain/AuthorizationCompletionPage.kt` and `.../data/model/domain/SystemBrowser.kt`; importers: `data/repository/api/.../SyncRepository.kt`, `data/repository/implementation/.../SyncRepositoryImpl.kt`, `domain/api/.../useCases/ConnectSyncProviderUseCase.kt`, `domain/implementation/.../useCases/SyncUseCaseImpls.kt`, `domain/implementation/src/commonTest/.../DeleteLibraryUseCaseImplTest.kt`, `data/repository/implementation/src/commonTest/.../sync/FakeSyncCollaborators.kt`, `.../SyncRepositoryImplTest.kt`, `data/source/remote/api/.../SyncAuthenticator.kt`, `data/source/remote/implementation/src/*/auth/SyncAuthenticator.*.kt` (all four platforms), `data/source/remote/implementation/src/desktopTest/.../DesktopSyncAuthenticatorTest.kt`, `presentation/src/commonMain/.../ui/CampfireViewModel.kt`, `presentation/src/commonMain/.../ui/screens/settings/SyncSettings.kt`, `presentation/src/desktopMain/.../ui/platform/SystemBrowser.desktop.kt`, `tools/screenshots/src/main/kotlin/.../screenshots/Fakes.kt`; CLAUDE.md files of `data/model`, `data/source/remote/api`, `data/repository/api`, `domain/api`, `presentation` wherever they say where these two types live or why the `api()` dependency exists
**Depends on:** none

## Problem

Both API modules re-export the whole remote-source API to everything above them:

```kotlin
// domain/api/build.gradle.kts
// Connecting carries the words the desktop's redirect page shows, see AuthorizationCompletionPage.
api(project(":data:source:remote:api"))
```

```kotlin
// data/repository/api/build.gradle.kts
// The connect call carries the words the desktop's redirect page shows, see AuthorizationCompletionPage.
api(project(":data:source:remote:api"))
```

So `:presentation` (which depends only on `:domain:api`) and `:domain:implementation` can see `SyncProvider`,
`SyncAuthenticator`, `PendingAuthorizationStore`, `Sha256`, `localContentHash`, `CoverArtRemoteSource`, the remote models
and the three sync exceptions. The root CLAUDE.md's rule ("everything else depends on api modules and gets wiring via
Koin"; the data sources are below the repositories) is enforced for the local source but not for the remote one, and
nothing stops a screen from importing `localContentHash` tomorrow.

What actually crosses the boundary at 2940b0e0a (grep of `data.source.remote` outside `:data:*`):
- `AuthorizationCompletionPage(title, message)` — a two-string model: the ViewModel builds it from string resources
  (`CampfireViewModel.kt:52`, `SyncSettings.kt:88`), `ConnectSyncProviderUseCase` and `SyncRepository.connect` carry it.
- `SystemBrowser` — `fun interface SystemBrowser { fun open(url: String): Boolean }`, implemented by
  `DesktopSystemBrowser` in `:presentation`'s `desktopMain` (a `@Single` found by `PresentationModule`'s scan) and
  consumed by the desktop `SyncAuthenticator` in `:data:source:remote:implementation`.
- `tools/screenshots`' fake `SyncRepository` (imports `AuthorizationCompletionPage`).

Nothing else.

## Fix

1. Move `AuthorizationCompletionPage` to `:data:model` as
   `com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage` (KDoc unchanged). Update every importer
   listed above. `:data:source:remote:api` already `api()`s `:data:model`, so the authenticators keep seeing it.
2. Move `SystemBrowser` to `:data:model` as `com.pandulapeter.campfire.data.model.domain.SystemBrowser`. It is a
   one-method port with no dependencies, which `:data:model` (pure Kotlin, no dependencies, seen by everything) can hold
   without growing any. Update `SyncAuthenticator.desktop.kt`, `DesktopSyncAuthenticatorTest`, and
   `SystemBrowser.desktop.kt`. Koin still binds `DesktopSystemBrowser` to `SystemBrowser` (the type moved, the
   binding did not); the compile-time graph check in `:app:di` confirms it.
   - Alternative considered: leave `SystemBrowser` where it is and give `:presentation`'s `desktopMain` an explicit
     `implementation(project(":data:source:remote:api"))`. Narrower in what moves, but it puts a data-source dependency
     into the UI module, which is the kind of edge this plan removes. Not recommended.
3. Delete the two `api(project(":data:source:remote:api"))` lines and their comments. Build every target
   (`./gradlew :presentation:compileKotlinDesktop :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:compileKotlinWasmJs :tools:screenshots:compileKotlin` — check the exact task names with `./gradlew :app:web:tasks` if one differs, plus every module's
   `desktopTest`) — any remaining leak is now a compile error naming its file. Expected leftovers: none in main code;
   `:data:repository:implementation` keeps its own `implementation(project(":data:source:remote:api"))`.
4. Update the CLAUDE.md sentences (the `:data:model` list gains the two types; `domain/api` and `data/repository/api`
   lose the explanation of the `api()` line).

Steps 1+2 can land as one commit, step 3 as the next (so a failure in 3 points at a missed importer).

## Tests

None new (two types moved). Guards: every module's `desktopTest` (in particular `DesktopSyncAuthenticatorTest`,
`SyncRepositoryImplTest`, `DeleteLibraryUseCaseImplTest`) and the compile of all four app targets and
`tools/screenshots`.

## Manual check

Desktop: Settings → Connect Dropbox opens the system browser, and the page the browser lands on after consent shows the
localized "you can close this tab" title and message (switch the app to Hungarian once to see the strings follow).
