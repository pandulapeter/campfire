# 13 — A stored text size outside the app's range is used as it is

**Severity:** unreadable or broken song screen until the preferences file is fixed by hand (all platforms, needs a
hand-edited or foreign `preferences.json`) · **Area:** `:data:source:local:implementation`
(`mapper/UserPreferencesMappers.kt`), `:data:model` (`UserPreferences.kt`), `:presentation` (`CampfireViewModel.kt`,
constants only)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it.

Reviewer finding 2-local#5.

## What the user sees

`preferences.json` holds `"fontScale": 40` (or `0`, or `-1`) — a hand edit on the desktop, where the file sits next to
the library, or a document from a future version with a wider range. Every song opens at 4000 % text size (a column
per word, the chords off screen) or at nothing at all; the stepper's `+`/`−` do not bring it back into range in one
step (the first tap snaps from the stored value), and the pinch or Ctrl-scroll limits are only applied to the *next*
value set.

## Cause

The document is decoded per field (`UserPreferencesDocumentFormat`), so a number of the right type always gets
through, and the mapper passes it on unchecked
(`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt:21`):

```kotlin
    fontScale = fontScale,
```

The only clamp is in the setter
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1800`):

```kotlin
    fun setFontScale(value: Float) = pendingFontScale.update { value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }
```

while the read path (`:688-690`) hands the stored value straight to the screen:

```kotlin
    val fontScale = combine(userPreferences, pendingFontScale) { userPreferences, pendingFontScale ->
        pendingFontScale ?: userPreferences?.fontScale ?: DEFAULT_FONT_SCALE
    }.asState(DEFAULT_FONT_SCALE)
```

where it multiplies the column width and every `sp` size of the song screen. The limits live in the view model's
companion (`:2298-2300`: `DEFAULT_FONT_SCALE = 1f`, `MIN_FONT_SCALE = 0.5f`, `MAX_FONT_SCALE = 2.5f`), out of reach of
the mapper.

Every other preference with a closed set of values already falls back at the mapper (`entries.firstOrNull { … } ?:
default`, `:22-33`); the font scale is the one open-ended number that does not.

## The change

Invoke the **`code-style`** skill before the first edit.

1. **`:data:model` owns the range.** In `UserPreferences.kt`, next to the `fontScale` property (`:28`), add a companion
   (the class has none at the top level yet — the existing `companion object` at `:114` belongs to a nested enum):

   ```kotlin
       companion object {
           /** The text size a song opens at, and the one a stored size that is not a size falls back on. */
           const val DEFAULT_FONT_SCALE = 1f
           const val MIN_FONT_SCALE = 0.5f
           const val MAX_FONT_SCALE = 2.5f
       }
   ```

   and turn the trailing `// Multiplier applied …` comment on `fontScale` into KDoc that names the range.
2. **The mapper clamps** (`UserPreferencesMappers.kt:21`):

   ```kotlin
       // A hand edit or a newer version's wider range must not reach the screen as it is: a size of 40 is a column per
       // word. Not a number at all is no size, and is the default.
       fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE)
           ?: UserPreferences.DEFAULT_FONT_SCALE,
   ```

   (Strict JSON has no `NaN`/`Infinity`, but `1e39` decodes to `Float.POSITIVE_INFINITY`; `isFinite` covers both.)
   `toDocument` is unchanged: a clamped value is written back on the next save, which repairs the file.
3. **`CampfireViewModel.kt:2298-2300`** — the three constants become aliases so that `SongDisplayControls.kt:193,197`
   and every other reader keep compiling unchanged:

   ```kotlin
           const val DEFAULT_FONT_SCALE = UserPreferences.DEFAULT_FONT_SCALE
           const val MIN_FONT_SCALE = UserPreferences.MIN_FONT_SCALE
           const val MAX_FONT_SCALE = UserPreferences.MAX_FONT_SCALE
   ```

   (`UserPreferences` is already imported there.) Keeping the names in the view model is deliberate: it avoids touching
   the song screen files that lane D's plans are editing.

## Tests

`data/source/local/implementation/src/commonTest/.../mapper/UserPreferencesMappersTest.kt`:

1. `aStoredFontScaleOutsideTheRangeIsClampedToIt`: `UserPreferencesDocument(fontScale = 40f).toModel().fontScale ==
   UserPreferences.MAX_FONT_SCALE`, `0f` and `-1f` → `MIN_FONT_SCALE`, `1.3f` → `1.3f`.
2. `aStoredFontScaleThatIsNotANumberFallsBackToTheDefault`: `Float.NaN` and `Float.POSITIVE_INFINITY` →
   `DEFAULT_FONT_SCALE`.

`desktopTest/.../source/UserPreferencesLocalSourceTest.kt`: add a case beside the existing `{"fontScale": 1.5}` one —
`{"fontScale": 40}` loads as `2.5f`.

## Verification

1. Tests above; root unit test command; compile `:presentation` for one target
   (`./gradlew :presentation:compileKotlinDesktop`) since its constants changed.
2. Desktop: quit, set `"fontScale": 40` in `~/Library/Application Support/Campfire/preferences/preferences.json`
   (Linux `~/.local/share/campfire/…`, Windows `%APPDATA%\Campfire\…`), start, open a song. **Before:** giant text.
   **After:** 250 %, the stepper's `+` disabled, `−` goes to 240 %. Change the size once and check the file now holds
   the new value.

## Docs

- `data/source/local/implementation/CLAUDE.md` — where the preferences mapper / document are described (search for
  `UserPreferencesDocumentFormat`), add: "The text size is clamped to `UserPreferences`' range on the way in, so a hand
  edit or a newer version's value never reaches the song screen as it is."
- `data/model/CLAUDE.md` — if `UserPreferences.kt` has a bullet, add that it owns the text size range.

## Files touched

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/UserPreferences.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/UserPreferencesLocalSourceTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (companion only)
- `data/source/local/implementation/CLAUDE.md`, `data/model/CLAUDE.md`

## Depends on

Nothing. **Cross-lane:** touches `CampfireViewModel.kt`, which many lane D plans (29–34, 38, 39, 44) and plan 17's
optional alternative also edit; the change is three lines in the companion, so rebase onto whatever lands first.
