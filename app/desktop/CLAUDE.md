# :app:desktop

Compose Desktop entry point (`CampfireDesktopApplication.kt`, `main(args)`). Starts Koin through the `KoinApplication` composable (`koinConfiguration { }`) with `dataLocalSourceModule + dataRepositoryModule + domainModule + presentationModule`, then hosts `CampfireDesktopApp` in a `Window` whose `onKeyEvent` is wired to `CampfireViewModel.handleKeyEvent` (Escape dismisses the visible modal, pops the back stack, or exits the application on the root screen). Add new Koin modules here.

The view model is obtained outside `Window` so window resizing doesn't reset it. Window min size is 400x400.

`main` takes `args` because that is how "open with" reaches a desktop application: the system launches the app with the file as an argument, and every argument is read and imported at startup. Files dropped onto the window take the same path (`Modifier.dragAndDropTarget` in `CampfireDesktopApp`).

Packaging: `compose.desktop` produces Dmg/Exe/Msi/Deb. `javaHome` is pinned to the toolchain JDK because the Gradle JVM may lack `jpackage`. Icons live in `src/main/resources/appIcon.{icns,ico}` (packaging) and `src/main/composeResources/drawable/app_icon.png` (the window icon, also used for the Linux package). `chordProFileAssociations()` in `build.gradle.kts` registers the six ChordPro extensions as `text/plain` for macOS and Windows — deliberately not zip or `.txt`, and kept in step with `LibraryFiles.SONG_EXTENSIONS`, which a build file cannot see.

The library lives where the platform keeps application data (`~/Library/Application Support/Campfire` on macOS, `%APPDATA%` on Windows, `~/.local/share` elsewhere), which is a folder the user can open — hence the "Location" row in Settings and the rescan when the window regains focus.

`./gradlew :app:desktop:run` to launch (`--args="/path/to/song.cho"` to test opening a file); `:app:desktop:packageDistributionForCurrentOS` to build an installer.
