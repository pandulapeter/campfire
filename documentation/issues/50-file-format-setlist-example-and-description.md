# 50 · The file format page's setlist example points at a file name the app never gives, and leaves out the description

(Two reviewers found this; merged into one plan.)

**Severity:** docs (all platforms. A setlist written by hand from the example shows its song as missing) · **Area:**
`documentation/file-format.md`

## Symptom
The setlist example names `"oasis_wonderwall.cho"`, but the Wonderwall song shown a few lines above it is stored as
`oasis-wonderwall.cho`, by the naming rule the same page states. A hand-written setlist copied from the example points
at a file that does not exist. The page also never mentions a setlist's `description`.

## Cause
`documentation/file-format.md:49`:

```json
{ "title": "Friday gig", "priority": 3, "songs": [ { "file": "oasis_wonderwall.cho", "transposition": 2 } ] }
```

The artist and title halves are joined with `LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR = "-"`
(`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt:77`), as the page's own
`green_day-good_riddance_time_of_your_life.cho` shows. `SetlistDocument`
(`data/source/local/implementation/src/commonMain/kotlin/…/model/SetlistDocument.kt:19-25`) has `title`,
`description`, `priority`, `isArchived` and `songs`; `:52-53` names only `isArchived` beyond the example.

The second report's third point (the "`meta` for anything else" sentence) belongs to 21's plan and is not part of this one.

## Fix
`documentation/file-format.md`:

1. `:49`, the example becomes:

   ```json
   { "title": "Friday gig", "priority": 3, "songs": [ { "file": "oasis-wonderwall.cho", "transposition": 2 } ] }
   ```

2. `:52-53`, replace "An archived setlist carries `"isArchived": true` as well; every field is defaulted, so a
   hand-written file can leave out anything it has nothing to say about." with "A setlist can also carry a
   `"description"`, the sentence shown under its title, and an archived one carries `"isArchived": true`. A song is
   named by its file name exactly as it is in `songs/`. Every field is defaulted, so a hand-written file can leave out
   anything it has nothing to say about."
3. While there, `:58-60` has two lines indented by two spaces in the middle of a paragraph ("  to lowercase unaccented
   words…", "  `катюша.cho` — as …"); remove the leading spaces and rewrap the paragraph at the page's width (text
   unchanged).

## Tests
None (docs).

## Verify
Render the page (GitHub preview) and read the Setlists and File names sections back.

## Docs
This is the doc change.

## Touches
- `documentation/file-format.md`

## Depends on
None. If 21's plan edits `:31-33` of the same file, run them one after another.
