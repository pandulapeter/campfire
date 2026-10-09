# Mark the app's section titles and sheet titles as headings, so a screen reader can jump between them

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all (screen readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SettingsSectionTitle.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SectionHeader.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionTitle.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/SectionTitle.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CampfireBottomSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (`## Accessibility`, started by plan 30)

Paths below are relative to `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

Nothing in `:presentation` calls `heading()` (`grep -rn "heading()" presentation/src` is empty at b5c8ed3b5), so
TalkBack's and VoiceOver's "headings" navigation finds nothing anywhere: not the settings groups, not the Songs list's
letter and artist headers, not a song's Verse / Chorus pills, not a sheet's title. A screen reader user scrolling a
long song or a long library has to swipe through every line. The section titles, verified:

- `components/SettingsSectionTitle.kt:33` — a bare `Text` (Sort sheet, filters, export options, checklist order):
  ```kotlin
  ) = Text(
      modifier = modifier.fillMaxWidth().padding(contentPadding),
      text = text,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
  )
  ```
- `components/SectionHeader.kt:191` — the list screens' section header. Its name `Text` sits inside a `Row` that is
  `clickable(..., role = Role.Button, onClick = onClick)` when the header scrolls to its section, otherwise plain.
- `screens/songDetails/SectionTitle.kt:45` — a section's name inside `SectionHeaderPill`, whose `Surface(onClick =
  toggle.onToggled, …)` merges it into one clickable node when the section folds.
- `screens/importReport/SectionTitle.kt:34` — the import screen's list headings.
- `dialogs/CampfireBottomSheet.kt:478` — `SheetHeader`'s title `Text` (`text = title`).

`SettingsSubsection`'s title is a control's label rather than a heading and stays as it is; the settings tabs are tabs.

## Fix

Add `Modifier.semantics { heading() }` (`androidx.compose.ui.semantics.heading`, present in Compose 1.12.1) to the
title `Text` itself in each of the five places — not to the row around it. That keeps one node where there is one
today: `SemanticsProperties.Heading` is an `AccessibilityKey` with the default merge policy (`parentValue ?: childValue`),
so inside a clickable row (`SectionHeader` with an `onClick`, a foldable `SectionHeaderPill`) it is merged into the
clickable node, which is then read as "Chorus, heading, button"; where nothing merges it, the `Text` node is the heading.

- `SettingsSectionTitle`: `modifier = modifier.fillMaxWidth().padding(contentPadding).semantics { heading() }`.
- `SectionHeader`: on the name `Text` (`text = text`), `modifier = Modifier.semantics { heading() }`; not on the
  subtitle.
- `songDetails/SectionTitle`: on the name `Text`, chained after its existing `if (isChevronAtEnd) Modifier.weight(1f)
  else Modifier`. An unnamed section (`UNNAMED_SECTION_HEADER`) has nothing to read, so only add it when `header !=
  UNNAMED_SECTION_HEADER`.
- `importReport/SectionTitle`: on the `Text(modifier = Modifier.weight(1f), …)`.
- `SheetHeader`: on the title `Text`.

The pinned copies of `SectionHeader` (`screens/songs/PushedSongSectionHeader.kt:43`,
`screens/setlists/PushedSetlistHeader.kt:54`) are already `clearAndSetSemantics {}`, so only the list's own header
becomes a heading; leave them as they are.

Add to `ui/CLAUDE.md`'s `## Accessibility`: "A section title is a heading: `heading()` on the title's own `Text`, so it
merges into a clickable header rather than adding a node."

## Tests

None: semantics on Composables only.

## Manual check

- Android, TalkBack, reading controls set to Headings: on Songs (sorted by artist) swipe down/up jumps from header to
  header; on a song's page from Verse to Chorus; in Settings → Song display between the groups; in the Sort sheet
  between its titles; a sheet's title is reached as a heading.
- iOS, VoiceOver rotor → Headings: the same.
- A foldable section header is still one node, read as its name, "heading", and that it can be activated.
