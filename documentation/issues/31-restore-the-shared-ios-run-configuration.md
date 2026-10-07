# Restore the shared iOS run configuration that an unrelated commit deleted, or stop documenting it

**Challenged:** sound

**Kind:** docs (and a project file)  ·  **Severity:** low  ·  **Platforms:** iOS (development only)
**Files:** `.run/iOS.run.xml` (option A), or `CLAUDE.md` (root, Build section), `app/ios/CLAUDE.md`, `.gitignore`
(option B)

**Decision D-31 (answered 2026-10-07): A — restore the file.** Option B is kept below for the record only.

## Problem

`9d2f40947 Improve multi-select UX.` deleted `.run/iOS.run.xml` alongside its multi-select changes; nothing in that
commit's message or its plan (`documentation/plans/multi-select.md`) mentions it, so it was most likely the IDE
removing a configuration of a kind it could not load, swept into the commit. At 1b26dfb94 `.run/` holds only
`Android.run.xml`, `Desktop.run.xml` and `Web.run.xml`, while everything else still describes four:

- Root `CLAUDE.md`, Build: "**`.run/` holds the four shared run configurations** — Android, Desktop, Web and iOS — …
  iOS is the Kotlin Multiplatform plugin's own kind, which an IDE without it (any on Windows or Linux) lists as one it
  cannot run and otherwise leaves alone; it finds the Xcode project through `.idea/xcode.xml` …"
- `app/ios/CLAUDE.md`: "The shared "iOS" run configuration (`.run/iOS.run.xml`) runs the app from Android Studio or
  IntelliJ." and, further down, "the Release configuration the shared iOS run configuration uses".
- `.gitignore`: "# Where the Xcode project is, which the shared iOS run configuration in .run/ needs …" (above
  `!/.idea/xcode.xml`) and "# The schemes the IDE makes out of the shared iOS run configuration in .run/, again on
  every Mac. …" (above `/app/ios/iosApp/iosApp.xcodeproj/xcshareddata/`).

`.idea/xcode.xml`, which exists only for that configuration, is still tracked.

The deleted file (`git show 9d2f40947^:.run/iOS.run.xml`):

```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="iOS" type="AppleRunConfiguration" factoryName="Application" REDIRECT_INPUT="false" ELEVATE="false" USE_EXTERNAL_CONSOLE="false" EMULATE_TERMINAL="false" PASS_PARENT_ENVS_2="true" PROJECT_NAME="iosApp" TARGET_NAME="iosApp" CONFIG_NAME="Release" IS_LOCATION_SIMULATION_SUPPORTED="true" SCHEME_NAME="iOS" IS_LOCATION_SIMULATION_ALLOWED="true" LOCATION_SCENARIO_ID="com.apple.dt.IDEFoundation.CurrentLocationScenarioIdentifier" LOCATION_SCENARIO_TYPE="1" APPLICATION_LANGUAGE="IDELaunchSchemeLanguageUseSystemLanguage" APPLICATION_REGION="" DEVELOPMENT_TEAM="" RUN_TARGET_PROJECT_NAME="iosApp" RUN_TARGET_NAME="iosApp" MAKE_ACTIVE="TRUE" SHOULD_DEBUG_EXTENSIONS="false">
    <embedded_app_extension_list />
    <method v="2">
      <option name="com.jetbrains.cidr.execution.CidrBuildBeforeRunTaskProvider$BuildBeforeRunTask" enabled="true" />
    </method>
  </configuration>
</component>
```

## Fix

- **A. Restore the file** (recommended): `git checkout 9d2f40947^ -- .run/iOS.run.xml`, byte for byte, with no
  license header (the root `CLAUDE.md` says the IDE writes these files and a header "would be gone on the next
  save"). Keep `CONFIG_NAME="Release"`: `app/ios/CLAUDE.md` documents it ("the Release configuration the shared iOS
  run configuration uses", the reason the target excludes `x86_64` from simulator builds), and nothing in the docs
  asks for Debug. No doc changes. When committing, check the diff of `.run/` so the IDE does not drop it again in the
  same commit.
- **B. Accept its removal**: delete the "`.run/` holds the four…" bullet's iOS half from the root `CLAUDE.md` (three
  configurations, Android, Desktop and Web), the paragraph about the shared "iOS" run configuration and the "the
  Release configuration the shared iOS run configuration uses" clause from `app/ios/CLAUDE.md`, both `.gitignore`
  comments and their rules (`!/.idea/xcode.xml` would then serve nothing, so `.idea/xcode.xml` is untracked too; the
  `xcshareddata/` ignore can stay with a comment that no longer names the run configuration, since the IDE's
  Kotlin Multiplatform plugin makes schemes there regardless).

A is recommended: it is one file, everything documented still holds, and running iOS from the IDE keeps working on a
fresh clone.

## Tests

None: an IDE configuration, no code.

## Manual check

On a Mac with Android Studio or IntelliJ and the Kotlin Multiplatform plugin, open the project from a fresh clone: an
"iOS" run configuration is listed and runs the app on a simulator. On Windows or Linux the IDE lists it as one it
cannot run and leaves `git status` clean after a save.
