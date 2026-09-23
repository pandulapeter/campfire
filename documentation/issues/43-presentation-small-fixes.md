# 43 — Small fixes in `:presentation`: notification permission docs, compact stepper docs, iPad share popover, an unused string

**Severity:** documentation, one misplaced popover (iPad), dead string · **Area:** `:presentation` (docs,
`SongDisplayControls.kt` KDoc, `strings.xml`), `:app:ios` (`IosFilePicker.kt`)

**Read, not run.** These were found by reading the code at HEAD (2065e47f) and the Compose UI 1.12.0 sources; none
has been reproduced. The "Verification" section below is how to confirm them.

Four independent items (screens review F9a, F9b, F9c; state review "minor").

## A. The notification permission is asked once per launch, not once ever (docs)

**Decided by the orchestrator (a default the user may override):** the documentation is corrected, the behaviour is
kept.

`presentation/CLAUDE.md:16` ends: "A refusal costs the notification, not the sync, so the answer is ignored and never
asked about again." `documentation/testing/01-android.md:341-342` (AND-082): "Notification permission is asked once,
when sync connects; refusing it doesn't stop sync, and it isn't asked again."

The code asks once per launch
(`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncNotificationPermission.kt`):

```kotlin
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSyncConnected, hasAsked) {
        if (!isSyncConnected || hasAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        hasAsked = true
        ...
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
```

