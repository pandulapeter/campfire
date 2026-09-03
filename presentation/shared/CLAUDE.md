# :presentation:shared

All UI logic and Compose components shared between Android and desktop. Depends only on `:domain:api`. Koin wiring in `Module.kt` (`presentationModule`) — `CampfireViewModel` as a `single`.

Two-object state pattern:

- `CampfireViewModel` — platform-agnostic, no Android/lifecycle types. Exposes `Flow`s derived from `GetScreenDataUseCase` plus UI state (`query`, `selectedNavigationDestination`, `visibleDialog`, `selectedSong`), and `suspend` intent handlers that take the current value as a parameter and delegate to `Save*` use cases.
- `CampfireViewModelStateHolder` — created in a `@Composable` via `fromViewModel(...)`; collects each flow into Compose `State`, owns list/scaffold/reorderable state, and wraps the suspend handlers in `coroutineScope.launch`. Screens read `stateHolder.x.value` and call its non-suspend methods.

Packages:
- `ui/catalogue/components/` — reusable Compose pieces: `CampfireScaffold`, app bar, navigation rail/bottom bar, item types, `SongDetails`, `SongLyrics`.
- `ui/catalogue/resources/` — `CampfireStrings` (sealed class with `English`/`Hungarian` subclasses — add new strings as abstract members, then implement in both), `CampfireIcons`, `UiConstants`.
- `ui/catalogue/theme/` — `CampfireColors`.
- `ui/screenComponents/` — per-screen content and control lists, composed differently by each platform shell.

**Raw song format** (parsed in `SongLyrics.kt`): `{c: Verse 1}` lines become section headers (title matched against known section names for localization), `[Am]` markers become chords rendered above the syllable they precede, everything else is lyrics.
