# 34 · Android: rotating the device while a snackbar is up (an import's result, a failed save) drops it and every message queued behind it

**Severity:** minor (Android only, since only Android recreates the composition. Messages that are the only report of something, "Export failed", "Could not save", an import's counts, are lost if the device is rotated or the system theme changes while they wait or show) · **Area:** `:presentation` (`CampfireApp.kt`: `Messages`; `CampfireViewModel.kt`: `messages`)

## Symptom
1. Android. Import a zip that has some oversized files in it. Two messages follow each other: the import's counts,
   then "N files were too large".
2. While the first snackbar is showing, rotate the device.
3. The snackbar is gone, and the second one never appears. Same for a "Could not save" or "Export failed" that was
   waiting behind another message: gone without being shown.

## Cause
The messages leave the view model's channel as soon as the composition receives them, and from then on they are held
in composition state that a recreation throws away.
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:486-496`:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val queue = remember { mutableStateListOf<IndexedValue<CampfireViewModel.Message>>() }
LaunchedEffect(viewModel) {
    var count = 0
    viewModel.messages.collect { queue += IndexedValue(count++, it) }
}
```

`viewModel.messages` is `_messages.receiveAsFlow()` (`CampfireViewModel.kt:650-651`), so each message is delivered to
one collector only. The old composition has already moved all of them into its `queue` (and removes the head only
after `showSnackbar` returns, `:517-522`). Android recreates the activity on rotation (see 42), which disposes the
composition and both `remember`s. The new composition's collector only gets messages sent after it started.

## Fix
Keep the queue in the view model and let the screen acknowledge a message once it has been shown. The composition
then only ever reads the head.

1. `CampfireViewModel.kt`, replace the channel (`:646-651`):

   ```kotlin
   /**
    * Messages waiting for the snackbar, oldest first, each with a number of its own so that two identical results in
    * a row are still two messages. Held here rather than by the screen that shows them: Android recreates that screen
    * on every rotation, and a message it had already taken would go with it unshown. A message leaves the queue once
    * it has been shown, see [onMessageShown].
    */
   private val _messageQueue = MutableStateFlow(emptyList<IndexedValue<Message>>())
   val messageQueue: StateFlow<List<IndexedValue<Message>>> = _messageQueue.asStateFlow()
   private var messageCount = 0

   private fun sendMessage(message: Message) = _messageQueue.update { it + IndexedValue(messageCount++, message) }

   /** Called by the snackbar host once [message] has been on screen for its whole duration, or dismissed. */
   fun onMessageShown(message: IndexedValue<Message>) = _messageQueue.update { queue -> queue.filterNot { it.index == message.index } }
   ```

   Replace every `_messages.send(…)` / `_messages.trySend(…)` with `sendMessage(…)`. That is 17 places:
   `grep -n '_messages\.' CampfireViewModel.kt`. They are all in suspend or plain functions on the main thread, and
   `sendMessage` is not suspending, so `send` loses nothing: the channel's back pressure was never wanted there
   anyway. Remove `messages`.

2. `CampfireApp.kt`, `Messages` (`:481-529`):

   ```kotlin
   val snackbarHostState = remember { SnackbarHostState() }
   val queue by viewModel.messageQueue.collectAsStateWithLifecycle()
   val head = queue.firstOrNull()
   val text = when (val current = head?.value) { ... unchanged ... }
   LaunchedEffect(head?.index) {
       if (head != null && text != null) {
           snackbarHostState.showSnackbar(text)
           viewModel.onMessageShown(head)
       }
   }
   ```

   Keep and adapt the comment about the numbering ("two failed exports are the same object…"), since the index is
   what keys the effect.

   A message that was on screen during the rotation is shown again, from the start, by the new composition. That is
   the right outcome: it was cut short.

3. Check the other readers of `messages` before removing it (`grep -rn 'viewModel.messages\|\.messages\b'
   presentation/src`). If a platform shell collects it, move that reader to `messageQueue` the same way.

4. Do **not**:
   - collect `messages` with `collectAsStateWithLifecycle` or `rememberSaveable` the list. A `Message` is not
     `Parcelable`/`Saveable` (it carries an `ImportResult`), and the view model already outlives the recreation.
   - make the channel a `SharedFlow` with replay. A replayed message would be shown again after every recreation,
     even when it has already been shown in full.

## Tests
None (UI is untested).

## Verify
1. Android (`.debug` build): import a zip containing one file over the import size limit (see `ImportBudget` for the
   size) and a few songs. While the first snackbar shows, rotate. The first snackbar shows again, then the "too
   large" one.
2. Import two archives right after each other: pick one, and as soon as its import is running open a `.cho` with
   Campfire from a file manager (it queues behind). Two result snackbars queue up. Rotate while the first one shows:
   it shows again, then the second one.
3. Desktop and web: messages show as before, one after another, with identical ones shown twice.
4. The message about a link that could not be opened (desktop) still shows the address.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet: "the snackbars for import and export results … (queued with a
running number, because two identical results in a row are the same object and an effect keyed on the message alone
would never show the second)" becomes "… (queued in the view model with a running number, since two identical results
in a row are the same object and an effect keyed on the message alone would never show the second, and since the
Android activity is recreated on every rotation, and a queue held by the composition went with it, unshown; a message
leaves the queue once it has been shown, `onMessageShown`)".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing, but it touches every `_messages` line of `CampfireViewModel.kt`, so land it after 10–33 (or before, all
together). 42 and 12 edit other parts of `CampfireApp.kt`.
