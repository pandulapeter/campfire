# :app:ios

iOS entry point. A Kotlin/Native module that produces the static `ComposeApp` framework consumed by the Xcode project in `iosApp/`.

- `CampfireViewController()` (in `src/iosMain`) starts Koin once (`dataLocalSourceModule + dataRepositoryModule + domainModule + presentationModule`) and returns a `ComposeUIViewController` hosting `CampfireIosApp`. Links open through `UIApplication.openURL`. Add new Koin modules here.
- `IosFilePicker.kt` — the `FilePicker` actual: `UIDocumentPickerViewController` for importing, `UIActivityViewController` for saving and sharing.
- `IosFileImport.kt` — a top-level `importFile(url)` that Swift calls from `.onOpenURL`, handing the file to the same flow the picker feeds.
- `iosApp/iosApp.xcodeproj` — SwiftUI wrapper (`ContentView.swift` embeds the view controller with `.ignoresSafeArea()`, the Compose scaffold applies insets itself). A "Compile Kotlin Framework" build phase runs `./gradlew :app:ios:embedAndSignAppleFrameworkForXcode` from the repo root. Team id, bundle id and app name live in `iosApp/Configuration/Config.xcconfig`; version/build number in `project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`).

`Info.plist` carries the document integration: `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` put the library under "On My iPhone → Campfire" in the Files app (the library is in the documents directory; the preferences are not, so they stay out of the user's way). `UTImportedTypeDeclarations` declares the ChordPro types and `CFBundleDocumentTypes` claims them — `.cho` with `LSHandlerRank` `Default`, the rest of the family `Alternate`, since those extensions are shared with other kinds of file. Zip and plain text are deliberately absent. Keep the list in step with `LibraryFiles.SONG_EXTENSIONS`.

Because the Files app can change the library behind the app's back, `CampfireApp` rescans on resume here (see `libraryLocation` in `:presentation`).

Build and run from Xcode (or `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`); `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` only checks that the Kotlin side compiles and links.
