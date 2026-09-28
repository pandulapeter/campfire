# Bound export memory before building the whole archive

**Kind:** performance / robustness · **Severity:** high (P1 on memory-constrained devices) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt`, export/archive contracts, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipWriter.kt`, `ByteArrayBuilder.kt`, platform export destinations.

## Problem

Export's batches of 64 limit concurrent reads, not retained memory: `flatMap` collects all song texts (53–67),
then `buildMap` holds all encoded files (69–77). `ZipWriter.write` creates a full-size archive buffer (23),
and `ByteArrayBuilder.build` copies it into another full-size array (43). The writer's input arrays remain
referenced alongside its output buffers during construction. Platform save/share implementations can copy again.

The library can grow through many individually valid imports or sync. There is no aggregate export budget.
For a 64 MiB payload, input byte arrays plus the archive builder and returned archive alone approach 192 MiB,
before song strings and the app's normal working set. Exact liveness and string storage vary by platform, but
peak allocation grows with the entire library. An out-of-memory failure can end the app before the save picker
appears; `catch (Exception)` is not a memory-management strategy.

The existing 24 MiB re-import warning is shown only after archive construction and saving
(`CampfireViewModel.save`, 2162–2175). It cannot protect this allocation. Exporting larger archives is already
intentional, so silently skipping songs to meet a new limit would be a regression.

## Evidence / reproduction

Verified by tracing retained collections and the two archive allocations, not by crashing a phone. A controlled
follow-up should populate a disposable library with, for example, 4,096 valid 16 KiB songs, export under a small
heap, and record peak retained memory. Each input is comfortably below the per-file limit; several import batches
can create this library. Do not use a real user's only library for the stress run.

## Fix

For the final patch, preflight total supported file sizes plus conservative archive/encoding overhead before
reading payloads. Enforce a documented memory-safe export budget with clear failure feedback and a useful smaller
export/manual-backup path; abort rather than deliver an incomplete archive as complete. Enforce the budget again
as files are read, since the listing can become stale.

The durable solution is streaming ZIP output to a temporary file or platform sink, one bounded entry at a time.
The current `ExportedFile.bytes` and `ArchiveRepository.pack(Map<String, ByteArray>)` contracts require whole
archives in memory, so this needs a contract change, not merely smaller read batches. Preserve cancellation,
temporary-file cleanup, and skipped-file reporting. Do not raise the import limit as a substitute.

## Verification

- A known over-budget library is refused before any payload read or large allocation in the patch solution.
- An input growing after preflight cannot exceed the runtime budget unnoticed.
- Ordinary exports remain complete and round-trip; over-budget exports produce an explicit message.
- For a streaming implementation, measure peak memory at increasing library sizes and prove it plateaus.

## Conflicts

Coordinate with 10's fresh setlist listing and 11's fresh reads. This is aggregate export allocation, independent
of 06's render allocations and 09's session cache.
