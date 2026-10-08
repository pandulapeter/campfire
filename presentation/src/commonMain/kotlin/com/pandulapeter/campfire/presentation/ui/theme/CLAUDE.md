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

The theme, the typography and the fonts.

### `ui/theme/`

`ui/theme/` — `CampfireTheme` (`MaterialExpressiveTheme`), which is driven by three independent preferences:
`themeColor` picks the palette, `backgroundWarmth` how far its neutrals are turned towards sepia and `uiMode` picks its
light or dark half. **The sepia is one transform of every palette** (`BackgroundWarmth.kt`, tested, the dimming for
every role `ColorScheme` has, found by reflection in `desktopTest`, so a role a Material update adds fails the build
until `mapColors` names it): the backgrounds, the surfaces and the text, outlines and inverse surfaces on them are moved
in CIELAB towards a paper yellow at their own L*, and the light half is first dimmed as a whole, every role's `Y + 0.05`
multiplied by one factor (down to 0.86, a background at about L* 92, since near white sRGB holds almost no chroma and
the paper would stay white). A WCAG ratio is `(Y1 + 0.05) / (Y2 + 0.05)` and nothing else, so no step of the Settings
slider (four, `UserPreferences.MAX_BACKGROUND_WARMTH`) changes any contrast of the scheme, the second accent's included
(tested pair by pair); the light half's accents keep their hue and come out a shade deeper, the dark half's are left
alone, a color pushed out of sRGB loses chroma rather than lightness, and the warmed pair is remembered so the identity
check below still holds.

Everything that paints a neutral reads the scheme, so the fades, the sheets and the web's `theme-color` follow it. A
change to any of them cross fades every color role from the scheme currently on screen to the new one with one progress
value, so an interrupted switch continues from what is visible instead of jumping back. The value the app is drawn with
moves in five steps (`THEME_FADE_STEPS`) rather than on every frame: the scheme and the second accent are static
composition locals, so every new value recomposes the whole app with skipping off. The first change of every launch is
the app correcting the guess it opened on — the system's setting, until the preferences arrive — and there the app
itself snaps, since it is covered by the launch screen and a cross fade of two static composition locals would recompose
all of it, skipping nothing, on every frame of the busiest stretch of the start. Only the launch screen's background and
mark fade, handed to it as `LaunchScreenColors` and read while drawing: on a launch screen that is nothing but a mark on
the background, a light scheme flipping to a dark one between two frames is the whole window blinking.

Where the platform's startup screen is still over it (`isStartupScreenHeldUntilAppReady`) that fade snaps too. It costs
nothing where there is nothing to correct, since a preference resolving to the palette already on screen is not a change
(the schemes are the constants of `ColorSchemes.kt`, so that is an identity check). `CampfireTheme` tells its content
whether the scheme it is drawn in is the final one, which is what keeps the launch screen in front of the app until the
colors have stopped moving — read from the schemes rather than from the animation, which has not started yet in the
frame the preferences arrive in. `ColorSchemes.kt` holds the `ColorSchemePair` (a palette's two halves, which always
travel together) and `themeColorOptions()`, the single ordered list both the color choice
(`components/ThemeColorChoice.kt`) and `colorSchemePair()` read — a color the device cannot honor is simply not in it.
`OrangeColorScheme.kt` is the orange of the hand-drawn icon, hand-saturated over Material's tonal mapping of #F57C00,
and `GrayColorScheme.kt` is that palette taken role by role to the gray of the same lightness (error roles aside, and
`secondaryContainer` a step further from the surfaces, since only its hue told a selected tag or chip from them).

`CampfireColorScheme.kt`, the app's own and the default, is dusk around a fire in the two colors of the app icon's
gradient: every role at the tone Material's own scheme puts it at, the purple (#5A49CA, exactly tone 40) as the primary
and tinting every neutral — lavender paper by day, a violet night sky in the dark theme — and the orange as the tertiary
and as what is played. The orange is the palette's second accent, which Material has no role for, so a `ColorSchemePair`
carries it as `lightSecondAccent` / `darkSecondAccent` — the primary color itself in every other palette, so reading it
changes nothing there — and `CampfireTheme` provides it as `LocalSecondAccentColor`, cross faded on the same progress
value as the scheme. It draws what is played (the chords of the viewer and the editor, the key in a song row, the key
and the capo of the read only line) and the lists' sticky `SectionHeader`s (an artist, a letter, a setlist), which it
sets apart from every other title — the settings' sections, the filters' "Tags" and "Languages", a song's section pills
— and every control, all of them on the primary.

