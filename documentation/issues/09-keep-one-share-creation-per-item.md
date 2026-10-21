# Keep one link creation per song or setlist, so Create link for one item is not dropped while another's is still being made

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — the controller test must hand the controller `backgroundScope` (its `shares` is an eager `stateIn`, which would keep `runTest` from finishing); the `LinkExportStateTest` cases are marked as pins that pass today, the controller test being the proof; the finished-after-close entries left in the map are named.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/share/ShareLinkController.kt`,
`…/ui/screens/export/LinkExport.kt`, `…/ui/screens/export/LinkExportState.kt`, `…/ui/CampfireViewModel.kt` (only if
`forgetCreation`'s call changes), `…/ui/screens/export/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LinkExportStateTest.kt`, a
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/share/ShareLinkControllerTest.kt`

(`…/ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.) Plan 01 (lane R) may change
`LinkExportState` and its wording before this lands: locate the code below by its snippet, not by line.

## Problem

`ShareLinkController` holds a single creation and drops any request while it runs:

```kotlin
private val _creation = MutableStateFlow<ShareCreation?>(null)
...
/** Shares [target] as a link, see [CreateShareLinkUseCase]; one at a time, a second request while one runs ignored. */
fun create(target: ShareTarget, title: String) {
    if (_creation.value?.let { it.result == null } == true) return
    _creation.value = ShareCreation(target = target, result = null)
```

and keeps a running one when its screen goes:

```kotlin
fun forgetCreation(target: ShareTarget) = _creation.update { if (it?.target == target && it.result != null) null else it }
```

`linkExportStateOf` only looks at a creation of its own target (`val ownCreation = creation?.takeIf { it.target == target }`),
so another item's export shows `NotShared` with **Create link** (`linkActionOf`). On a slow network: Create link for song
A, close the export (A's upload keeps running, as intended), open song B's export on Link, tap Create link: nothing
happens, no progress, no message, every tap until A's creation finishes. The button looks broken.

Making two shares at once is safe below the presentation layer: `SharedLinksRepositoryImpl.share` names each copy by a
fresh id and edits the account's list against its revision with retries (`SharedLinksRegistry.edit`, see
`data/sync/implementation/.../shared/CLAUDE.md`: "an action and a run … never clobber each other"), so the "one at a
time" rule guards nothing but the single slot.

## Fix

**Decided (2026-10-21): one creation per target.** `_creations = MutableStateFlow<Map<ShareTarget, ShareCreation>>(emptyMap())`
  (or keep `ShareCreation` and key by its `target`). `create` ignores a request only for a target whose own creation is
  running (a double tap), starts it otherwise, and writes the result into that target's entry
  (`_creations.update { map -> map[target]?.let { map + (target to it.copy(result = result)) } ?: map }`, so a
  creation forgotten or cleared meanwhile is not put back). `forgetCreation(target)` removes that target's entry only
  if it has a result. `stop` and `stopEverything` clear the entries whose `CreateShareResult.Shared` share they stop,
  as today for the one slot. `LinkExport.kt` passes `creations[target]` to `linkExportStateOf`, whose signature can stay
  (`creation: ShareCreation?`), or takes the map; keep the state function pure.

A creation that finishes after its screen went keeps its entry (`forgetCreation` ran while it was running), as the
one slot keeps it today until the next creation: with a map it stays until that item's export is opened and closed
again, or the item is shared again; a few small entries, and reopening shows how that attempt ended, as today. No
change needed.

Rename the KDoc of `create` ("one at a time per item, a second request for the same item while it runs ignored") and
update `screens/export/CLAUDE.md` › Link if it states the single creation ("A creation runs in the view model's scope
and is forgotten when the screen goes" stays true per item).

## Tests

- New `ShareLinkControllerTest` (fake `CreateShareLinkUseCase` that suspends on a `CompletableDeferred` per call, the
  other use cases trivial fakes; the controller gets `backgroundScope`, not the `TestScope`, since `shares` is an
  eager `asState` that never completes; `runCurrent()` moves the launched creations on): while A's creation is
  running, `create(B)` starts B's (the fake sees two calls) and both results land in their own entries; a second `create(A)` while A runs is ignored;
  `forgetCreation(A)` keeps a running A and removes a finished one.
- `LinkExportStateTest`: with A's creation running, B's state is `NotShared` and its action is `CREATE` (unchanged
  behaviour of the pure function, now reached), and B's own running creation is `Working`. Both pass today: they pin
  the pure function; the controller test is the one that fails without the fix (today `create(B)` is dropped, so the
  fake sees one call).
