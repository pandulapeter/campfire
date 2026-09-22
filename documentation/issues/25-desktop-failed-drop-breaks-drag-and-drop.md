# 25 · Desktop: a drop whose file list cannot be read is ignored, and every later drag onto the window fails until the app is restarted

**Severity:** wrong behaviour (desktop, mostly Linux; unlikely — it takes a drag source whose file list the JDK
cannot convert, but once it happens drag and drop is dead for the rest of the session, with nothing on screen to say
so) · **Area:** `:presentation` (`desktopMain/ui/CampfireDesktopApp.kt`)

## Symptom
1. On Linux (X11/XWayland), drag a file onto the Campfire window from an application that puts a malformed
   `text/uri-list` on the drag (an unencoded space or `#` in a `file://` URI; some file managers, sandboxed apps
   and Wine programs do), or from a source that goes away or fails to deliver its data during the drop (a file on a
   remote share or a Windows Explorer drag of a file inside a zip that cannot be extracted).
2. Nothing is imported, and a stack trace goes to stderr.
3. Every following drag onto the window — any file, from any application — is refused, again with a stack trace
   (`IllegalStateException: DragAndDropTarget self reference must be null at the start of a drag and drop session`),
   until Campfire is restarted.

The app does not close: the exception is thrown on the AWT event thread, whose dispatcher prints it and carries on,
and the drop listener is outside the `WindowExceptionHandler` that would otherwise show the error dialog.

## Cause
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt:70-78`:

```kotlin
override fun onDrop(event: DragAndDropEvent): Boolean {
    val paths = (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
```

`Transferable.getTransferData` is declared to throw `UnsupportedFlavorException` and `IOException`, and a drop
transferable can also throw `InvalidDnDOperationException`. The JDK's X11 transfer converts a `text/uri-list` with
`new URI(line)` and turns a `URISyntaxException` into an `IOException` (`sun.awt.X11.XDataTransferer.dragQueryURIs`);
`shouldStartDragAndDrop` only checked that the flavor is *offered*, which says nothing about whether it can be
converted.

Compose Multiplatform 1.12.0 calls the target from `AwtDragAndDropManager.desktop.kt:209-215`:

```kotlin
override fun drop(dtde: DropTargetDropEvent) {
    ...
    dtde.dropComplete(rootNode.onDrop(event))
    rootNode.onEnded(event)
}
```

with no `try`. An exception out of `onDrop` skips `onEnded`, which is what resets the node's
`thisDragAndDropTarget` to null (`DragAndDropNode.kt:433-441`), and AWT sends no `dragExit` after a drop. The next
`dragEnter` then hits `acceptDragAndDropTransfer`'s precondition (`DragAndDropNode.kt:337-340`,
`checkPrecondition(currentNode.thisDragAndDropTarget == null)`) and throws, for as long as the node lives — which is
as long as the window.

## Fix
In `CampfireDesktopApp.kt`, read the file list inside a `try` and answer a failure as a refused drop, so that
`onDrop` always returns and Compose always ends the session:

```kotlin
override fun onDrop(event: DragAndDropEvent): Boolean {
    // The transferable is the drag source's, and converting it can fail however it was offered (the JDK
    // throws for a malformed uri-list on X11, or a source that is gone); an exception out of here would also
    // skip the end of the session, and the window would refuse every drag after this one.
    val files = try {
        event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
    } catch (exception: Exception) {
        println("Could not read the dropped files: ${exception.message}")
        return false
    }
    val paths = files.orEmpty().filterIsInstance<File>().map { it.absolutePath }
    // Only the paths are taken here: this is the AWT event thread, and the files are read off it.
    scope.launch { viewModel.importFiles(withContext(Dispatchers.IO) { paths.readAsImportedFiles() }) }
    return true
}
```

`Exception` covers the three checked and unchecked types above; nothing else is expected there. No message is shown:
a refused drop is what the drag source displays (the item flies back), and there is no file name to say anything
about.

Do **not** wrap the whole `drop` in the app's own `DropTargetListener` or install a default uncaught exception
handler: the fault is the one call that reads foreign data, and that is where it is caught.

## Tests
None (UI is untested).

## Verify
1. Linux (or any desktop, with a test source): run `./gradlew :app:desktop:run`; drag a file from a source that offers
   a broken uri-list — e.g. a tiny Swing test app whose `Transferable.getTransferData` throws `IOException` for
   `javaFileListFlavor` while `isDataFlavorSupported` answers true. Before: nothing imported, and the next ordinary
   drag from the file manager is refused with the `IllegalStateException` above. After: the bad drop is refused with
   one log line, and the next ordinary drag imports as usual.
2. An ordinary drop of files and of a folder still imports them.

## Docs
`presentation/CLAUDE.md`, the `desktopMain/ui/CampfireDesktopApp.kt` bullet: after "accepts dropped files (a folder
standing for the files directly inside it)" add "; a drop whose file list cannot be read is refused rather than
thrown, since an exception out of the target skips the end of the drag session and the window would refuse every
drag after it".

## Touches
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
- `presentation/CLAUDE.md`

## Depends on
19 also edits `CampfireDesktopApp.kt` (a new `handlePreviewKeyEvent` next to `handleKeyEvent`, not these lines);
schedule the two one after another.
