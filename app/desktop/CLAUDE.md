# :app:desktop

Compose Desktop entry point (`CampfireDesktopApplication.kt`, `main()`). Starts Koin with `dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule` + `domainModule` + `presentationModule`, then hosts `CampfireDesktopApp` in a `Window`. Add new Koin modules here.

The state holder is created outside `Window` so window resizing doesn't reset it. Window min size is 400x400.

Packaging: `compose.desktop` produces Dmg/Exe/Msi/Deb. `javaHome` is pinned to the toolchain JDK because the Gradle JVM may lack `jpackage`. Icons live in `src/main/resources/appIcon.{icns,ico,png}`.

`./gradlew :app:desktop:run` to launch; `:app:desktop:packageDistributionForCurrentOS` to build an installer.
