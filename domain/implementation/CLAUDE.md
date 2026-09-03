# :domain:implementation

Implements `:domain:api` on top of `:data:repository:api`. Koin wiring in `Module.kt` (`domainModule`), all use cases as `factory`. Impl classes are public with an `internal constructor`.

Two carry the real logic:

- `GetScreenDataUseCaseImpl` — combines all six repository flows into one `Flow<DataState<ScreenData>>`. Owns the whole visible-song pipeline: keep enabled + selected databases, flatten, de-duplicate by id, drop non-public songs, apply the downloaded/explicit/chords filters, then sort by title or artist (via `NormalizeTextUseCase`, so accents are ignored). Holds a `cache` so a `Failure`/`Loading` state can still carry the last good data.
- `TransposeRawSongDetailsUseCaseImpl` — rewrites `[Chord]` markers by semitone offset. Handles the bass note after `/`, `#`/`b` accidentals, and German `H`; output is always sharp-spelled.

`LoadScreenDataUseCaseImpl` fans out the initial/forced load across repositories in parallel, resolving enabled database URLs from preferences before loading songs.
