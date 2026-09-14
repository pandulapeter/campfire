# 67 · Web picker: a slow `change` after the window regains focus loses the selection

**Severity:** low · **Area:** `:presentation` (wasmJsMain `FilePicker.wasmJs.kt`)

`FilePicker.wasmJs.kt:75`: the focus fallback resolves `[]` 500 ms after focus returns; `isDone` then discards a
`change` that arrives later (large selections, slow dialogs, mobile Safari).

## Fix

Use the `cancel` event where the browser has it and lengthen the fallback where it does not:

```js
input.addEventListener('cancel', function () { finish([]); });
function onFocus() { setTimeout(function () { finish([]); }, 1500); }
```

`cancel` on `<input type=file>` fires in current Chrome, Firefox and Safari when the dialog is dismissed; the focus
timer stays as the fallback for the rest. 1.5 s is long enough for a large selection to deliver `change` first.
