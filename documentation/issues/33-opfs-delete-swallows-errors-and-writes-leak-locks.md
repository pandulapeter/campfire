# 33 · OPFS: `delete` treats every failure as success, and a failed write never aborts its writable

**Severity:** medium-low (a rename that leaves the song twice, a delete that "succeeds" without deleting) · **Area:** `:data:source:local:implementation` (wasmJsMain)

## Cause

`FileStorage.wasmJs.kt:178`: `parent.removeEntry(name).catch(function () { return null; })` maps every rejection
(including `NoModificationAllowedError` while a writable is open on the file) to success. `renameSong` and
`renameSetlist` write the new file then delete the old, so a failed delete leaves both. `writeFileText` /
`writeFileBytes` (:172, :175) never call `abort()` when `write` rejects, which is what leaves that lock held.
`getFileHandle` (:158) has the same blanket catch, which is why issue 07 lists it.

## Fix

Narrow every catch to the one error it is meant for, and abort a writable on failure:

```js
// removeEntry
parent.removeEntry(name).catch(function (e) { if (e && e.name === 'NotFoundError') return null; throw e; })

// getFileHandle
parent.getFileHandle(name, { create: create }).catch(function (e) { if (e && e.name === 'NotFoundError') return null; throw e; })

// writeFileText / writeFileBytes
handle.createWritable().then(function (writable) {
    return writable.write(data).then(function () { return writable.close(); }, function (e) { return writable.abort().then(function () { throw e; }, function () { throw e; }); });
})
```

Update the KDoc on `removeEntry` ("resolves instead of rejecting when the file is not there") to say only that case
is folded. A rejected promise surfaces as an exception from `.await()`, which the sources already treat as a failed
operation. Test manually on the web build: delete a song while it is open, rename a song, refresh: exactly one file.
