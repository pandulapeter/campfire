# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The deterministic half of the add-language skill: everything about a new app language that a script can decide, so
that the model doing the translation only has to translate.

    python3 .claude/skills/add-language/scripts/language.py preflight <code> --sample "<alphabet and punctuation>"
    python3 .claude/skills/add-language/scripts/language.py split <dir>
    python3 .claude/skills/add-language/scripts/language.py join <code> <dir>
    python3 .claude/skills/add-language/scripts/language.py strings <code>
    python3 .claude/skills/add-language/scripts/language.py wiring <code> <EnglishName>

`preflight` exits 2 when the language needs groundwork the skill cannot do (another script, right to left, a plural
system the localization plugin cannot pick, a code the platforms disagree on). `strings` and `wiring` exit 1 on an
error. Standard library only: the bundled fonts' character maps are read directly.
"""

import re
import struct
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
RESOURCES = ROOT / "presentation/src/commonMain/composeResources"
ENGLISH = RESOURCES / "values/strings.xml"
FONTS = sorted((RESOURCES / "font").glob("*.ttf"))

# The same list as :chordpro's RightToLeftLanguages, which folds the retired iw and ji before asking; keep them alike.
RIGHT_TO_LEFT = {"ar", "arc", "ckb", "dv", "fa", "he", "iw", "ji", "ks", "ps", "sd", "syr", "ug", "ur", "yi"}

# Codes the platforms report differently: older JDKs and every Android report the legacy one as Locale.language, and
# AppLocale.findByCode matches the code exactly, so "System" would never find the strings on some platforms.
LEGACY_CODES = {"id": "in", "he": "iw", "yi": "ji", "in": "id", "iw": "he", "ji": "yi"}

# The generated LocalizedStrings.getPlural picks `one` for 1 and `other` for every other count (`zero`, `two`, and
# `many` only if the item exists, `many` then for every count above 2; `few` never). A language whose counts 2 to 10
# need more than one form cannot be written with that.
PLURALS_BLOCKED = {
    "ar", "be", "br", "bs", "cs", "cy", "ga", "gd", "gv", "he", "hr", "iu", "kw", "lt", "lv", "mt", "pl", "ru", "se",
    "sh", "sk", "sl", "sma", "smi", "smj", "smn", "sms", "sr", "tl", "fil", "uk",
}
PLURALS_NOTES = {
    "fr": "0 takes the singular in French: give a `zero` item to each plural that can show 0 (see values-fr).",
    "pt": "Portuguese (pt-PT) is fine; Brazilian 0 takes the singular, given `other` here.",
    "ro": "20 and up need \"de\" (\"20 de cântece\"); write `other` in the 2-19 form, and 20+ reads without it.",
    "is": "21, 31, ... take the singular in Icelandic; the plugin gives them `other`.",
    "mk": "21, 31, ... take the singular in Macedonian; the plugin gives them `other`.",
    "hy": "0 takes the singular in Armenian: give a `zero` item to each plural that can show 0 (see values-fr).",
}
PLURALS_FINE = {
    "af", "az", "bg", "ca", "da", "de", "el", "en", "es", "et", "eu", "fi", "fo", "fy", "gl", "hu", "it", "ka", "kk",
    "ky", "lb", "ms", "nb", "nl", "nn", "no", "sq", "sv", "sw", "tr", "uz", "vi",
} | set(PLURALS_NOTES)
# Languages whose 0 takes the singular. getPlural reads a `zero` item for 0 when there is one, so these add it to the
# plurals that can show 0 (values-fr has them), each on a line of its own that the English lacks.
ZERO_IS_SINGULAR = {"fr", "hy"}

CASING_NOTES = {
    "tr": "Kotlin's lowercase()/titlecase() are locale-insensitive: a scale name or section label starting with i/ı/İ "
          "is cased the English way (standaloneScaleName, SectionNumbering). Avoid such leading letters where a "
          "synonym exists and check the Scales tab and section headings in the app.",
    "az": "Same dotted/dotless i casing caveat as Turkish (standaloneScaleName, SectionNumbering).",
}

SPECIFIER = re.compile(r"%\d+\$[sd]|\{[a-z_]+\}|\\n|</?[biu]>")


def font_map(path):
    """The code points a TrueType file's `cmap` maps (formats 4 and 12, which is all the bundled fonts use)."""
    data = path.read_bytes()
    count = struct.unpack(">H", data[4:6])[0]
    tables = {data[12 + 16 * i:16 + 16 * i].decode("latin-1"): struct.unpack(">II", data[20 + 16 * i:28 + 16 * i]) for i in range(count)}
    offset = tables["cmap"][0]
    points = set()
    for i in range(struct.unpack(">H", data[offset + 2:offset + 4])[0]):
        _, _, sub = struct.unpack(">HHI", data[offset + 4 + 8 * i:offset + 12 + 8 * i])
        start = offset + sub
        kind = struct.unpack(">H", data[start:start + 2])[0]
        if kind == 4:
            segments = struct.unpack(">H", data[start + 6:start + 8])[0] // 2
            ends = struct.unpack(f">{segments}H", data[start + 14:start + 14 + 2 * segments])
            starts = struct.unpack(f">{segments}H", data[start + 16 + 2 * segments:start + 16 + 4 * segments])
            for first, last in zip(starts, ends):
                points.update(range(first, last + 1))
        elif kind == 12:
            for group in range(struct.unpack(">I", data[start + 12:start + 16])[0]):
                first, last, _ = struct.unpack(">III", data[start + 16 + 12 * group:start + 28 + 12 * group])
                points.update(range(first, last + 1))
    points.discard(0xFFFF)
    return points


