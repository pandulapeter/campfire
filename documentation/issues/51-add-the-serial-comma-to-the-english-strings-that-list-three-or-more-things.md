# Add the serial comma to the English strings that list three or more things

**Kind:** copy  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/values/strings.xml`
**Challenged:** amended — a thirteenth string lacks it, which the grep's one-word item missed: `songs_empty_hint` ("import songs, PDF or Word documents and zip archives", whose middle item holds an "or" of its own). Every quoted string was checked at HEAD.

## Problem

The user's writing preference (their global `CLAUDE.md`) is the Oxford comma in every list of three or more items, UI text
included. Thirteen English strings at 72d48deb7 list three or more things without it (found with
`grep -n -E "[A-Za-z)]+, [^,<]+? (and|or) " strings.xml | grep -v -E ", (and|or) "` and read one by one; the remaining hits
of that search are not lists):

| Key | Now |
|---|---|
| `import_unreadable_documents` (one) | (a scan, a protected PDF or an old Word format) |
| `import_unreadable_documents` (other) | (scans, protected PDFs or an old Word format) |
| `settings_import` | Import songs, documents or zip archives… |
| `settings_read_only_mode_description` | editing, deleting, reordering and transposing are hidden |
| `settings_chords_description` | along with the key, the transposition and the capo |
| `song_details_link_address_hint` | A video, a recording, a tab or any other page about the song |
| `settings_sync_description` | how each song is transposed, capoed and played |
| `settings_sync_preferences_failed` | (transposition, capo and tempo) |
| `print_format_chordpro_song_description` | who can import, edit and transpose the song |
| `print_chords` | Chords, tablature and grids |
| `print_key` | Key, transposition and capo |
| `print_metadata_setlist` | Artists, date and description |
| `songs_empty_hint` | import songs, PDF or Word documents and zip archives, or add the demo songs |

## Fix

In `values/strings.xml` only, add the comma before the last "and" / "or" of each:

- `(a scan, a protected PDF, or an old Word format)` and `(scans, protected PDFs, or an old Word format)`
- `Import songs, documents, or zip archives…`
- `editing, deleting, reordering, and transposing are hidden`
- `along with the key, the transposition, and the capo`
- `A video, a recording, a tab, or any other page about the song`
- `how each song is transposed, capoed, and played`
- `(transposition, capo, and tempo)`
- `who can import, edit, and transpose the song`
- `Chords, tablature, and grids`
- `Key, transposition, and capo`
- `Artists, date, and description`
- `Create a song, import songs, PDF or Word documents, and zip archives, or add the demo songs.`

Hungarian does not put a comma before "és" / "vagy", so `values-hu/strings.xml` is unchanged. Comments in Kotlin files
that quote these phrases (in `ui/print/`, `SongTextMeasurements.kt`, and others) are not UI text and are left alone. Run
the same grep again after the edit and read any new hit, in case another plan of this review has added a string.

## Tests

None: string resources. No test asserts these texts (checked with `grep -rl` for each phrase).

## Manual check

In English: Settings → Features (the chords and read-only descriptions), Settings → Library (Import), the sync section, a
song's Add link sheet (the address hint), and the export screen's options (Chords, tablature, and grids; Key,
transposition, and capo; for a setlist, Artists, date, and description), and the empty library's hint on the Songs
screen each read with the serial comma, and none of them
wraps or is cut short at 360dp where it did not before.
