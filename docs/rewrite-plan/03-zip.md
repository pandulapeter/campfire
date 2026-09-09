# Step 03: pure Kotlin zip support

**Goal:** read `.zip` archives (STORED and DEFLATE entries) and write them (STORED entries) in common Kotlin, with no
platform APIs, so that bulk import/export works identically on Android, iOS, desktop and web.

**Depends on:** nothing. Can run in parallel with steps 01 and 02. Step 08 uses it.

## 1. Location

Package `com.pandulapeter.campfire.data.source.local.implementation.zip` in
`data/source/local/implementation/src/commonMain/kotlin/...`. Everything is `internal` except the two entry points
below, which step 08 calls from the local source implementations in the same module (they stay `internal` too;
the module is the only consumer).

If step 02 has not yet added `commonTest.dependencies { implementation(kotlin("test")) }` to this module's
`build.gradle.kts`, add it.

## 2. API

```kotlin
internal data class ZipEntry(val name: String, val bytes: ByteArray)

internal object ZipReader {
    /**
     * Every file entry of the archive, directories skipped, names as stored (forward slashes, may contain
     * sub-directories; callers strip the path). Throws ZipException on a malformed archive or an unsupported
     * compression method / encryption.
     */
    fun read(archive: ByteArray): List<ZipEntry>
}

internal object ZipWriter {
    /** A valid zip archive with one STORED (uncompressed) entry per input, UTF-8 names, current date/time not needed. */
    fun write(entries: List<ZipEntry>): ByteArray
}

internal class ZipException(message: String) : Exception(message)
```

Song files are tiny, so STORED output is fine; DEFLATE compression on write is out of scope.

## 3. Reader algorithm (PKWARE APPNOTE)

1. Find the End Of Central Directory record: scan backwards from the end for the signature `0x06054b50`, allowing up
   to 65 535 + 22 bytes of comment. Read `totalEntries` (offset 10, u16), `centralDirectorySize` (12, u32) and
   `centralDirectoryOffset` (16, u32). ZIP64 is unsupported: if any of these is `0xFFFF` / `0xFFFFFFFF` throw.
2. Walk the central directory: each header has signature `0x02014b50`, then at fixed offsets: general purpose flag
   (8, u16), method (10, u16), crc32 (16, u32), compressedSize (20, u32), uncompressedSize (24, u32), nameLength (28),
   extraLength (30), commentLength (32), localHeaderOffset (42, u32), name at 46. Names are decoded as UTF-8
   regardless of flag bit 11 (real-world archives with non-ASCII names are UTF-8 in practice).
3. Skip entries whose name ends with `/` (directories). If flag bit 0 (encryption) is set, throw.
4. For each file: read the local header at `localHeaderOffset` (signature `0x04034b50`, nameLength at 26, extraLength
   at 28, data starts at 30 + both). Always trust the *central directory* sizes (the local ones are zero when flag
   bit 3, the data descriptor, is set).
5. Method 0 → copy `compressedSize` bytes. Method 8 → `Inflater.inflate(data, offset, compressedSize, uncompressedSize)`.
   Any other method → throw.
6. Verify the CRC-32 of the result against the central directory value; throw on mismatch.

All multi-byte integers are little-endian; write two tiny helpers `ByteArray.u16(offset)` / `u32(offset)` returning
`Int` / `Long`.

## 4. `Inflater` (RFC 1951)

Implement a straightforward inflater (about 250 lines):

- Bit reader over the input (LSB first).
- Block loop: `BFINAL`, `BTYPE` ∈ {0 stored, 1 fixed Huffman, 2 dynamic Huffman}; 3 → throw.
- Stored block: align to byte, read LEN / NLEN, copy.
- Fixed Huffman: the standard literal/length code lengths (0–143: 8, 144–255: 9, 256–279: 7, 280–287: 8) and
  5-bit distance codes.
- Dynamic Huffman: read HLIT, HDIST, HCLEN, the code-length code (order 16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15),
  then the literal/length and distance code lengths with the 16/17/18 repeat codes.
- Canonical Huffman decoding: build `count[]` / `symbol[]` tables per RFC (the "puff.c" approach is the simplest to get
  right: decode bit by bit, no lookup tables needed at these sizes).
- Length base/extra tables (257–285) and distance base/extra tables (0–29) as `IntArray` constants.
- Output into a `ByteArray` sized `uncompressedSize` when known, otherwise a growable buffer.

Reference implementation to mirror: Mark Adler's `puff.c` (public domain, in zlib's `contrib/puff`). Do not port
zlib's `inflate.c`.

## 5. `Crc32`

