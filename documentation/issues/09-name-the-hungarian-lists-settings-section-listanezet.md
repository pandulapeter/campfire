# Name the Hungarian Settings section about lists "Listanézet", apart from the Setlists tab's "Listák"

**Kind:** strings · **Severity:** low · **Platforms:** all (Hungarian)
**Files:** `presentation/src/commonMain/composeResources/values-hu/strings.xml`

**Challenged:** sound — `settings_group_lists` is read only by `SongListsSection.kt`; `Listák` stays for `setlists`
and `import_review_setlists`, which are the setlists; tests read strings by key.

**Decision (taken by the user, 2026-10-26): "Listanézet".** **Lane P, last** (lane P owns both `strings.xml` files,
since plan 07 adds strings to them).

## Problem

In Hungarian, the Settings section built by `ui/screens/settings/SongListsSection.kt`
(`SettingsSection(title = stringResource(Res.string.settings_group_lists))`), the first section of the Preferences tab, which
sets what the song and setlist cards say, is titled the same as the Setlists tab and screen:

```xml
<string name="setlists">Listák</string>
...
<string name="settings_group_lists">Listák</string>
```

so a Hungarian reader takes the section for the setlists' settings. English says "Lists" against "Setlists" and is fine.

## Fix

In `values-hu/strings.xml` only: `<string name="settings_group_lists">Listanézet</string>`. English unchanged. Drop this
plan if the value is already something other than "Listák" (the user may have renamed it meanwhile).

## Tests

None can say more than the string; the strings parity check (both files have the key) is untouched.
`./gradlew :presentation:desktopTest` stays green.
