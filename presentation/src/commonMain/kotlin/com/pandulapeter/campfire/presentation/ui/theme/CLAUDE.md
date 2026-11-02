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

Three independent preferences: `themeColor` (palette), `backgroundWarmth` (sepia), and `uiMode` (light/dark half).

## Sepia (`BackgroundWarmth.kt`)

- One transform of every palette: neutrals (backgrounds, surfaces, and the text, outlines, and inverse surfaces on them)
  move in CIELAB towards paper yellow at their own L*; the light half is first dimmed by one factor on every role's `Y +
  0.05` (down to 0.86, since near-white sRGB holds almost no chroma).
- **No warmth step changes any WCAG contrast** of the scheme, second accent included (tested pair by pair). Out-of-gamut
  colors lose chroma, not lightness.
- `mapColors` must name every `ColorScheme` role: a `desktopTest` finds roles by reflection, so a role a Material update
  adds fails the build until handled.

## Theme changes

- A change cross fades every role from what is on screen with one progress value (interruptions continue from what's
  visible), stepped in `THEME_FADE_STEPS` rather than per frame: the scheme and the second accent are static composition
  locals, so every value recomposes the whole app.
- The first change of a launch (correcting the system's guess once preferences arrive) **snaps**: the launch screen
  covers the app, and a fade would recompose everything every frame of startup. Only the launch screen's background and
  mark fade (`LaunchScreenColors`, read while drawing), and those snap too under `isStartupScreenHeldUntilAppReady`.
- Schemes are constants, so "no change" is an identity check (the warmed pair is remembered to keep it so).
  `CampfireTheme` tells its content whether its scheme is final from the schemes, not the animation (not started yet in
  the frame preferences arrive); this keeps the launch screen up until colors settle.

## Palettes

- `themeColorOptions()` is the one ordered list read by both the color choice and `colorSchemePair()`; a color the
  device can't honor is left out of it.
- The default palette: purple primary tinting every neutral, orange as tertiary and **second accent**. Material has no
  role for it, so `ColorSchemePair` carries `lightSecondAccent` / `darkSecondAccent` (the primary in every other
  palette), provided as `LocalSecondAccentColor`, faded on the same progress. It draws what is played (chords, a row's
  key, the read only line's key and capo) and the lists' sticky `SectionHeader`s; every other title and control uses the
  primary.
- The one gradient in the interface is the app's own disc (and ring) in the color choice; everything else is flat.
- `MaterialColorSchemes.kt`: seeds run through Material's tonal spot mapping and pasted in; a new color is a seed.
- Dynamic color is Android 12+ only (no "System" swatch elsewhere). From Android 14 it reads role colors, which Samsung
  leaves at the framework's blue while recoloring the palette; where the role primary is far from the palette's tone 40,
  the actual maps the palettes itself.

## Interface scale

`ProvideInterfaceScale` scales `LocalDensity` by 0.85 where `isDesktopPlatform` (so the web follows its input, not its
platform), 1 on touch. Not a setting. Window size classes follow it; song text size multiplies on top; the launch screen
is excluded (it hands over from platform startup screens at their size); the desktop window minimum accounts for it.

## Icons

Directional icons carry `android:autoMirrored` (Compose honors it on every platform) exactly where Material mirrors its
own counterpart (its AutoMirrored icons), so the tag, checklist, and playlist remove icons stay as drawn.

## Fonts

**Bundled on every platform**: Inter for the interface, JetBrains Mono (no-ligatures) for tabs and the editor, so a song
looks and breaks the same on a phone, a computer, and paper. Subset to Latin, Greek, and Cyrillic; Inter's ♭ ♮ ♯ and
JetBrains Mono's ♮ are drawn in by `tools/fonts/accidentals.py`, rerun on any new subset. Others fall back to the script
faces below and system fonts (Skia's Roboto on the web, lacking most symbols), so a string uses no character the bundled
files lack (⌘ is written "Cmd").

- The type scale is read before the first frame except on the web, whose actual returns null until all three weights are
  fetched; the theme isn't settled until then (no second layout of every line), and after five seconds falls back to
  Material's, since a failed fetch is never reported.
- `LocalMonospaceFontFamily` is provided by the theme so the web fetches it at launch.
- `app/web`'s `index.html` preloads them **by their path in the distribution**: renaming one means renaming it there.

### Script faces

- `ScriptFaces.kt` is the table of faces for scripts Inter lacks, a row each; `tools/fonts/script_faces.py` subsets an
  upstream file to its row's ranges, so another script is a row, a run, and its license credited. No code asks for a
  script by name. Only the print names the faces (`scriptFontFamilies`, `ScriptFace.runsIn`), so a PDF is the same on
  every platform; the web holds the layout until they are fetched, five seconds at most.
- `ProvideScriptFaces` registers them as **fallbacks** of Skia's font collection. Skia asks the platform's fonts first,
  so the bundled face is what the web and a Linux desktop without one draw; it takes a fallback by character alone, the
  first registered face having it whatever the weight, so only Regular is bundled and no two rows may share a character
  (tested). Android does nothing: its system fallback's Hebrew is Noto Sans Hebrew. JetBrains Mono falls back the same
  way, so the editor's Hebrew is proportional.
- **The web fetches a face only when needed** (`NeededScriptFaces.kt`: the interface's language, the library's names,
  tags, declared languages, and opened songs' text), keeps it for the session, and registers it with a new resolver,
  which lays every text out again once. A face needed at launch holds the launch screen, five seconds at most. Not
  preloaded by `index.html`; the offline copy still keeps it.

## The app icon

- Follows the theme color wherever the platform allows: Android launcher (`activity-alias` per color), iOS alternate
  icon, web favicon, a running Linux app's window icon. Not Windows (the Store package draws the taskbar) or the Mac
  Dock (a runtime icon is flat, without the system's clear, tinted, or dark styles). `isAppIconThemed` turns it off.
- Every packaged icon is the diagonal gradient of the app's purple and orange. Android's launcher and Play icons are
  drawn as they are; every other is the hand-drawn orange one in `app/icons`, recolored by `app/generate_theme_icons.py`
  (outputs committed).
