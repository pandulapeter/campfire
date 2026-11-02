# Fail a test when a public :chordpro model class is missing from the Compose stability configuration

**Kind:** testing / performance · **Severity:** low · **Platforms:** all · **Priority:** P3 · **Effort:** S
**Files:** `gradle/compose-stability.conf`, `gradle/libs.versions.toml` (`kotlin-reflect`), `chordpro/build.gradle.kts`
(its `desktopTest` dependency), new
`chordpro/src/desktopTest/kotlin/com/pandulapeter/campfire/chordpro/model/ComposeStabilityListTest.kt`,
`chordpro/CLAUDE.md` (the "model is immutable" bullet), `gradle/build-logic/CLAUDE.md` (one clause)

**Challenged:** amended — option (a) decided; the list misses eleven classes, not nine (`ChordProBlock.Break` and
`GridToken.Beat` are `data object`s the test keeps, as it keeps the listed `SectionType.Verse`); the test declares the
list file as an input, or an edit of the list alone leaves `desktopTest` up to date and unchecked.

## Problem

`:chordpro` is not built with the Compose compiler, so its classes carry no stability information; the
`campfire-compose-library` plugin passes `gradle/compose-stability.conf`, which names ~35 classes by hand, on purpose
never by wildcard ("so that a class added to it later is not vouched for unread"; `gradle/build-logic/CLAUDE.md`:
"Name each class individually, never a wildcard"). A class missing from it compiles fine and silently makes every
composable taking it skip nothing: a new parse recomposes every line. Nothing notices.

At HEAD the list already misses public model classes: `ChordProLink`, `ChordProLine.Verbatim`,
`ChordProLine.Lyrics.Formatting`, `TabNotice`, `TabRowLine`, `TabTuning`, `TabPlayer` (all in
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/`), and `ScaleGroup`, `ScaleException` in
`scales/` beside the listed `Scale` and `ScaleChord`; and the `data object`s `ChordProBlock.Break` and
`GridToken.Beat` (the stateless `SectionType.Verse`, `ChordProLine.Blank`… are listed). A listed name that a rename left behind would also go unnoticed.

## Fix

**A test over the list** (decided). `ComposeStabilityListTest` in `:chordpro`'s `desktopTest` (JVM, so Java
  reflection and the classpath directory are at hand; the working directory is the module, as `PdfRoundTripTest`'s
  `File("src/…")` relies on):
  1. Read `../gradle/compose-stability.conf`, dropping `//` comments and blanks.
  2. List the compiled classes of package `com.pandulapeter.campfire.chordpro.model` from the class loader's directory
     for that package and for `com.pandulapeter.campfire.chordpro.scales`, and keep the public types (Kotlin
     visibility, not bytecode's: `scales` holds many `internal` classes, which the JVM sees as public, so the test
     reads `KClass.visibility` through `kotlin-reflect`, added as a `desktopTest` dependency only, with a catalog entry
     on the Kotlin version; nothing ships). Skip enums (the Compose compiler treats enum classes as stable), companions,
     file facades (`…Kt`), synthetic classes, and plain `object`s (`Scales`, `ScaleChords`… hold no state and are never
     a parameter); keep `data object`s (`SectionType.Verse`, listed today; `KClass.isData`). At HEAD this yields the
     listed classes plus the missing ones below.
  3. Assert both directions, with `$` read as `.`: every listed class exists (a rename is caught), and every scanned
     class is listed, the message naming the missing ones and saying "add it to gradle/compose-stability.conf once it is
     immutable (chordpro/CLAUDE.md)".
  Then add the eleven missing classes above to the list, after checking each is immutable (vals only, no collection
  mutated after construction; `TabPlayer` holds lambdas and compares by identity, which is still stable).
  This judges only the change that adds a class: no baseline, no exception list.
  4. In `chordpro/build.gradle.kts`, `tasks.named<Test>("desktopTest") { inputs.file(rootProject.file("gradle/compose-stability.conf")).withPropertyName("composeStability") }`,
  so the pre-commit hook's `desktopTest` reruns the test when only the list changed (configuration cache: a file only).

Rejected:

- **(b) A wildcard** (`com.pandulapeter.campfire.chordpro.model.**`): smallest, but reverses a rule written twice.
- **(c) `@Immutable`** from `androidx.compose.runtime:runtime-annotation`, which publishes every target the build uses
  (android, jvm, iosArm64, iosSimulatorArm64, wasm-js are in the Gradle cache): makes `:chordpro` depend on a Compose
  artifact against "dependency-free", must be remembered on each new class all the same, and needs crediting.


## Tests

The test above; check it fails by temporarily removing one line from the list (not committed).