def missing_glyphs(text):
    """Characters of [text] some bundled font cannot draw, by font. The app never falls back to a system font."""
    characters = {c for c in text if not c.isspace() and unicodedata.category(c)[0] != "C"}
    return {path.name: sorted(c for c in characters if ord(c) not in font_map(path)) for path in FONTS if any(ord(c) not in font_map(path) for c in characters)}


def right_to_left(text):
    return sorted({c for c in text if unicodedata.bidirectional(c) in ("R", "AL")})


def preflight(code, sample):
    blockers, notes = [], []
    if not re.fullmatch(r"[a-z]{2,3}", code):
        blockers.append(f"\"{code}\" is not a plain ISO 639 language code. Regional or script variants (pt-BR, zh-Hans, "
                        "sr-Latn) need groundwork: AppLocale.findByCode matches Locale.current.language alone, and the "
                        "localization plugin's handling of values-xx-rYY folders is untested here.")
    if (RESOURCES / f"values-{code}").exists():
        blockers.append(f"values-{code} already exists: the language is already supported.")
    if code in RIGHT_TO_LEFT or right_to_left(sample):
        blockers.append("The language is written right to left. The app has never been laid out or tested in RTL "
                        "(chord rows over lyrics, the editor, pagers, the PDF), and the bundled fonts have no RTL script.")
    if code in LEGACY_CODES:
        blockers.append(f"Platforms report this language as either \"{code}\" or \"{LEGACY_CODES[code]}\"; "
                        "ApplyLanguagePreference needs to map both before \"System\" can find it everywhere.")
    if code in PLURALS_BLOCKED:
        blockers.append("Its plurals need more than one form for the counts 2 to 10 (few/many), which the generated "
                        "LocalizedStrings.getPlural never picks; the plural selection needs replacing first.")
    elif code in PLURALS_NOTES:
        notes.append("Plurals: " + PLURALS_NOTES[code])
    elif code not in PLURALS_FINE:
        notes.append("Plurals: not in this script's table. Write the word \"song\" for 0-10, 21, 22, 25, and 100 in the "
                     "language: if the counts 2 to 10 need more than one form, stop (groundwork needed).")
    if code in CASING_NOTES:
        notes.append("Casing: " + CASING_NOTES[code])
    missing = missing_glyphs(sample)
    if missing:
        blockers.append("The bundled fonts (Inter, JetBrains Mono: Latin, Greek, Cyrillic only) cannot draw: " +
                        "; ".join(f"{font}: {' '.join(chars)}" for font, chars in missing.items()) +
                        ". The app never uses system fonts, so these would be missing glyphs; a font for the script "
                        "has to be bundled and credited first.")
    for note in notes:
        print("NOTE  " + note)
    for blocker in blockers:
        print("ABORT " + blocker)
    if blockers:
        sys.exit(2)
    print("OK    No fundamental blocker found.")


def chunk_ranges(lines):
    """Line ranges (1-based, inclusive) of English groups, cut at group comments into about 250 lines each."""
    first = next(i for i, line in enumerate(lines, 1) if line.startswith("    <!--"))
    last = max(i for i, line in enumerate(lines, 1) if line.strip() == "</resources>") - 1
    starts = [i for i, line in enumerate(lines, 1) if line.startswith("    <!--") and first <= i <= last]
    ranges, start = [], first
    for cut in starts:
        # A chunk starts at the blank line above its group comment, so none ends in a blank line a writer would drop.
        cut -= 1 if not lines[cut - 2].strip() else 0
        if cut - start >= 250:
            ranges.append((start, cut - 1))
            start = cut
    ranges.append((start, last))
    return ranges, first, last


