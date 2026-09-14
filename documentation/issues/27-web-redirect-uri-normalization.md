# 27 · The web redirect URI is whatever path the user opened

**Severity:** low (a Dropbox "redirect URI mismatch" page for `…/campfire/index.html`) · **Area:** `:data:source:remote:implementation` (wasmJsMain)

`SyncAuthenticator.wasmJs.kt:53` returns `origin + pathname`. Opening `…/campfire/index.html` instead of
`…/campfire/` yields a redirect URI Dropbox has not registered.

## Fix

Normalize in `currentPageUrl()`: strip a trailing `index.html` from `pathname`, and make sure the result ends with
`/` (`…/campfire/`). Keep the registered URI list in the Dropbox app console in sync (both `https://pandulapeter.github.io/campfire/`
and the dev server's `http://localhost:8080/`). Document the rule in `app/web/CLAUDE.md`.
