# Reload a web page restored from the back/forward cache when another tab launched the app while it was away

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Files:** `app/web/src/wasmJsMain/resources/index.html`, `app/web/tests/offline.test.cjs`, `app/web/CLAUDE.md`

## Problem

The web build keeps the whole library in memory, in the repositories, and writes files back from what it remembers.
Only one tab may own the library, and that is what the `campfire-library` Web Lock enforces (`app/web/CLAUDE.md`: "keeps
OPFS from changing behind the running app's cached repositories"). A page that goes into the back/forward cache loses
its lock, though, and comes back holding the memory it had when it left. At b5c8ed3b5, `index.html`:

```js
// A page that is put into the back/forward cache loses its locks, and gets none back when it is restored -
// pressing Back on the consent page is how that happens. It asks again, and if another tab has taken the
// library in the meantime it reloads, which lands on the page above.
window.addEventListener('pageshow', function (event) {
    if (event.persisted && hasStarted) {
        claimLibrary(function () {}, function () {
            location.reload();
        });
    }
});
```

This handles a tab that *still* holds the lock. It does not handle a tab that held the lock while this page was cached
and has since closed. Here is the sequence:

1. Tab 1 runs the app and leaves for another page in the same tab, such as Connect → the Dropbox consent page.
   With nothing unsaved, no `beforeunload` listener is registered, so the page is eligible for the cache.
2. The user opens the app in tab 2. Tab 2 gets the lock, edits a song, deletes another, changes a setting, and is
   closed.
3. In tab 1 the user presses Back. The page is restored from the cache, `claimLibrary` is granted (nobody holds the
   lock now), and the old app carries on with its stale song list, setlists, song texts, preferences and sync index.

The next write from tab 1 is built on that memory. A setlist edit writes the whole setlist file as tab 1 remembers
it, and a preferences change writes the whole `preferences.json` as tab 1 remembers it, so tab 2's changes to those
files are lost. A song the list still shows can be opened, but its file is gone. The next sync run then sends whatever
tab 1 wrote to every device.

Rescanning wouldn't be enough. `CampfireApp` rescans on `ON_START` only where `isLibraryEditableOutsideApp` is true,
and that is false on the web (`Platform.wasmJs.kt`). Even if a `pageshow` called `viewModel.refresh()`, that only runs
`LoadScreenDataUseCase(isRescan = true)`, which rescans songs and setlists and calls
`userPreferencesRepository.loadUserPreferencesIfNeeded()`. The preferences, the sync credentials and index, and the
held song texts would all stay stale. A reload is the only thing that reads everything again.

The scenario is rare: it needs a second tab used while the first sat in the cache. But when it happens it silently
undoes the user's edits.

## Fix

Have each launch mark the library as claimed by this page. When the page is restored from the cache, reload it if
another page has claimed the library since. Don't reload otherwise: the normal case is a Back from the consent page,
and that should stay as instant as it is now.

In `window.campfireLaunch` (the pure decisions the Node tests run), add and export:

```js
/**
 * Whether a page restored from the back/forward cache has to start again: another page launched the app while this
 * one was away, and may have changed every file this one remembers. [own] is the claim this page made as it
 * launched, [latest] the one the origin holds now; either missing means it cannot tell, which is answered the
 * safe way.
 */
function isClaimedElsewhere(own, latest) {
    return !own || own !== latest;
}
```

In the page script, next to `LOCK_NAME`:

```js
// Written by every launch that is granted the library, so that a page coming back from the back/forward cache can
// tell whether another one had it in the meantime (local storage is shared by every tab of the origin).
var LIBRARY_CLAIM = 'campfire-library-claim';
var ownClaim = null;

function noteClaim() {
    try {
        ownClaim = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
        window.localStorage.setItem(LIBRARY_CLAIM, ownClaim);
    } catch (error) {
        ownClaim = null;
    }
}

function latestClaim() {
    try {
        return window.localStorage.getItem(LIBRARY_CLAIM);
    } catch (error) {
        return null;
    }
}
```

Call `noteClaim()` once the lock is granted for a launch. Do that at the top of `launch()`, after its `hasStarted`
guard, because every path that is granted the library goes through `launch`: the first claim, the Retry of the
"already open" page, and the no-Web-Locks fallback. Then change the `pageshow` handler:

```js
window.addEventListener('pageshow', function (event) {
    if (event.persisted && hasStarted) {
        claimLibrary(function () {
            // The lock is free again, but the library may have been another page's while this one was cached, and the
            // app remembers it as it was: only a fresh start reads every file again.
            if (window.campfireLaunch.isClaimedElsewhere(ownClaim, latestClaim())) {
                location.reload();
            }
        }, function () {
            location.reload();
        });
    }
});
```

Update the comment above the handler to cover both cases. A reload here loses nothing that was unsaved: a page whose
editor holds unsaved text keeps a `beforeunload` listener, which keeps it out of the back/forward cache to begin with
(`presentation/src/wasmJsMain/CLAUDE.md`). The reload starts the kept build from the same address, and an OAuth answer
in the address bar survives it the same way it survives the existing reload.

Leave out a wasm-side listener or a `refresh()` call (the alternative the finding suggested). As explained above, a
rescan leaves the preferences and the sync state stale.

Docs: in `app/web/CLAUDE.md`, change "A page restored from the back/forward cache reclaims the lock in `pageshow`" to
"…reclaims the lock in `pageshow`, and reloads if another tab holds it, or if another page launched the app while it
was cached (`campfire-library-claim` in local storage, `isClaimedElsewhere`), since its repositories remember the
library as it was."

## Tests

In `app/web/tests/offline.test.cjs` (run by `node --test app/web/tests/offline.test.cjs`, which `tests.yml` runs),
add:

```js
test('a page restored from the back/forward cache starts again only when another page claimed the library meanwhile', () => {
    assert.equal(launch.isClaimedElsewhere('a', 'a'), false);
    assert.equal(launch.isClaimedElsewhere('a', 'b'), true);
    assert.equal(launch.isClaimedElsewhere('a', null), true);
    assert.equal(launch.isClaimedElsewhere(null, 'a'), true);
});
```

## Manual check

In Chrome, on the deployed site or a local imitation of GitHub Pages (`web-target-verification` memory):

1. Open the app in tab 1, with nothing unsaved. Navigate the same tab to another site (type an address), and check in
   DevTools → Application → Back/forward cache that the page is eligible.
2. Open the app in tab 2, rename or delete a song and change the theme, then close tab 2.
3. Press Back in tab 1. The page reloads, through the progress bar, and shows tab 2's changes.
4. Repeat without step 2. Back restores tab 1 instantly, with no reload.
5. Repeat with tab 2 still open at step 3. Tab 1 reloads onto the "already open" page, as it does today.
