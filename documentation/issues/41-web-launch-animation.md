# 41 — Web without a touchscreen: the launch mark gets the desktop's slower exit, holding the loading page longer

**Severity:** a few hundred milliseconds of startup (web on computers) · **Area:** `:presentation`
(`CampfireApp.kt`, `ui/platform/Platform*.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been measured in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

Nothing different on screen — which is the problem. On a computer's browser the page's own loading screen stays up a
little longer than it has to: the app's launch mark underneath it runs the desktop's "grow as it goes" exit over the
slower effects spring, and the loading page is only told to fade (`window.campfireReady`, through `onAppReady`) once
that exit has finished. The mark's growth is never seen, since the loading page covers it.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:237-243`:

```kotlin
                val markGrowth = if (isDesktopPlatform) LAUNCH_MARK_EXIT_GROWTH else 0f
                ...
                val fadeSpec = if (isDesktopPlatform) motionScheme.slowEffectsSpec<Float>() else motionScheme.defaultEffectsSpec<Float>()
```

and `onAppReady()` runs after `opacity.animateTo(0f, fadeSpec)` (`:245-258`). The comment above (`:230-236`) and
`presentation/CLAUDE.md` say this treatment is for "the one platform where the launch screen is the whole of the
startup", the JVM desktop, and that "Android's splash and the web's loading page cover the fade".

But `isDesktopPlatform` means something else on the web
(`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.wasmJs.kt`):

```kotlin
// The page can be open on a phone just as well as on a computer, so the input method decides: with a touchscreen the
// touch treatment is used (a long press and a bottom sheet), without one the desktop treatment is.
internal actual val isDesktopPlatform = !hasTouchScreen()
```

It is about the input method (long press, sheets vs. menus), which is right for its other readers, and wrong for a
question about which startup screen the user is watching.

## The change

Invoke the **`code-style`** skill before the first edit.

A platform value of its own for the question the launch screen actually asks. In
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`, next to
`isDesktopPlatform`:

```kotlin
/**
 * Whether the app's own launch screen is the whole of the startup the user watches, from the first frame the window
 * paints to the app: true only in the desktop application. Android and the web put a startup screen of their own over
 * it (the system splash, the page's loading screen) and iOS shows its storyboard first, so there the launch screen's
 * exit is never watched and is kept short. Not [isDesktopPlatform], which on the web says which input the page is
 * used with rather than what is on screen before it.
 */
internal expect val isLaunchScreenWholeStartup: Boolean
```

Actuals: `true` in `desktopMain/.../Platform.desktop.kt`; `false` in the Android, iOS and wasmJs ones.

In `CampfireApp.kt:237` and `:243`, read it instead of `isDesktopPlatform`:

```kotlin
                val markGrowth = if (isLaunchScreenWholeStartup) LAUNCH_MARK_EXIT_GROWTH else 0f
                ...
                val fadeSpec = if (isLaunchScreenWholeStartup) motionScheme.slowEffectsSpec<Float>() else motionScheme.defaultEffectsSpec<Float>()
```

and swap the import (`isDesktopPlatform` is read nowhere else in `CampfireApp.kt`; check with a grep before removing
it). The comment above (`:230-236`) already describes the new condition; change "On the desktop" to "In the desktop
application".

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check for every target, since an `expect` gains four actuals:
  `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

1. `./gradlew :app:web:wasmJsBrowserDistribution`, serve `app/web/build/dist/wasmJs/productionExecutable` locally, and
   open it in desktop Chrome with DevTools → Performance. Record a reload.
   - Measure from the first frame of the app to the `campfireReady` call (a `console.time` in `index.html`'s
     handler, temporary, or the loading page's fade start in the recording).
   - **Before:** the gap includes the slow effects spring. **After:** it is the default effects spring, and the
     loading page fades out correspondingly sooner. Visually: nothing changes except that.
2. Chrome DevTools device emulation with touch (a phone profile), reload: unchanged (already the plain dissolve).
3. Desktop app (`./gradlew :app:desktop:run`): unchanged — the mark still grows as it fades.
4. Android and iOS: unchanged.
5. Regression for the other reader of `isDesktopPlatform` on the web: on a computer's browser a long press on a song
   row still opens nothing, and the controls still behave as before.

## Docs

- `presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet: "**On the desktop the mark grows half as large again as
  it goes, over the slower effects spring** (`isDesktopPlatform`, `LAUNCH_MARK_EXIT_GROWTH`)" → "**In the desktop
  application the mark grows half as large again as it goes, over the slower effects spring**
  (`isLaunchScreenWholeStartup`, `LAUNCH_MARK_EXIT_GROWTH`) — not in a desktop browser, where the page's loading
  screen covers it".
- `presentation/CLAUDE.md`, the `ui/platform/Platform.kt` bullet: add `isLaunchScreenWholeStartup` to the list of
  `expect` declarations, "(true in the desktop application only, the one platform with no startup screen of its own
  over the app's)".

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.android.kt`
- `presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.ios.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.wasmJs.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. `CampfireApp.kt` order in lane D: 32, 35, **41**, 44 (different lines).
