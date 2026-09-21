# 58 · Every song row starts a flow collection, a lifecycle observer and an animation coroutine it never uses, which is what a fling through a large library pays for

**Severity:** performance (all platforms; felt when flinging a library of thousands of songs on a low-end phone) · **Area:** `:presentation` (`components/SongActions.kt`, `components/ListItems.kt`, `screens/setlists/SetlistsScreen.kt`; the `com.hyperether.localization` plugin's generated `formatString`)

## Symptom
Fling the Songs screen with a library of a few thousand songs on a 2 GB Android phone (or scroll it in the web
build): frames are dropped while rows enter the screen. Nothing is wrong on screen. What decides whether such a fling
holds its frame rate is how much a row costs to compose for the first time, and a row currently sets up four things
per composition that it has no use for on that screen.

## Cause
Four separate per-row costs, all on the path of `SongsScreen.kt:338-376` (`SongListItem` + `SongActionsButton`).

1. **A lifecycle-aware collection per row, read only by an open menu.** `components/SongActions.kt:115`:

   ```kotlin
   val filePicker = LocalFilePicker.current
   val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
   ActionsMenu(modifier = modifier, state = state) { dismiss ->
       …
       icon = painterResource(if (song.fileName in songFileNamesInSetlists) Res.drawable.ic_setlists else Res.drawable.ic_setlists_outline),
   ```

   `collectAsStateWithLifecycle` is a `produceState`: a coroutine, a `repeatOnLifecycle` and with it an observer on
   the lifecycle, per row that enters composition. The value is only read inside the dropdown's content, and
   `OverflowMenu` (`components/OverflowMenu.kt:48-54`) hands that to `DropdownMenu`, which composes it only while the
   menu is expanded — for one row at a time, and usually for none.

2. **An animation coroutine per row for a drag the Songs screen does not have.** `components/ListItems.kt:141-145`:

   ```kotlin
   val dragProgress by animateFloatAsState(
       if (isBeingDragged) 1f else 0f,
       MaterialTheme.motionScheme.defaultEffectsSpec(),
   )
   val containerColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHigh, dragProgress)
   ```

   `animateFloatAsState` remembers an `Animatable` and a `Channel`, runs a `SideEffect` on every recomposition and
   keeps a `LaunchedEffect` coroutine alive for as long as the row is composed (`AnimateAsState.kt:427-429` in Compose
   1.12.0). Only `SetlistsScreen.kt:414` ever passes `isBeingDragged`; on the Songs screen it is the default `false`
   for the life of every row.

3. **Two `Regex` compilations per row that names a key.** `components/ListItems.kt:160`,
   `description = stringResource(Res.string.songs_key, key)`. The generated
   `presentation/build/generated/compose/resourceGenerator/kotlin/commonCustomResClass/…/localization/LocalizedStrings.kt`
   sends every formatted string through `formatString` (lines 22-63), which is emitted verbatim from a template in
   the plugin (`LocalizationPlugin.kt:608-650` of `compose-multiplatform-localize-plugin` 2.0.1) and builds both of
   its patterns inside the function:

   ```kotlin
   private fun formatString(template: String, vararg args: Any): String {
       if (args.isEmpty()) return template
       …
       val positionalPattern = Regex("%(\\d+)\\$([-#+ 0,(]*)\\d*(\\.\\d+)?([dfsioxXeEfFgGaAcspn])")
       …
       val sequentialPattern = Regex("%([-#+ 0,(]*)\\d*(\\.\\d+)?([dfsioxXeEfFgGaAcspn])")
   ```

   That is two `Pattern.compile`s (two `new RegExp`s on the web) plus two replace passes for every row with a key,
   on every composition of it, for a content description that only a screen reader ever asks for. The file is
   generated, so it cannot be edited; the patterns can only be avoided, not hoisted.

4. **An `AnimatedContent` per row** (`components/ListItems.kt:205-233`) for the note next to the artist.

## Fix
Items 1 and 2 are pure waste and go away with no visible change. Item 3 is removed by plan 63, for a reason of its
own. Item 4 stays, deliberately.

1. `components/SongActions.kt`, `SongActionsButton`: move the collection into the menu's content, so that it exists
   only while a menu is open.

   ```kotlin
   val filePicker = LocalFilePicker.current
   ActionsMenu(
       modifier = modifier,
       state = state,
   ) { dismiss ->
       // Collected here rather than by the button: this content is only composed while the menu is open, and the
       // button is in every row of the song list, where a collection of its own is a coroutine and a lifecycle
       // observer per row for a value none of them draws. The state is kept up to date by the view model whether
       // or not anybody collects it, so the menu opens on the right star rather than correcting itself a frame in.
       val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
       …
   ```

   `songFileNamesInSetlists` is `asState(emptySet())`, i.e. `stateIn(viewModelScope, Eagerly)`, and
   `collectAsStateWithLifecycle` starts from `StateFlow.value`, so the first frame of the menu already has the real
   set. Do not replace it with a plain `viewModel.songFileNamesInSetlists.value` read: nothing would then redraw
   the star when a sync run or a rescan changes the setlists under an open menu, and one collection for the one
   menu that is open is not a cost worth that. `SongDetailsScreen.kt:285` uses the same composable and needs no
   change.

2. `components/ListItems.kt`: take the drag tint out of `SongListItem` and give it to the one caller that drags.
   - Replace the parameter `isBeingDragged: Boolean = false` with
     `containerColor: Color = MaterialTheme.colorScheme.surface`, delete the `dragProgress` / `containerColor` locals
     (`:139-145`) and keep `colors = ListItemDefaults.colors(containerColor = containerColor)`. Add to the KDoc:
     `@param containerColor What the row is drawn on, which is only ever something else than the surface while the
     row is being dragged ([draggedListItemContainerColor]).`
   - Add, right after `SongListItem`:

     ```kotlin
     /**
      * The container color of a row that can be dragged: the surface, tinted for as long as the row is off the list.
      *
      * A progress value instead of an animated color, so that the row follows the color scheme immediately while it
      * is animating between the light and the dark theme (a color animation would chase it and trail behind). It is
      * asked for by the list whose rows are dragged rather than worked out by every [SongListItem], because the
      * animation behind it is a coroutine that runs for as long as the row is composed, and the song list has
      * thousands of rows and no drag.
      */
     @OptIn(ExperimentalMaterial3ExpressiveApi::class)
     @Composable
     internal fun draggedListItemContainerColor(isBeingDragged: Boolean): Color {
         val dragProgress by animateFloatAsState(
             if (isBeingDragged) 1f else 0f,
             MaterialTheme.motionScheme.defaultEffectsSpec(),
         )
         return lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHigh, dragProgress)
     }
     ```

     (`Color` is `androidx.compose.ui.graphics.Color`; add the import if the file does not have it yet. Drop
     `ExperimentalMaterial3ExpressiveApi` from `SongListItem`'s own `@OptIn` only if the compiler no longer asks for
     it there.)
   - `screens/setlists/SetlistsScreen.kt:401-417`: replace `isBeingDragged = isBeingDragged,` with
     `containerColor = draggedListItemContainerColor(isBeingDragged),` and import the function. The Songs screen's
     call (`SongsScreen.kt:338`) passes nothing and gets the plain surface, which is what `lerp(…, 0f)` returned.

3. The formatted content description: **no step here — plan 63 does it.** `key` is text out of the user's file, so
   `songs_key` is one of the strings plan 63 moves off the plugin's formatter and onto `textResource(…)`, which reads
   the template with a map lookup and fills it in with one scan of a pattern compiled once for the process rather
   than two compiled per call. That is the whole of what can be done
   on our side short of forking the plugin: `remember`ing the result only helps recompositions, and a fling is first
   compositions; the generated `formatString` is private and regenerated on every build; and
   `stringResource(Res.string.songs_key)` without arguments is not a way around it, because the plugin files a string
   holding a specifier under `formattedStrings` only and the argument-less lookup answers `"???"`. If plan 63 is
   dropped, do its steps 1 and 2 for `songs_key` alone as part of this plan.

4. The `AnimatedContent`: **leave it.** It is the documented behaviour ("whatever stands there is crossfaded rather
   than swapped", `presentation/CLAUDE.md`) and, unlike items 1 and 2, it keeps nothing running while it is idle:
   `Transition.animateTo` only adds its frame loop effect while `targetState != currentState || isRunning ||
   updateChildrenNeeded` (`Transition.kt:1343-1377`). What is left is composition-only cost (a transition, its child
   transition, one layout), and the ways of avoiding it — composing the `AnimatedContent` only once the note has
   changed, seeded through a `MutableTransitionState` with the note the row started with — swap the subtree under a
   row in the frame the crossfade should start, which is exactly the kind of first-frame difference this codebase
   keeps fixing. Revisit only with a profile that shows it.

Ordering: steps 1 and 2 are independent of each other and of plan 63.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run`.
   - Songs screen: open a row's overflow menu for a song that is in a setlist and one that is not — filled and
     outlined star respectively, right from the menu's first frame. On a touch build (Android emulator), long press
     a row: same menu, same star.
   - Song details: the app bar's overflow menu shows the same entries and star.
   - With the menu of a song open, nothing else changed: Escape closes it (desktop), a tap outside closes it.
2. Setlists screen: drag a row by its handle and by a long press — the row lifts (shadow) and tints to
   `surfaceContainerHigh` over the same fade as before, and fades back when dropped. Switch the theme while holding
   a row: the tint follows the scheme without trailing.
3. Songs screen rows look exactly as before (plain surface), including during a theme change.
4. Optional, to see the saving: on Android, Layout Inspector's recomposition counts are unchanged; a method trace of
   a fling no longer shows `produceState` / `repeatOnLifecycle` / `animateFloatAsState` under `SongListItem` and
   `SongActionsButton`.
5. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
None. (`presentation/CLAUDE.md` describes the star and the crossfade, both of which are unchanged.)

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`

## Depends on
Nothing for steps 1 and 2. The third cost is removed by plan 63, which edits `ListItems.kt:160` — schedule the two
one after the other, in either order. Plan 60 edits `SetlistsScreen.kt` as well (the grid's and the fast scroller's
padding, not the rows), so 58 and 60 cannot run side by side either.