def split(directory):
    lines = ENGLISH.read_text().split("\n")
    ranges, _, _ = chunk_ranges(lines)
    out = Path(directory)
    out.mkdir(parents=True, exist_ok=True)
    for number, (start, end) in enumerate(ranges, 1):
        (out / f"en_{number}.xml").write_text("\n".join(lines[start - 1:end]) + "\n")
        print(f"en_{number}.xml  lines {start}-{end}  ->  write the translation to {out / f'xx_{number}.xml'}")


def join(code, directory):
    lines = ENGLISH.read_text().split("\n")
    ranges, first, last = chunk_ranges(lines)
    parts = []
    for number in range(1, len(ranges) + 1):
        part = (Path(directory) / f"xx_{number}.xml").read_text().rstrip("\n").split("\n")
        expected = ranges[number - 1][1] - ranges[number - 1][0] + 1
        if len(part) != expected:
            sys.exit(f"xx_{number}.xml has {len(part)} lines, en_{number}.xml {expected}.")
        parts += part
    target = RESOURCES / f"values-{code}/strings.xml"
    target.parent.mkdir(exist_ok=True)
    target.write_text("\n".join(lines[:first - 1] + parts + lines[last:]))
    print(f"Wrote {target.relative_to(ROOT)}")


def entries(path):
    values = {}
    for element in ET.parse(path).getroot():
        if element.tag == "string":
            values[element.get("name")] = element.text or ""
        elif element.tag == "plurals":
            for item in element:
                values[f"{element.get('name')}:{item.get('quantity')}"] = item.text or ""
    return values


def check_strings(code):
    target = RESOURCES / f"values-{code}/strings.xml"
    english, translated = entries(ENGLISH), entries(target)
    zeros = {key: text for key, text in translated.items() if key.endswith(":zero")}
    if code in ZERO_IS_SINGULAR:
        translated = {key: text for key, text in translated.items() if key not in zeros}
    errors, warnings = [], []
    if list(english) != list(translated):
        missing = [k for k in english if k not in translated]
        extra = [k for k in translated if k not in english]
        errors.append(f"Keys differ from English (missing {missing[:10]}, extra {extra[:10]}, or the order changed).")
    english_comments = re.findall(r"<!--.*?-->", ENGLISH.read_text(), re.S)
    if english_comments != re.findall(r"<!--.*?-->", target.read_text(), re.S):
        errors.append("The XML comments differ from English; they stay verbatim, in English.")
    for key, text in translated.items():
        source = english.get(key, "")
        if sorted(SPECIFIER.findall(source)) != sorted(SPECIFIER.findall(text)):
            message = f"{key}: placeholders {SPECIFIER.findall(source)} became {SPECIFIER.findall(text)}"
            (warnings if key.endswith(":one") and not SPECIFIER.findall(text) else errors).append(message)
        if ":" in key and key.split(":")[1] not in ("one", "other"):
            errors.append(f"{key}: only `one` and `other` items; the plugin never picks the others correctly.")
        if text == source and len(source) > 12:
            warnings.append(f"{key}: identical to English ({source[:50]!r}); fine only for names and symbols.")
        if right_to_left(text):
            errors.append(f"{key}: right-to-left characters.")
    missing = missing_glyphs("".join(translated.values()))
    if missing:
        errors.append("Characters no bundled font has: " + "; ".join(f"{f}: {' '.join(c)}" for f, c in missing.items()))
    for key, text in zeros.items() if code in ZERO_IS_SINGULAR else ():
        other = english.get(key.replace(":zero", ":other"))
        if other is None:
            errors.append(f"{key}: a `zero` item in a key that is not a plural.")
        elif not set(SPECIFIER.findall(text)) <= set(SPECIFIER.findall(other)):
            errors.append(f"{key}: placeholders {SPECIFIER.findall(text)} that `other` lacks.")
    zero_lines = len(zeros) if code in ZERO_IS_SINGULAR else 0
    if ENGLISH.read_text().count("\n") != target.read_text().count("\n") - zero_lines:
        errors.append("Line count differs from English: the file must mirror it line for line.")
    report(errors, warnings)


