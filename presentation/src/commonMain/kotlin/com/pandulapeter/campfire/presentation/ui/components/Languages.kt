/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_languages_unknown
import com.pandulapeter.campfire.presentation.ui.platform.languageDisplayName

/**
 * What a language is called wherever the app names one: the platform's own name for it, in the language the app is
 * set to rather than the one the system is. [currentLanguage] is Compose state, so the chips and the picker follow
 * the language being switched in the settings without a restart, exactly as the string resources do.
 *
 * A code the platform has no name for at all — one that names no language, or one an old browser has never heard
 * of — is shown in capitals as the file wrote it, which at least says what the file says.
 */
@Composable
internal fun languageLabel(code: String): String = if (code == SongLanguage.UNKNOWN) {
    stringResource(Res.string.songs_languages_unknown)
} else {
    val appLanguageCode = currentLanguage.value.code
    remember(code, appLanguageCode) { languageName(code = code, appLanguageCode = appLanguageCode) ?: code.uppercase() }
}

/**
 * [languageLabel] outside a composition, and null rather than a fallback where the platform cannot name the language
 * at all: the picker offers what can be named and nothing else, which is a question only this can answer.
 *
 * The English name stands in wherever the app's own language has none. CLDR is translated language by language and
 * falls off steeply past the few hundred a reader is likely to have heard of — around a hundred of these codes have
 * no Hungarian name — and a picker that lost a language the moment the app was switched to Hungarian would be worse
 * than one that names a few of them in English.
 */
internal fun languageName(code: String, appLanguageCode: String) = (
    languageDisplayName(code = code, inLocaleCode = appLanguageCode)
        ?: languageDisplayName(code = code, inLocaleCode = FALLBACK_LANGUAGE_CODE)
    )?.replaceFirstChar { it.uppercaseChar() }

/**
 * Every language the picker knows of, named where the platform can name it and in the order they are shown in.
 *
 * Which of them it *lists* is [PickableLanguage.isListed], and the reason for the distinction is the web: a browser's
 * `Intl.DisplayNames` carries the languages a browser is translated into rather than a catalogue of languages, so
 * Chrome can name 133 of the 184 two letter codes and 19 of the 434 three letter ones, where a JVM and Apple's
 * Foundation name nearly all of them. Listing a code the platform has no name for would fill the picker with four
 * hundred rows of capitals; leaving it out entirely would mean a library that already holds a song in Romani could
 * not have a second one filed under it. So an unnamed language is not listed but is still *there*, and a search for
 * its code finds it — see the dialog in `Dialogs.kt`.
 *
 * The languages the library already sings in come first, before the alphabet the rest are in. A song about to be
 * filed under a language is far likelier to be in one of the handful the library already holds than in any of the
 * six hundred it does not, and those few are also the ones whose spelling the user has already settled on.
 *
 * @param alsoOffer The library's own languages: listed whatever the platform can say about them, and listed first.
 * @param normalize Accent and case insensitive text, which both the ordering and the search run on — a reader
 *   looking for Ír should not have to type the accent, and an `Ő` sorts after `Z` without it.
 */
internal fun pickableLanguages(
    appLanguageCode: String,
    alsoOffer: Collection<String>,
    normalize: (String) -> String,
): List<PickableLanguage> {
    val offered = alsoOffer.toSet()
    return (LANGUAGE_CODES + offered.filterNot { it in LANGUAGE_CODES })
        .map { code ->
            PickableLanguage(
                code = code,
                name = languageName(code = code, appLanguageCode = appLanguageCode),
                isInLibrary = code in offered,
                normalize = normalize,
            )
        }
        .sortedWith(compareByDescending<PickableLanguage> { it.isInLibrary }.thenBy { it.sortKey })
}

/** One language as the picker shows it, with the key it is ordered and searched by worked out once. */
internal class PickableLanguage(
    val code: String,
    /** Null where the platform has no name for the language, which on the web is most of them. */
    val name: String?,
    /** Whether some song in the library is already filed under it, which is what puts it at the top of the list. */
    val isInLibrary: Boolean,
    normalize: (String) -> String,
) {

    /** The code stands in for the name the platform does not have, exactly as a chip does. */
    val label = name ?: code.uppercase()

    /** Whether the picker lists the language before anything has been typed into its search field. */
    val isListed = name != null || isInLibrary

    val sortKey = normalize(label)
}

