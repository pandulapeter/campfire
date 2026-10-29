<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/theme

The theme, the typography, and the fonts. `CampfireTheme` (`MaterialExpressiveTheme`) is driven by three independent
preferences: `themeColor` (palette), `backgroundWarmth` (sepia), and `uiMode` (light/dark half).

## Sepia (`BackgroundWarmth.kt`)

- One transform of every palette: neutrals (backgrounds, surfaces, and the text, outlines, and inverse surfaces on them)
  move in CIELAB towards paper yellow at their own L*; the light half is first dimmed by one factor on every role's
  `Y + 0.05` (down to 0.86, since near-white sRGB holds almost no chroma).
- **No warmth step changes any WCAG contrast** of the scheme, second accent included (tested pair by pair). Out-of-gamut
  colors lose chroma, not lightness.
- `mapColors` must name every `ColorScheme` role: a `desktopTest` finds roles by reflection, so a role a Material update
  adds fails the build until handled.

## Theme changes

- A change cross fades every role from what is on screen with one progress value (interruptions continue from what's
  visible), stepped in `THEME_FADE_STEPS` rather than per frame: the scheme and the second accent are static
  composition locals, so every value recomposes the whole app.
- The first change of a launch (correcting the system's guess once preferences arrive) **snaps**: the launch screen
  covers the app, and a fade would recompose everything every frame of startup. Only the launch screen's background
  and mark fade (`LaunchScreenColors`, read while drawing), and those snap too under
  `isStartupScreenHeldUntilAppReady`.
- Schemes are the constants of `ColorSchemes.kt`, so "no change" is an identity check (the warmed pair is remembered to
  keep it so). `CampfireTheme` tells its content whether its scheme is final, read from the schemes rather than the
  animation (not started yet in the frame preferences arrive); this keeps the launch screen up until colors settle.

## Palettes

- `ColorSchemePair` (both halves travel together); `themeColorOptions()` is the one ordered list read by both the
  color choice and `colorSchemePair()`. A color the device can't honor is left out of it.
- `CampfireColorScheme.kt` (default): purple as primary, tinting every neutral, with orange as the tertiary and the
  **second accent**. Material has no role for it, so `ColorSchemePair` carries `lightSecondAccent` / `darkSecondAccent`
  (the primary in every other palette) and `CampfireTheme` provides `LocalSecondAccentColor`, faded on the same
  progress. It draws what is played (chords, a row's key, the read only line's key and capo) and the lists' sticky
  `SectionHeader`s; every other title and every control uses the primary.
- The one gradient in the interface is the app's own disc (and ring) in the color choice; everything else is flat.
- `MaterialColorSchemes.kt`: seeds run through Material's tonal spot mapping and pasted in — a new color is a seed.
- `DynamicColorSchemePair.kt`: Android 12+ dynamic color, null elsewhere (no "System" swatch). From Android 14 those
  read role colors, which Samsung leaves at the framework's blue while recoloring the palette; where the role primary is
  far from the palette's tone 40, the actual maps the palettes itself.

## Interface scale

`ProvideInterfaceScale` scales `LocalDensity` by `interfaceScale` (0.85 where `isDesktopPlatform`, so the web follows
its input rather than its platform; 1 on touch). Not a setting. Window size classes follow it; song text size multiplies
on top; the launch screen is excluded (it hands over from platform startup screens at their size); the desktop window
minimum accounts for it (see `app/desktop`).

## Icons

Directional icons (back, previous/next song, undo/redo) carry `android:autoMirrored`, which Compose honors on every
platform for RTL.

## Fonts

**Bundled on every platform** (`src/commonMain/composeResources/font`, OFL licenses under `files/licenses`): Inter for
the interface, JetBrains Mono (no-ligatures) for tabs and the editor, so a song looks and breaks the same on a phone, a
computer, and paper (PDF and diagram numbers included). Subset to Latin, Greek, and Cyrillic; Inter's ♭ ♮ ♯ and
JetBrains Mono's ♮ are drawn into them by `tools/fonts/accidentals.py` (upstream has none), rerun on any new subset.
Others fall back to the script faces below and system fonts (Skia's Roboto on the web, which lacks most symbols), so a
string uses no character the bundled files lack (⌘ is written "Cmd").

- `InterfaceTypography.kt`: Material's type scale in Inter Regular/Medium/Bold, read before the first frame except on
  the web, whose actual returns null until all three weights are fetched; the theme isn't settled until then (no second
  layout of every line), and after five seconds falls back to Material's, as a failed fetch is never reported.
- `MonospaceFontFamily.kt`: `LocalMonospaceFontFamily`, provided by the theme so the web fetches it at launch.
- `app/web`'s `index.html` preloads them **by their path in the distribution**: renaming one means renaming it there.

### Script faces

- `ScriptFaces.kt` is the table of faces for scripts Inter lacks, a row each (Noto Sans Hebrew first): its ranges, the
  languages written in it, its file. `tools/fonts/script_faces.py` subsets an upstream file to the ranges its row names,
  so another script is a row, a run, and its license credited. No code asks for a script by name.
- `ProvideScriptFaces` registers them as **fallbacks** of the resolver's Skia font collection. Skia asks the platform's
  fonts first, so the bundled face is what the web and a Linux desktop without one draw; it takes a fallback by
  character alone, the first registered face that has it whatever the weight, so only Regular is bundled (Hebrew titles
  are not bolder on the web) and no two rows may share a character (tested). Android does nothing: its fallback is the
  system's, whose Hebrew is Noto Sans Hebrew. JetBrains Mono falls back the same way, so the editor's Hebrew is
  proportional: the fallback cannot tell monospace text from the rest.
- **The web fetches a face only when needed** (`NeededScriptFaces.kt`: the interface's language, the library's names,
  tags, declared languages, and opened songs' text), keeps it for the session, and registers it with a new resolver,
  which lays every text out again once (Compose keeps measured text). A face needed at launch holds the launch screen,
  five seconds at most. It is not preloaded by `index.html`; the offline copy still keeps it with the build.

## The app icon and the app's own colors

### The app icon follows the theme color

- Wherever the platform allows: Android launcher (`activity-alias` per color, see `app/android`), iOS alternate icon,
  web favicon, and a running Linux app's window icon. Not Windows (the Store package draws the taskbar) or the Mac Dock
  (a runtime icon is flat, without the system's clear, tinted, or dark styles).
- `UserPreferences.isAppIconThemed` turns it off.
- Every packaged icon is the diagonal gradient of the app's own colors, the promotional purple and orange.
- Android's launcher and Play icons are drawn as they are; every other icon is the hand-drawn orange one in `app/icons`,
  recolored by `app/generate_theme_icons.py` (outputs committed).
