# 13 · Web: reloading or closing the tab discards unsaved editor text without asking

**Severity:** data loss (web; likely — a reload by reflex, a closed tab, a swipe-back on a trackpad or a phone) ·
**Area:** `:presentation` `wasmJsMain` — `CampfireWebApp`

## Symptom
1. In the web build, open a song in the editor and type for a while without pressing Save.
2. Press F5 / Cmd+R, close the tab or the window, type another address, or go Back in the browser (the button, a
   two-finger swipe on a trackpad, the edge swipe of a phone browser).
3. The page is gone at once, and everything typed since the last save with it. No question was asked.

Every other way out of the editor asks (`navigateBack` → the *Unsaved changes* dialog), and the desktop build asks
before its window closes (`requestExit`). The web build is the one where leaving by accident is the easiest, and the
only one with nothing in the way.

## Cause
Nothing in the repository listens to `beforeunload` (`grep -rn "beforeunload\|pagehide" presentation app` finds
nothing). `CampfireViewModel.hasUnsavedEditorChanges` is only consulted by `navigateBack` and `requestExit`, and
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt:30-44` wires neither
to the page:

```kotlin
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel(),
) = CompositionLocalProvider(
    LocalFilePicker provides WebFilePicker
) {
    DisposableEffect(Unit) {
        startForwardingEscapeKey()
        onDispose { stopForwardingEscapeKey() }
    }
    CampfireApp(…)
}
```

**Browser Back** needs a word of its own. The web build does not map the browser's history to the Navigation 3 back
stack at all: there is no `pushState`, no `popstate` listener and no navigation binding anywhere (the only history
call in the project is the `replaceState` that takes the OAuth answer out of the address bar,
`SyncAuthenticator.wasmJs.kt:73`). The whole session is one history entry, so Back never pops a screen — it leaves
the document for whatever the tab showed before (the Dropbox consent page after a connect, the site that linked
here, or nothing in a fresh tab, where the button is disabled). Leaving the document is a `beforeunload` like a
reload is, so the fix below covers it; turning browser Back into in-app back navigation is a feature, not part of
this plan.

## Fix
The browser's own "Leave site?" prompt, armed only while there is unsaved text. It needs no `expect`/`actual`: the
web shell already holds the view model, and a shell is where the project does what only one platform can (the
desktop's close guard is wired the same way, from `:app:desktop` into `CampfireViewModel.requestExit`). The other
three targets get nothing, not even a no-op.

1. `CampfireWebApp.kt`, inside `CampfireWebApp`, after the existing `DisposableEffect`:

   ```kotlin
   // Collected in an effect rather than read as lifecycle-aware state: a tab that is closed from the tab strip
   // while another one is in front is a hidden page, and that is no moment to have stopped listening.
   LaunchedEffect(viewModel) {
       viewModel.hasUnsavedEditorChanges.collect { hasUnsavedChanges ->
           if (hasUnsavedChanges) startWarningBeforeUnload() else stopWarningBeforeUnload()
       }
   }
   DisposableEffect(Unit) {
       onDispose { stopWarningBeforeUnload() }
   }
   ```
   (add `import androidx.compose.runtime.LaunchedEffect`; the file keeps its import order.)

2. Same file, next to `startForwardingEscapeKey` / `stopForwardingEscapeKey`:

   ```kotlin
   /**
    * Makes the browser ask before the page is left - reloaded, closed, navigated away from, or gone back from,
    * which leaves the page too, since the app puts nothing into the browser's history. The editor only writes when
    * it is told to, so at that moment the page holds the only copy of what was typed.
    *
    * The listener exists only while there is unsaved text ([stopWarningBeforeUnload] otherwise): a page with a
    * `beforeunload` listener is one the browser cannot keep in its back/forward cache, and one that always asks is
    * one the browser stops listening to. What the prompt says is the browser's own business - the text a page
    * supplies has not been shown by any of them for years - so there is nothing here to translate. The browser
    * also only asks once the user has interacted with the page, which typing has taken care of.
    */
   private fun startWarningBeforeUnload() {
       js(
           """(function () {
               if (window.campfireUnloadWarning) return;
               window.campfireUnloadWarning = function (event) {
                   event.preventDefault();
                   // What asks the question in the browsers that predate preventDefault doing so.
                   event.returnValue = true;
               };
               window.addEventListener('beforeunload', window.campfireUnloadWarning);
           })()"""
       )
   }

   /** Undoes [startWarningBeforeUnload]. */
   private fun stopWarningBeforeUnload() {
       js(
           """(function () {
               if (!window.campfireUnloadWarning) return;
               window.removeEventListener('beforeunload', window.campfireUnloadWarning);
               window.campfireUnloadWarning = null;
           })()"""
       )
   }
   ```
   Both are idempotent, because the flow's first emission is `false` and calls `stop…` before anything was started.

3. Update the KDoc of `CampfireWebApp`: "Web shell of the shared UI. Links open in a new browser tab, files dropped
   on the page are imported, and the browser asks before the page is left with unsaved text in the editor."

What must **not** be done:
- Do not register the listener permanently and decide inside it (by calling back into Kotlin or reading a flag): the
  bfcache penalty comes from the listener existing, not from what it does.
- Do not try to save from the handler. OPFS writes are asynchronous and a page being unloaded does not wait for
  promises; a half-done write is what plan 12 is about avoiding.
- Do not use `unload` or `pagehide` for this: neither can ask anything.

Known limit, to be stated in the docs rather than worked around here: iOS Safari (and so every iOS browser) does
not reliably fire `beforeunload` when a tab is closed or the browser is swiped away, and no browser asks when its
process is killed. The prompt is a guard against accidents on a keyboard-and-mouse browser, which is where the web
build's editor is actually used; persisting the draft is a different feature (see plan 14 for the Android
counterpart of that question).

## Tests
None (the UI is untested).

## Verify
`./gradlew :app:web:wasmJsBrowserDevelopmentRun`, then in Chrome, Firefox and Safari:
1. Open a song in the editor, type a character, press Cmd/Ctrl+R: the browser asks; "Cancel"/"Stay" keeps the page
   and the text. Same for closing the tab and for typing another URL.
2. Press Save in the editor, reload: no prompt. Type again, leave the editor with **Discard**, reload: no prompt.
3. Navigate to the app from another page (so Back is enabled), type in the editor, press browser Back: the prompt.
4. With no unsaved text, DevTools console: `getEventListeners(window).beforeunload` (Chrome) is `undefined`; with
   unsaved text it has one entry. Chrome DevTools → Application → Back/forward cache → "Test back/forward cache"
   with no unsaved text must not list a `beforeunload` reason.
5. `./gradlew :app:web:wasmJsBrowserDistribution` still builds; the other three targets are untouched.

## Docs
- `presentation/CLAUDE.md`, the `wasmJsMain/ui/CampfireWebApp.kt` bullet, append: "While the editor holds unsaved
  text (`hasUnsavedEditorChanges`) it also keeps a `beforeunload` listener registered, so a reload, a closed tab or
  the browser's Back — which leaves the page, since the app puts nothing into the browser's history — is asked about
  by the browser first. The listener is there only for as long as there is something to lose, because a page that
  has one is kept out of the back/forward cache."
- `app/web/CLAUDE.md`: none (it is about the loading page and the build).
- `documentation/features.md:35`: "…and leaving with unsaved changes asks first" may gain "— closing or reloading
  the browser tab included"; optional.

## Touches
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt`
- `presentation/CLAUDE.md`
- `documentation/features.md` (optional sentence)

## Depends on
Nothing. Independent of plan 05 (it reads the same `hasUnsavedEditorChanges` state, which 05 does not change).
Plan 49 (a second tab is turned away at the loading page) and plan 21 (the connecting state after browser Back from
the consent page) touch other files.
