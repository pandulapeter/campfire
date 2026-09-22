# 36 · Snackbar messages appear behind the on-screen keyboard, including the editor's "could not save"

**Severity:** minor (Android, iOS and mobile browsers. Any message sent while the keyboard is up: a failed save from
the editor, the "file is gone" notice, an import finishing while a search field has focus. The message times out
unseen; for a failed save it is the only word the user gets) · **Area:** `:presentation` (`ui/CampfireApp.kt`,
`Messages` placement)

## Symptom
1. Open a song in the editor, type, keep the keyboard up and tap Save while the write fails (full storage, or on
   desktop-less platforms simply delete the file by sync so that `EditedSongFileGone` is sent).
2. The snackbar is laid out at the bottom of the window, under the keyboard, and disappears after its timeout without
   ever being visible.

## Cause
The app draws edge to edge and handles the keyboard itself through insets (`enableEdgeToEdge()` in
`CampfireActivity.kt:37`, no `windowSoftInputMode` resize; the screens' content padding is `KeyboardAwarePadding`,
`CampfireApp.kt:318-343`). The snackbar host is padded by the chrome and the system bars only
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:488-493`):

```kotlin
Messages(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = navigationBarHeight + systemBars.calculateBottomPadding()),
    viewModel = viewModel,
)
```

## Fix
Pad the host with a `KeyboardAwarePadding` whose floor is today's padding, so the snackbar sits above whichever is
higher, the chrome or the keyboard. `KeyboardAwarePadding` reads the IME inset in `calculateBottomPadding`, which
`Modifier.padding(PaddingValues)` calls during measure, so the keyboard animation only relayouts the host and does not
recompose the app (the reason that class exists, see its KDoc).

In `CampfireContent`, next to `songDetailsContentPadding` (after `:402`), add:

```kotlin
    // The snackbar sits above the chrome, and above the keyboard wherever that reaches higher: the app is laid out
    // under the keyboard rather than resized by it, and a message sent while somebody is typing - a save that failed
    // in the editor - would otherwise time out behind it unseen.
    val messagesPadding: PaddingValues = KeyboardAwarePadding(
        start = 0.dp,
        end = 0.dp,
        bottom = navigationBarHeight + systemBars.calculateBottomPadding(),
        coveredHeight = 0.dp,
        ime = ime,
        density = density,
    )
```

and replace `:488-493` with:

```kotlin
        Messages(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(messagesPadding),
            viewModel = viewModel,
        )
```

Why `coveredHeight = 0.dp` with the chrome in `bottom`: the IME inset is measured from the bottom of the window, so
`max(bottom, ime)` is exactly "above the navigation bar, or above the keyboard". Keeping the navigation bar height in
`bottom` also keeps the snackbar at today's place whenever the keyboard is down (the editor covers that bar, which is
how it is today too). `start`/`end` stay zero, as the host had no horizontal padding. No new imports: `PaddingValues`,
`dp` and `KeyboardAwarePadding` are already in the file.

Do **not** use `Modifier.windowInsetsPadding(WindowInsets.ime)` on top of the existing padding: it adds the two
(the snackbar would float a navigation bar's height above the keyboard) and it does not take the max.

## Tests
None (UI).

## Verify
1. Android emulator: open a song in the editor, focus the field (keyboard up). Trigger a message: the simplest is the
   "file is gone" one (delete the file with `adb shell run-as <package> rm files/library/songs/<name>.cho` and pull
   to refresh in split screen, or via sync). The snackbar appears directly above the keyboard and moves with it as the
   keyboard is dismissed, without the app flickering.
2. Keyboard down, on the song list: an import message sits above the navigation bar exactly as before. In landscape
   on a tablet (rail): above the system bar as before.
3. iOS simulator (with the software keyboard shown, Cmd+K): same as 1.
4. Desktop and web desktop: nothing changes (IME inset is 0).

Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
`:presentation:compileKotlinWasmJs`.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet, after "…a message leaves the queue once it has been shown,
`onMessageShown`)" add: ", laid out above the chrome and above the keyboard wherever it reaches higher (the same
`KeyboardAwarePadding` the screens use, so the keyboard's animation only relayouts it)".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 40 edits the `Messages` `when` in the same file; run them one after another.
