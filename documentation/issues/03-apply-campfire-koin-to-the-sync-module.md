# Apply the campfire-koin convention plugin to :data:sync:implementation

**Kind:** build  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/sync/implementation/build.gradle.kts`

## Problem

d9a03d9ff created `:data:sync:implementation` just before 175796ec2 moved every module that declares Koin definitions
to the `campfire-koin` convention plugin, so this module was missed:

```kotlin
plugins {
    id("campfire-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}
...
            implementation(libs.koin.annotations)
            implementation(libs.koin.core)
```

Every other such module (for example `data/repository/implementation/build.gradle.kts`) has `id("campfire-koin")` and
no hand-added Koin dependencies. As a result, the plugin's settings (such as `aiAssist` off) do not apply here, and the
root CLAUDE.md line "applied through `campfire-koin` by every module that declares a definition" is untrue for this
module.

## Fix

Replace `alias(libs.plugins.koin.compiler)` with `id("campfire-koin")`, and drop `implementation(libs.koin.annotations)`
and `implementation(libs.koin.core)`, keeping the order the sibling modules use. First check that
`gradle/build-logic`'s `campfire-koin` adds both dependencies to `commonMain`. If it does not add one of them, keep that
line.

## Tests

`./gradlew :data:sync:implementation:desktopTest :app:desktop:compileKotlin` (the `:app:di` graph check) and
`:app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Manual check

None.
