/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * The one piece of data `:chordpro` carries that is not about the ChordPro format: which three letter language codes
 * name a language that also has a two letter one.
 *
 * It is here because the identity of a language is decided here, in [ChordProSyntax.languageCode], and a library
 * where one file says `{meta: language eng}` and the next says `{meta: language en}` has to hold one language rather
 * than two. Which of the two spellings wins is not arbitrary either: the two letter code is the one every platform
 * can translate — asked about `eng` a JVM answers "English" whatever language it is asked in, while `en` it calls
 * "angol" — so folding the long form into the short one is also what gets the language a name the reader can read.
 *
 * A three letter code with no two letter equivalent (`rom`, Romani) is not in here and is kept exactly as the file
 * wrote it: that is the half of ISO 639-2 that ISO 639-1 never covered, and it is the only way those languages can
 * be named at all.
 */
internal object ChordProLanguageCodes {

    /**
     * ISO 639-2 to ISO 639-1, with both of the standard's three letter forms — the terminological (`deu`) and the
     * bibliographic (`ger`) — plus the four two letter codes ISO 639-1 itself has since replaced (`in`, `iw`, `ji`,
     * `mo`), which are still what a JVM writes for Indonesian, Hebrew, Yiddish and Moldavian.
     */
    val twoLetterEquivalents = mapOf(
        "aar" to "aa", "abk" to "ab", "afr" to "af", "aka" to "ak", "alb" to "sq", "amh" to "am", "ara" to "ar", "arg" to "an",
        "arm" to "hy", "asm" to "as", "ava" to "av", "ave" to "ae", "aym" to "ay", "aze" to "az", "bak" to "ba", "bam" to "bm",
        "baq" to "eu", "bel" to "be", "ben" to "bn", "bih" to "bh", "bis" to "bi", "bod" to "bo", "bos" to "bs", "bre" to "br",
        "bul" to "bg", "bur" to "my", "cat" to "ca", "ces" to "cs", "cha" to "ch", "che" to "ce", "chi" to "zh", "chu" to "cu",
        "chv" to "cv", "cor" to "kw", "cos" to "co", "cre" to "cr", "cym" to "cy", "cze" to "cs", "dan" to "da", "deu" to "de",
        "div" to "dv", "dut" to "nl", "dzo" to "dz", "ell" to "el", "eng" to "en", "epo" to "eo", "est" to "et", "eus" to "eu",
        "ewe" to "ee", "fao" to "fo", "fas" to "fa", "fij" to "fj", "fin" to "fi", "fra" to "fr", "fre" to "fr", "fry" to "fy",
        "ful" to "ff", "geo" to "ka", "ger" to "de", "gla" to "gd", "gle" to "ga", "glg" to "gl", "glv" to "gv", "gre" to "el",
        "grn" to "gn", "guj" to "gu", "hat" to "ht", "hau" to "ha", "heb" to "he", "her" to "hz", "hin" to "hi", "hmo" to "ho",
        "hrv" to "hr", "hun" to "hu", "hye" to "hy", "ibo" to "ig", "ice" to "is", "ido" to "io", "iii" to "ii", "iku" to "iu",
        "ile" to "ie", "in" to "id", "ina" to "ia", "ind" to "id", "ipk" to "ik", "isl" to "is", "ita" to "it", "iw" to "he",
        "jav" to "jv", "ji" to "yi", "jpn" to "ja", "kal" to "kl", "kan" to "kn", "kas" to "ks", "kat" to "ka", "kau" to "kr",
        "kaz" to "kk", "khm" to "km", "kik" to "ki", "kin" to "rw", "kir" to "ky", "kom" to "kv", "kon" to "kg", "kor" to "ko",
        "kua" to "kj", "kur" to "ku", "lao" to "lo", "lat" to "la", "lav" to "lv", "lim" to "li", "lin" to "ln", "lit" to "lt",
        "ltz" to "lb", "lub" to "lu", "lug" to "lg", "mac" to "mk", "mah" to "mh", "mal" to "ml", "mao" to "mi", "mar" to "mr",
        "may" to "ms", "mkd" to "mk", "mlg" to "mg", "mlt" to "mt", "mo" to "ro", "mol" to "ro", "mon" to "mn", "mri" to "mi",
        "msa" to "ms", "mya" to "my", "nau" to "na", "nav" to "nv", "nbl" to "nr", "nde" to "nd", "ndo" to "ng", "nep" to "ne",
        "nld" to "nl", "nno" to "nn", "nob" to "nb", "nor" to "no", "nya" to "ny", "oci" to "oc", "oji" to "oj", "ori" to "or",
        "orm" to "om", "oss" to "os", "pan" to "pa", "per" to "fa", "pli" to "pi", "pol" to "pl", "por" to "pt", "pus" to "ps",
        "que" to "qu", "roh" to "rm", "ron" to "ro", "rum" to "ro", "run" to "rn", "rus" to "ru", "sag" to "sg", "san" to "sa",
        "sin" to "si", "slk" to "sk", "slo" to "sk", "slv" to "sl", "sme" to "se", "smo" to "sm", "sna" to "sn", "snd" to "sd",
        "som" to "so", "sot" to "st", "spa" to "es", "sqi" to "sq", "srd" to "sc", "srp" to "sr", "ssw" to "ss", "sun" to "su",
        "swa" to "sw", "swe" to "sv", "tah" to "ty", "tam" to "ta", "tat" to "tt", "tel" to "te", "tgk" to "tg", "tgl" to "tl",
        "tha" to "th", "tib" to "bo", "tir" to "ti", "ton" to "to", "tsn" to "tn", "tso" to "ts", "tuk" to "tk", "tur" to "tr",
        "twi" to "tw", "uig" to "ug", "ukr" to "uk", "urd" to "ur", "uzb" to "uz", "ven" to "ve", "vie" to "vi", "vol" to "vo",
        "wel" to "cy", "wln" to "wa", "wol" to "wo", "xho" to "xh", "yid" to "yi", "yor" to "yo", "zha" to "za", "zho" to "zh",
        "zul" to "zu",
    )
}
