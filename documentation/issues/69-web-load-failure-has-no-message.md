# 69 · A failed web load leaves the splash screen with a stuck bar and no message

**Severity:** low · **Area:** `:app:web` (`index.html`)

The fetch wrapper in `index.html` (:211–259) decrements its counters on a rejected fetch and rethrows; nothing on the
page reacts. A network failure, a blocked `.wasm` download or an uncaught exception from `campfire.js` leaves the
progress bar at wherever it stopped.

## Fix

1. Add a hidden `<p id="splash-message">` under the bar and a `<button id="splash-retry">` (`location.reload()`).
2. In the fetch wrapper's rejection handler and in a `window.addEventListener('error', …)` /
   `'unhandledrejection'` registered before `campfire.js` loads, call `showFailure()`: set the message text ("Campfire
   could not be loaded. Check the connection and try again."), reveal the button, stop `schedule()`.
3. Once `campfireReady()` has fired, remove those listeners (the app's own errors are not the loader's business).
4. The page is English only by design (it is shown before the app's language is known); keep it that way.
5. `app/web/CLAUDE.md`: one line about the failure state.