and its own KDoc says so ("[hasAsked] keeps it to one prompt per launch … once Android has taken two refusals it stops
showing the dialog by itself"), while its first paragraph ends ambiguously: "which is why this asks and then never
mentions it again".

Change:

- `presentation/CLAUDE.md:16`: "so the answer is ignored and never asked about again." → "so the answer is ignored:
  it is asked once per launch while sync is connected and the permission is missing, and after the second refusal
  Android stops showing the dialog on its own."
- `SyncNotificationPermission.kt` KDoc, first paragraph: "which is why this asks and then never mentions it again." →
  "which is why a refusal is never argued with: nothing in the app mentions it."
- `documentation/testing/01-android.md` AND-082: "and it isn't asked again." → "it is asked again on the next launch
  only, and after a second refusal Android shows no dialog at all."

## B. The compact stepper and touch devices (docs)

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt:209-211`:

```kotlin
 * @param isCompact Trades the 48dp touch targets for a shorter pill. The app bar uses it, because there the height
 * of the buttons is what makes the two groups look oversized; the bottom sheet, which is what touch devices get,
 * does not.
```

The inline steppers are shown wherever `WindowSize.usesInlineSongControls`, which is `this == EXPANDED` (≥840dp,
`WindowSize.kt:24`) — tablets in landscape and many phones in landscape (a phone is 850–930dp wide on its side).
Those are touch devices, and they get the compact pill (36×40dp buttons, `COMPACT_BUTTON_WIDTH`/`COMPACT_HEIGHT`,
`:329-330`), with `LocalMinimumInteractiveComponentSize` lowered to match (`:238-240`). The editor's
`TextTranspositionControls` is compact on every device (`isCompact = true`, `:162`).

**Corrected from the reviewer's suggestion** (key the compact variant on the pointer platform): the touch targets are
not actually 36dp on a touch screen. Lowering `LocalMinimumInteractiveComponentSize` only lets the buttons be *laid
out* smaller; Compose's hit testing still extends every pointer input node smaller than
`ViewConfiguration.minimumTouchTargetSize` (48dp) to that size for touch input
(`NodeCoordinator.kt:698-728`, `SuspendingPointerInputFilter.kt:578-581` in Compose UI 1.12.0), taking the nearest
target where two extended areas overlap. A tap a few dp above or beside the pill still lands on its button. What the
KDoc says about touch devices is what is wrong, so the KDoc is what changes; making the inline pills 48dp tall on
touch would bring back the oversized look the compact variant exists to avoid, and would grow the editor's control row
on the phones where it is tightest.

Change the KDoc to:

```kotlin
 * @param isCompact A shorter pill with narrower buttons, for the app bar, where full height buttons make the two groups
 * look oversized: the inline steppers of a window at least 840dp wide, tablets and phones on their side included, and
 * the editor's. On a touch screen the buttons still take a touch 48dp across, since Compose extends a small target's
 * touch area to the minimum touch target size; only their drawn size shrinks. The bottom sheet of a narrower window
 * keeps the full size.
```

and `presentation/CLAUDE.md:74`: "the app bar uses a shorter variant of it (`isCompact`, which also lowers
`LocalMinimumInteractiveComponentSize` to match the smaller buttons), the sheet keeps the 48dp touch targets." →
"the app bar uses a shorter variant of it (`isCompact`, which also lowers `LocalMinimumInteractiveComponentSize` to
match the smaller buttons — a touch screen still gets a 48dp touch area around each, from Compose's own touch target
expansion), the sheet keeps the full size."

## C. iPad: the share sheet's popover points at the top-left corner

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:121-123`:

```kotlin
                    val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                    // An iPad presents this as a popover, which needs something to point at; the whole view will do.
                    controller.popoverPresentationController?.sourceView = host.view
```

`sourceRect` is left at its default, `CGRectZero`, so the popover's arrow points at the origin of the view — the
top-left corner of the screen — whichever menu the share was chosen from. The menu that asked for it is a Compose
dropdown the picker cannot see, so there is no real anchor to point at; the honest presentation is a popover in the
middle of the screen without an arrow.

Change (invoke the **`code-style`** skill first):

```kotlin
                    val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                    // An iPad presents this as a popover, which needs something to point at. The menu the share was chosen
                    // from is Compose's and out of reach from here, so the popover stands in the middle of the view with no
                    // arrow rather than pointing at the view's corner, which is where a source rectangle left at zero is.
                    controller.popoverPresentationController?.let { popover ->
                        val bounds = host.view.bounds
                        popover.sourceView = host.view
                        popover.sourceRect = bounds.useContents { CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0) }
                        popover.permittedArrowDirections = 0uL
                    }
```

Imports: `kotlinx.cinterop.useContents`, `platform.CoreGraphics.CGRectMake`. `permittedArrowDirections` is a
`UIPopoverArrowDirection` (`ULong` in Kotlin/Native); `0uL` is "none". The iPhone ignores all of it (the sheet is not a
popover there).

## D. `settings_sync_syncing` is unused

`presentation/src/commonMain/composeResources/values/strings.xml:317` (`Syncing…`) and `values-hu/strings.xml:317`
(`Szinkronizálás…`) are referenced nowhere (`grep -rn settings_sync_syncing` over `.kt`, `.swift` and `.xml` finds only
the two definitions; the running state is `settings_sync_progress` / `settings_sync_preparing`). Delete both lines.
The localization plugin regenerates the tables; nothing else refers to the key.

## Tests

- **No unit test is possible** for any of the four.
- Compile checks: `./gradlew :presentation:compileKotlinDesktop :presentation:compileDebugKotlinAndroid :presentation:compileKotlinWasmJs :presentation:compileKotlinIosSimulatorArm64 :app:ios:linkDebugFrameworkIosSimulatorArm64`
  (the last for C).

## Verification

1. **A**: Android 13+ emulator, sync connected, deny the notification prompt; kill and relaunch → asked again; deny;
   relaunch → no dialog (Android's own rule). Matches the corrected AND-082.
2. **B**: Android phone in landscape (≥840dp wide), a song with chords: tap 4dp above the + of the inline stepper →
   it steps. Nothing changes in the code; this confirms the claim the KDoc now makes.
3. **C**: iPad simulator, `xcodebuild` as in the root `CLAUDE.md`, a song → ⋮ → Share. **Before:** the popover's arrow
   points at the top-left corner. **After:** the share popover is centred with no arrow; rotate the iPad while it is
   up → it stays sensible (UIKit re-anchors to the same rect of the same view). iPhone: unchanged sheet.
4. **D**: a clean build (the plugin's generated tables, see `presentation/CLAUDE.md`) succeeds, Settings → Library
   during a sync shows the progress rows as before, in English and Hungarian.

## Docs

Covered per item: `presentation/CLAUDE.md` (lines 16 and 74), `documentation/testing/01-android.md` (AND-082).
`app/ios/CLAUDE.md` says nothing about the popover's anchor; no change.

## Files touched

- `presentation/CLAUDE.md`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncNotificationPermission.kt` (KDoc)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt` (KDoc)
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `documentation/testing/01-android.md`

## Depends on

Nothing. After 29 in lane D (29 changes `TranspositionControls` in `SongDisplayControls.kt`; this only its KDoc).
Plan 44 adds a string to both `strings.xml` files (a different key); either order.
