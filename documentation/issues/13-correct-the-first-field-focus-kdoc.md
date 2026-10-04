# Correct rememberFirstFieldFocusRequester's KDoc, which says the field is always the first thing under the title

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt

**Challenged:** sound

## Problem
`rememberFirstFieldFocusRequester`'s KDoc (`Dialogs.kt` ~869-874 at 800ebde0b) says:

> A dialog that is there to be typed into puts the caret in its first field rather than asking for one more tap, which
> on a touch platform is also what brings the keyboard up with it - and every such dialog here holds that field as the
> first thing under the title, so there is only ever the one field to open on.

`DeleteLibraryDialog` (same file, ~800-860) uses it for its confirmation field, which comes after one or two paragraphs
(`settings_library_delete_confirmation`, and the sync warning while connected), so "the first thing under the title" is
not true; what is true is that it is the dialog's first (and only) field.

## Fix
Replace that clause with: "— and every such dialog here has one field that obviously comes first (its first field, or
its only one, under whatever the dialog has to say before it), so there is only ever the one field to open on." Keep
the rest of the KDoc (the exceptions: the song metadata form, an edited setlist, the assignment sheets) as is.

## Tests
None: documentation only.

## Manual check
None.
