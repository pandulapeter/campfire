# Cover the clamp of a stored background warmth with the preferences mapper tests

**Challenged:** sound

**Kind:** test  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`

## Problem

The document → model mapping holds a stored warmth within the slider's range —
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt:34`:

```kotlin
    backgroundWarmth = backgroundWarmth.coerceIn(0, UserPreferences.MAX_BACKGROUND_WARMTH),
```

(`UserPreferences.MAX_BACKGROUND_WARMTH = 4`, `data/model/.../UserPreferences.kt:157`) — but nothing tests it, while
every neighboring clamp of the same mapper is tested in `UserPreferencesMappersTest.kt`, e.g. lines 129-135:

```kotlin
    @Test
    fun aStoredFontScaleOutsideTheRangeIsClampedToIt() {
        assertEquals(UserPreferences.MAX_FONT_SCALE, UserPreferencesDocument(fontScale = 40f).toModel().fontScale)
        assertEquals(UserPreferences.MIN_FONT_SCALE, UserPreferencesDocument(fontScale = 0f).toModel().fontScale)
        …
    }
```

Low stakes: `withBackgroundWarmth` clamps its `level` again in `:presentation`, so an out-of-range value cannot draw a
wrong palette today — but the Settings slider reads the model value, and the mapper's clamp is the one that keeps it on
a stop. The existing test class is the natural home, so this is one small test method, not a new class.

## Fix

Add, next to `aStoredFontScaleOutsideTheRangeIsClampedToIt` in `UserPreferencesMappersTest.kt`:

```kotlin
    @Test
    fun aStoredBackgroundWarmthOutsideTheRangeIsClampedToIt() {
        assertEquals(UserPreferences.MAX_BACKGROUND_WARMTH, UserPreferencesDocument(backgroundWarmth = 40).toModel().backgroundWarmth)
        assertEquals(0, UserPreferencesDocument(backgroundWarmth = -1).toModel().backgroundWarmth)
        assertEquals(0, UserPreferencesDocumentFormat.decode("{}").document.toModel().backgroundWarmth)
        val saved = UserPreferencesDocumentFormat.encode(UserPreferencesDocument().toModel().copy(backgroundWarmth = 3).toDocument())
        assertEquals(3, UserPreferencesDocumentFormat.decode(saved).document.toModel().backgroundWarmth)
    }
```

The last two lines also cover that a document from before the slider reads as no warmth and that a chosen step survives
saving and reloading. No production change.

## Tests

The test above. Verified with an untracked probe of exactly these assertions at 1b26dfb94 (then deleted): it passes with
`./gradlew :data:source:local:implementation:desktopTest`.

## Manual check

None.