The one gradient in the interface is the app's own disc in the color choice and its ring, which run from the one accent
to the other like the icon; everything else is flat; `MaterialColorSchemes.kt` is the rest, seven seeds run through
Material's tonal spot mapping and pasted in, so a new color is a seed rather than forty values.
`DynamicColorSchemePair.kt` is the one `expect`/`actual` here: on Android 12 and above it is `dynamicLightColorScheme` /
`dynamicDarkColorScheme`, everywhere else null, which is what keeps the "System" swatch off the other platforms. From
Android 14 those read the role colors (`system_primary_light` …) rather than the tonal palette, and Samsung recolors
only the palette, leaving the roles at the framework's blue; where the role primary is nowhere near the palette's own
tone 40 the Android actual maps the five palettes itself (Material's tonal spot roles, the in-between surface tones
mixed from their neighbors by luminance).

`InterfaceScale.kt` and `ProvideInterfaceScale.kt` draw a pointer driven interface smaller: `ProvideInterfaceScale`,
around everything `CampfireApp` composes once the preferences are in, scales `LocalDensity` by `interfaceScale` — 0.85
where `isDesktopPlatform` (so the web page follows its input rather than its platform), 1 on a touchscreen — which
shrinks Material as a whole, text, icons, paddings and touch targets in the proportions it was designed in, since
Material's own sizes, made for a phone at reading distance, read as oversized on a monitor at arm's length. It is not a
setting. The window size classes are counted in the same dp and follow it; the song text size multiplies on top of it;
the launch screen is left out, since it hands over from the platforms' startup screens at their size; and the desktop
window's minimum is worked out with it (see `app/desktop`). Icons are XML vector drawables in
`composeResources/drawable/`, loaded with `painterResource(Res.drawable.x)` and passed around as `Painter`. The ones
that point a way — back, the previous and next song, undo and redo — carry `android:autoMirrored`, which Compose's
resource loader honors on every platform, so they point the other way where the system reads right to left and the rest
of the chrome is mirrored.

### `ui/theme/InterfaceTypography.kt`

`ui/theme/InterfaceTypography.kt` — the typography `CampfireTheme` hands to Material: Material's own on three platforms,
where its sans serif resolves to the system's font. The web build's Skia cannot reach the browser's fonts and carries a
single weight of Roboto, so every style Material sets in Medium came out Regular and the interface read as thin; that
build bundles Inter (the closest to the desktop systems' fonts that may be shipped, SF Pro's license keeping it on
Apple's platforms) in Regular, Medium and Bold, subset to Latin, Greek, Cyrillic and the symbols a song uses and to the
default layout features, with its OFL license under `files/licenses`. The actual answers null until all three weights
are in, and the theme does not report itself settled until then, so the launch screen covers the app rather than every
line of text in it being laid out a second time; a font that has not arrived within five seconds gives way to Material's
typography, since Compose resources never reports a failed fetch. `app/web`'s `index.html` preloads the files by their
path in the distribution.

### `ui/theme/MonospaceFontFamily.kt`

`ui/theme/MonospaceFontFamily.kt` — `LocalMonospaceFontFamily`, provided by `CampfireTheme` and read by the tab blocks
in `SongLyrics` and the editor's field. It is the system's monospaced font everywhere but the web, where Compose draws
with its own Skia and cannot reach the browser's fonts, so `FontFamily.Monospace` there is the same proportional font as
the rest of the text and the columns of a tab drift apart. That build bundles JetBrains Mono (the no-ligatures variant,
regular and bold, subset to Latin, Greek, Cyrillic and the symbols a song uses) in
`src/wasmJsMain/composeResources/font`, with its OFL license next to it under `files/licenses`, so the other three
builds do not carry it. `app/web`'s `index.html` preloads both files next to the binaries, and the theme provides the
family so that it is read as the app starts rather than when the first tab is opened; until it has arrived the family is
the default one. The preload links name the files by their path in the distribution, so renaming a font is renaming it
there too.

## The app icon and the app's own colors

### The app icon follows the theme color

**The app icon follows the theme color** wherever the platform lets an app change it: the launcher entry on Android (one
`activity-alias` per color, see `app/android`), an alternate icon on iOS, the favicon on the web, and the window,
taskbar and Dock icons of a running desktop app — unless the user turned that off (`UserPreferences.isAppIconThemed`, a
switch under the colors named after the platform's icon), which keeps the app's own. **The app's own colors are the
promotional material's purple and orange** (#5A49CA and #F57C00): its icon, and so every packaged one (the installed
app, the Start menu, a store's page), is their diagonal gradient, and the "Campfire" palette is dusk around a fire:
Material tones of the purple, tinting the neutrals too (a lavender paper by day, a violet night sky in the dark theme),
with the orange as the tertiary and the second accent — the chords, the key, the capo and the lists' sticky headers
(`LocalSecondAccentColor`, which is the primary color in every other palette); the one place the two meet in the
interface is its color disc in Settings, which is the icon's gradient.

The gray is the last option, a color like the rest; it was the app's own before, under the id the new palette took, so
nobody was left on it. Android's own launcher icon and its Play icon are drawn as they are; every other icon, the
packaged ones included, is the hand-drawn orange one in `app/icons` recolored by `app/generate_theme_icons.py` — the
app's own onto the gradient read off the Android icon's background — and the files it writes are committed.
