# 63 · `TabRows.rowsByWidth` grows without bound

**Severity:** low · **Area:** `:presentation` (`SongLyrics.kt`, `TabRows`)

`SongLyrics.kt:565–572`: one entry of `TextLayoutResult`s per width the block was ever measured at, for the life of
the composition; a desktop window drag adds hundreds.

## Fix

Bound it to the last few widths: `private val rowsByWidth = object : LinkedHashMap<Int, List<List<TextLayoutResult>>>(16, 0.75f, true) { override fun removeEldestEntry(eldest) = size > MAX_WIDTHS }`
— `LinkedHashMap` with access order is in `kotlin.collections` on the JVM only; in common code implement the same
with a `MutableMap` plus an `ArrayDeque<Int>` of recently used widths (`MAX_WIDTHS = 8`). The KDoc says "the same few
come back on every pass", which is exactly what a small LRU keeps.