Standard table-driven CRC-32 (polynomial `0xEDB88320`), `fun of(bytes: ByteArray): Long` returning the unsigned value.

## 6. Writer

For each entry, in order: local file header (signature, version 20, flags `0x0800` for UTF-8 names, method 0,
time/date 0, crc32, size twice, name length, extra length 0, name), then the data. Then the central directory
(signature `0x02014b50`, version made by 20, version needed 20, same fields plus comment length 0, disk 0, attributes
0, local header offset, name), then the End Of Central Directory record with entry counts, directory size and offset,
comment length 0. Build into a growable `ByteArray` (a small `ByteArrayBuilder` helper: `u8`, `u16`, `u32`, `bytes`).

## 7. Tests (`commonTest`, run on desktop: `./gradlew :data:source:local:implementation:desktopTest`)

- `ZipRoundTripTest` (commonTest): write three entries (empty, ASCII, UTF-8 name and content, a 100 KB pseudo-random
  one) and read them back: names, bytes, order.
- `ZipReaderTest` (commonTest): a hand-built minimal STORED archive as a byte literal; reading rejects a truncated
  archive, a method-99 entry and a bad CRC with `ZipException`; a directory entry is skipped.
- `InflaterTest` (**desktopTest**): use `java.util.zip.Deflater` (allowed there, it is a JVM test source set) to
  compress fixtures with every level 0–9 (level 0 → stored blocks, 1 → mostly fixed, 9 → dynamic) plus
  `Deflater(level, nowrap = true)` for raw deflate, and assert the Kotlin inflater reproduces the input. Fixtures:
  empty, "a", 300 × "abc", a 2 MB random buffer, a text with long repeats (exercises max distance 32 768).
- `ZipReaderJvmTest` (desktopTest): build an archive with `java.util.zip.ZipOutputStream` (DEFLATED, with a
  sub-directory and a data-descriptor entry, which `ZipOutputStream` produces when writing to a non-seekable stream)
  and check `ZipReader` reads it.

## Verify

- Tests pass; full build command from the README succeeds for all four platforms (the code is common, but confirm
  the wasm compiler accepts it: no `Long` shifts larger than 63, no signed/unsigned pitfalls).

## Execution notes

Implemented as specified. Files added under
`data/source/local/implementation/src/commonMain/kotlin/.../zip/`: `ZipEntry.kt`, `ZipException.kt`, `ZipReader.kt`,
`ZipWriter.kt`, `Inflater.kt`, `Crc32.kt`, `ByteArrayBuilder.kt`, `LittleEndian.kt`. Tests: `ZipRoundTripTest` and
`ZipReaderTest` in `commonTest`, `InflaterTest` and `ZipReaderJvmTest` in `desktopTest`. The
`commonTest.dependencies { implementation(kotlin("test")) }` block was added to this module's `build.gradle.kts`
(step 02 had not added it yet).

Small deviations, all deliberate:

- `ZipEntry` overrides `equals` / `hashCode` (content based) instead of relying on the generated `data class` ones,
  which compare the `ByteArray` by identity.
- `Crc32` only exposes `of(bytes)`; the offset/length overload would have been dead code.
- The `u8` / `u16` / `u32` helpers throw `ZipException` instead of `IndexOutOfBoundsException` when the requested
  bytes fall outside the input, so every malformed archive surfaces as a `ZipException`.
- The fixed distance code is built from 32 five-bit lengths (a complete code) rather than `puff.c`'s 30 (an
  incomplete one it constructs without checking); symbols 30 and 31 are rejected when they are decoded.
- Test method names are camelCase rather than backticked sentences, because `commonTest` is also compiled for the
  Kotlin/Native and Kotlin/Wasm targets.
- `ZipReaderTest`'s fixture is a hex string literal of a real archive produced by the system `zip -0` tool (a
  directory entry plus two stored files) instead of a hand-typed byte literal.

Verification: `:data:source:local:implementation:desktopTest` runs 18 tests green (10 common + 8 JVM-only) and
`:data:source:local:implementation:build` is green, which also runs the 10 common tests on `iosSimulatorArm64` and
`wasmJsBrowser`. Beyond the listed tests, `ZipReader` was checked byte-for-byte against an archive built by the
system `zip` command (mixed STORED and DEFLATE entries, nested directories, a 400 KB incompressible file and a
216 KB highly compressible one), and a `ZipWriter` archive passed `unzip -t` and Python's `zipfile` verification.
Note that Info-ZIP's `unzip` mangles non-ASCII entry names on extraction because it ignores the UTF-8 flag; that is
an `unzip` limitation, not a writer bug — the JVM and Python both read the names correctly.