def check_wiring(code, english_name):
    enum = english_name.upper().replace(" ", "_")
    key = "settings_user_interface_language_" + english_name.lower().replace(" ", "_")
    strings_object = "Strings" + code.capitalize()
    presentation = "presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/preferences"
    ios = "app/ios/iosApp"
    checks = [
        ("data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/preferences/AppLanguage.kt", rf'\b{enum}\("{code}"\),'),
        (f"{presentation}/AppLanguageNames.kt", rf"import com\.pandulapeter\.campfire\.presentation\.resources\.{key}\n"),
        (f"{presentation}/AppLanguageNames.kt", rf"AppLanguage\.{enum} -> Res\.string\.{key}\n"),
        ("tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt", rf"import com\.pandulapeter\.campfire\.presentation\.localization\.{strings_object}\n"),
        ("tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt", rf"AppLanguage\.{enum} -> {strings_object}\.strings\[key\]"),
        (f"{ios}/iosApp/Info.plist", rf"<key>CFBundleLocalizations</key>\s*<array>(\s*<string>[a-z-]+</string>)*\s*<string>{code}</string>"),
        (f"{ios}/ShareExtension/Info.plist", rf"<key>CFBundleLocalizations</key>\s*<array>(\s*<string>[a-z-]+</string>)*\s*<string>{code}</string>"),
        (f"{ios}/iosApp/{code}.lproj/InfoPlist.strings", r'"NSMicrophoneUsageDescription" = ".+";'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf'name = {code}; path = {code}\.lproj/Localizable\.strings;'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf'name = {code}; path = {code}\.lproj/InfoPlist\.strings;'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf"knownRegions = \([^)]*\n\t\t\t\t{code},"),
        (f"{ios}/ShareExtension/en.lproj/Localizable.strings", rf"{code}\.lproj"),
        ("app/web/src/wasmJsMain/resources/index.html", rf"\n            {code}: \{{"),
        ("app/web/src/wasmJsMain/resources/index.html", rf"/\^\([a-z|]*\b{code}\b[a-z|]*\)\\b/i"),
        ("CLAUDE.md", rf"values\[[^\]]*-{code}\b[^\]]*\]/strings\.xml"),
        ("presentation/CLAUDE.md", english_name),
        ("app/web/CLAUDE.md", rf"navigator\.language` \([^)]*{english_name}"),
        ("app/ios/CLAUDE.md", rf"`Localizable\.strings` \([^)]*`{code}`"),
        ("app/ios/CLAUDE.md", rf"`CFBundleLocalizations` \([^)]*`{code}`"),
    ]
    stores = sorted(p.name for p in (ROOT / "documentation/store-listings").iterdir() if p.is_dir())
    checks += [(f"documentation/store-listings/{store}/en.md", rf"\b{english_name}\b[^\n]*, switchable inside the app") for store in stores]
    checks += [(f"documentation/store-listings/{store}/{code}.md", r"(?m)^## ") for store in stores]
    checks += [("app/desktop/AppxManifest.xml", rf'<Resource Language="{code}-[a-z]+" />')]
    checks += [(str(p.relative_to(ROOT)), rf'<string name="{key}">[^<]+</string>') for p in sorted(RESOURCES.glob("values*/strings.xml"))]
    errors = []
    for relative, pattern in checks:
        path = ROOT / relative
        if not path.exists():
            errors.append(f"{relative}: missing file")
        elif not re.search(pattern, path.read_text()):
            errors.append(f"{relative}: nothing matches {pattern}")
    pbxproj = (ROOT / f"{ios}/iosApp.xcodeproj/project.pbxproj").read_text()
    if pbxproj.count(f"/* {code} */") != 4:
        errors.append(f"project.pbxproj: expected 4 `/* {code} */` (two file references, two variant group children), "
                      f"found {pbxproj.count(f'/* {code} */')}")
    shares = lambda path: set(re.findall(r'^"(\w+)" =', path.read_text(), re.M))
    english_share = ROOT / f"{ios}/ShareExtension/en.lproj/Localizable.strings"
    new_share = ROOT / f"{ios}/ShareExtension/{code}.lproj/Localizable.strings"
    if not new_share.exists() or shares(english_share) != shares(new_share):
        errors.append(f"{new_share.relative_to(ROOT)}: must hold exactly en.lproj's keys")
    generated = ROOT / f"presentation/build/generated/compose/resourceGenerator/kotlin/commonCustomResClass/com/pandulapeter/campfire/presentation/localization/{strings_object}.kt"
    if not generated.exists():
        errors.append(f"{strings_object}.kt is not generated yet: run ./gradlew :presentation:generateTranslateFile --rerun-tasks")
    listings = subprocess.run([sys.executable, str(ROOT / ".github/scripts/check_store_listings.py")], capture_output=True, text=True)
    errors += listings.stdout.splitlines()
    report(errors, [])


def report(errors, warnings):
    for warning in warnings:
        print("WARN  " + warning)
    for error in errors:
        print("ERROR " + error)
    if errors:
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    if arguments[:1] == ["preflight"] and len(arguments) == 4 and arguments[2] == "--sample":
        preflight(arguments[1], arguments[3])
    elif arguments[:1] == ["split"] and len(arguments) == 2:
        split(arguments[1])
    elif arguments[:1] == ["join"] and len(arguments) == 3:
        join(arguments[1], arguments[2])
    elif arguments[:1] == ["strings"] and len(arguments) == 2:
        check_strings(arguments[1])
    elif arguments[:1] == ["wiring"] and len(arguments) == 3:
        check_wiring(arguments[1], arguments[2])
    else:
        sys.exit(__doc__)
