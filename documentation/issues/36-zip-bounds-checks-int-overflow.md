# 36 · Zip bounds checks can overflow `Int`

**Severity:** low (wrong exception type, no crash) · **Area:** `:data:source:local:implementation` (zip)

`ZipReader.kt:89` `dataOffset + compressedSize > archive.size` and `Inflater.kt:31` `offset + length > source.size`:
with `compressedSize = 0x7FFFFFFF` the sum wraps negative and the check passes; STORED then fails in `copyOfRange`
with `IllegalArgumentException`, DEFLATE on the first `bits()` call. Both are caught upstream as `Exception`.

## Fix

Do the arithmetic in `Long`: `dataOffset.toLong() + compressedSize > archive.size` (and the same in `Inflater`),
so the message is the `ZipException` the reader means to throw. Add the crafted-header case to the zip tests
alongside issue 08's.
