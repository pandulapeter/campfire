# :app:ios

iOS entry point. A Kotlin/Native module that produces the static `ComposeApp` framework consumed by the Xcode project in `iosApp/`.

- `CampfireViewController()` (in `src/iosMain`) starts Koin once (`dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule + domainModule + presentationModule`) and returns a `ComposeUIViewController` hosting `CampfireIosApp`. Links open through `UIApplication.openURL`. Add new Koin modules here.
- `iosApp/iosApp.xcodeproj` — SwiftUI wrapper (`ContentView.swift` embeds the view controller with `.ignoresSafeArea()`, the Compose scaffold applies insets itself). A "Compile Kotlin Framework" build phase runs `./gradlew :app:ios:embedAndSignAppleFrameworkForXcode` from the repo root. Team id, bundle id and app name live in `iosApp/Configuration/Config.xcconfig`; version/build number in `project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`).

Build and run from Xcode (or `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator`); `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` only checks that the Kotlin side compiles and links.