/** What a language is called where the app's own language has no name for it, see [languageName]. */
private const val FALLBACK_LANGUAGE_CODE = "en"

/**
 * The languages the picker offers: every two letter ISO 639-1 code, and every three letter one the standard's later
 * parts add for a language ISO 639-1 never covered — Romani, Cherokee, Tunisian Arabic and some four hundred others.
 * It is the one part of this feature the app does have to carry, since no browser will enumerate languages, but it
 * carries codes and nothing else: the names are asked for at runtime (see [languageName]), and so is the order they
 * are shown in, which is alphabetical by a name that depends on the language the app is set to.
 *
 * Two kinds of code are deliberately not here, and nothing is lost by it, since `:chordpro` reads both as the code
 * that replaced them: the three letter form of a language that also has a two letter one (`eng`, `ger`), and the
 * four two letter codes ISO 639-1 has itself replaced (`in`, `iw`, `ji`, `mo`). Offering either would mean two rows
 * with the same name that no song could be filed under together. Neither are the codes that name no language at all
 * (`und`, `zxx`, `mul`, `mis`): two of them the parser reads as "no language", and the other two say something a
 * song says better by naming the languages it is actually sung in.
 *
 * No platform can name quite all of these, and a language the picker cannot name it does not offer.
 */
internal val LANGUAGE_CODES = listOf(
    "aa", "ab", "ace", "ach", "ada", "ady", "ae", "aeb", "af", "afh", "agq", "ain", "ak", "akk", "akz", "ale", "aln", "alt",
    "am", "an", "ang", "anp", "apw", "ar", "arc", "arn", "aro", "arp", "arq", "ars", "arw", "ary", "arz", "as", "asa", "ase",
    "ast", "av", "avk", "awa", "ay", "az", "ba", "bal", "ban", "bar", "bas", "bax", "bbc", "bbj", "be", "bej", "bem", "ber",
    "bew", "bez", "bfd", "bfq", "bg", "bgc", "bgn", "bh", "bho", "bi", "bik", "bin", "bjn", "bkm", "bla", "blo", "bm", "bn",
    "bo", "bpy", "bqi", "br", "bra", "brh", "brx", "bs", "bss", "bua", "bug", "bum", "byn", "byv", "ca", "cad", "car", "cay",
    "cch", "ccp", "ce", "ceb", "cgg", "ch", "chb", "chg", "chk", "chm", "chn", "cho", "chp", "chr", "chy", "cic", "ckb", "co",
    "cop", "cps", "cr", "crh", "cs", "csb", "cst", "csw", "cu", "cv", "cy", "da", "dak", "dar", "dav", "de", "del", "den",
    "dgr", "din", "dje", "doi", "dsb", "dtp", "dua", "dum", "dv", "dyo", "dyu", "dz", "dzg", "ebu", "ee", "efi", "egl", "egy",
    "eka", "el", "elx", "en", "enm", "eo", "es", "esu", "et", "eu", "ewo", "ext", "fa", "fan", "fat", "ff", "fi", "fil", "fit",
    "fj", "fo", "fon", "fr", "frc", "frm", "fro", "frp", "frr", "frs", "fur", "fy", "ga", "gaa", "gag", "gan", "gay", "gba",
    "gbz", "gd", "gez", "gil", "gl", "glk", "gmh", "gn", "goh", "gom", "gon", "gor", "got", "grb", "grc", "gsw", "gu", "guc",
    "gur", "guz", "gv", "gwi", "ha", "hai", "hak", "haw", "hch", "he", "hi", "hif", "hil", "hit", "hmn", "ho", "hr", "hsb",
    "hsn", "ht", "hu", "hup", "hy", "hz", "ia", "iba", "ibb", "id", "ie", "ig", "ii", "ik", "ilo", "inh", "io", "is", "isc",
    "it", "iu", "izh", "ja", "jam", "jbo", "jgo", "jmc", "jpr", "jrb", "jut", "jv", "ka", "kaa", "kab", "kac", "kaj", "kam",
    "kaw", "kbd", "kbl", "kcg", "kde", "kea", "ken", "kfo", "kg", "kgp", "kha", "kho", "khq", "khw", "ki", "kiu", "kj", "kk",
    "kkj", "kl", "kln", "km", "kmb", "kn", "ko", "koi", "kok", "kos", "kpe", "kr", "krc", "kri", "krj", "krl", "kru", "ks",
    "ksb", "ksf", "ksh", "ku", "kum", "kut", "kv", "kw", "kxv", "ky", "la", "lad", "lag", "lah", "lam", "lb", "lez", "lfn",
    "lg", "li", "lij", "liv", "lkt", "lmo", "ln", "lo", "lol", "loz", "lrc", "lt", "ltg", "lu", "lua", "lui", "lun", "luo",
    "lus", "lut", "luy", "lv", "lzh", "lzz", "mad", "maf", "mag", "mai", "mak", "man", "mas", "mde", "mdf", "mdh", "mdr",
    "men", "mer", "mfe", "mg", "mga", "mgh", "mgo", "mh", "mi", "mic", "mid", "min", "mk", "ml", "mn", "mnc", "mni", "moh",
    "mos", "mr", "mrj", "ms", "mt", "mua", "mus", "mwl", "mwr", "mwv", "my", "mye", "myv", "mzn", "na", "nan", "nap", "naq",
    "nb", "nd", "nds", "ne", "new", "nez", "ng", "nia", "niu", "njo", "nl", "nmg", "nn", "nnh", "nnp", "no", "nog", "non",
    "nov", "nqo", "nr", "nso", "nus", "nv", "nwc", "ny", "nym", "nyn", "nyo", "nzi", "oc", "oj", "om", "or", "os", "osa",
    "ota", "otk", "oui", "pa", "pag", "pal", "pam", "pap", "pau", "pcd", "pcm", "pdc", "pdt", "peo", "pfl", "phn", "pi", "pl",
    "pms", "pnt", "pon", "pqm", "prg", "pro", "ps", "pt", "qu", "quc", "qug", "raj", "rap", "rar", "rej", "rgn", "rhg", "rif",
    "rm", "rn", "ro", "rof", "rom", "rtm", "ru", "rue", "rug", "rup", "rw", "rwk", "sa", "sad", "sah", "sam", "saq", "sas",
    "sat", "saz", "sba", "sbp", "sc", "scn", "sco", "sd", "sdc", "sdh", "se", "see", "seh", "sei", "sel", "ses", "sg", "sga",
    "sgs", "shi", "shn", "shp", "shu", "si", "sid", "sjd", "sje", "sju", "sk", "sl", "sli", "sly", "sm", "sma", "smj", "smn",
    "sms", "sn", "snk", "so", "sog", "sq", "sr", "srn", "srr", "ss", "ssy", "st", "stq", "su", "suk", "sus", "sux", "sv", "sw",
    "swb", "syc", "syr", "szl", "ta", "tcy", "te", "tem", "teo", "ter", "tet", "tg", "th", "ti", "tig", "tiv", "tk", "tkl",
    "tkr", "tl", "tlh", "tli", "tly", "tmh", "tn", "to", "tog", "tok", "tpi", "tr", "tru", "trv", "ts", "tsd", "tsi", "tt",
    "ttt", "tum", "tvl", "tw", "twq", "ty", "tyv", "tzm", "udm", "ug", "uga", "uk", "umb", "ur", "uz", "vai", "ve", "vec",
    "vep", "vi", "vls", "vmf", "vmw", "vo", "vot", "vro", "vun", "wa", "wae", "wal", "war", "was", "wbp", "wo", "wuu", "xal",
    "xh", "xmf", "xnr", "xog", "yao", "yap", "yav", "ybb", "yi", "yo", "yrl", "yue", "za", "zap", "zbl", "zea", "zen", "zgh",
    "zh", "zu", "zun", "zza",
)
