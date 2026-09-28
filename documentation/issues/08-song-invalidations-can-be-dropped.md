# Keep the open song current when invalidation notifications exceed the buffer

**Kind:** bug · **Severity:** medium (P2) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongContentRepositoryImpl.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, song-content repository/use-case contracts.

## Problem

The content repository uses a `MutableSharedFlow` with 64 extra slots and ignores the result of `tryEmit`
(37, 65–84). The ViewModel collector performs suspending file reads before taking the next notification
(973–976, 1084–1098). While it is reading, enough saves, refresh batches, or import notifications can fill the
buffer. Subsequent events are lost, including a `null` event meaning every song changed.

The 32-name batch threshold only bounds one call. Several smaller batches still fill the buffer, and the
fallback full-refresh event uses the same full buffer. The repository cache is cleared correctly, but the
ViewModel retains separate texts. A currently displayed song can keep showing old lyrics after sync until a
later invalidation or reopen. Cache generation checks do not notify that ViewModel copy.

## Evidence / reproduction

A temporary test subscribed to the real repository, suspended the collector inside its first event, then sent
64 distinct file notifications, an event for `currently-open.cho`, and a full invalidation. After releasing
the collector, it received exactly 65 events: the initial event and 64 buffered ones. Neither the open-song
event nor the full invalidation arrived. No exception or failure is reported to the caller.

## Fix

Replace best-effort event delivery with a coalescing state protocol. A simple safe option is a monotonically
increasing `StateFlow<Long>` revision and rereading the bounded active texts whenever it changes. Conflation is
safe when every revision means “reconcile all active texts,” and a revision arriving during a read must cause
another reconciliation afterwards. If retaining targeted events, keep pending names plus an all-dirty flag in
state that a consumer acknowledges; a dropped wake-up must never drop the dirty state.

Do not merely increase the buffer or switch named events to `DROP_OLDEST`: both still lose relevant files.
Do not suspend an emitter while holding the cache mutex; a collector needs that mutex to read.

## Verification

- Repeat the blocked-collector scenario with more than 64 events and multiple <=32-file batches.
- Verify an invalidation arriving during reconciliation is reflected by its end or a subsequent pass.
- Verify the open viewer and untouched editor follow the new file; a dirty editor keeps its draft.
- Check that rapid invalidations coalesce reads and cannot deadlock a save.

## Conflicts

Coordinate with 09 and 11 in the same repository. Bounding active texts makes a full-reconcile protocol cheaper.
