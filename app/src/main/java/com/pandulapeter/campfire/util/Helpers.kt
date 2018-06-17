package com.pandulapeter.campfire.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.pandulapeter.campfire.feature.detail.page.parsing.Note

fun consume(action: () -> Unit): Boolean {
    action()
    return true
}

fun showKeyboard(focusedView: View?) = focusedView?.also {
    it.requestFocus()
    (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(it, 0)
}

fun hideKeyboard(focusedView: View?) = focusedView?.also {
    it.clearFocus()
    (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
}

fun generateNotationExample(shouldUseGermanNotation: Boolean) = listOf(
    Note.C.getName(shouldUseGermanNotation),
    Note.CSharp.getName(shouldUseGermanNotation),
    Note.D.getName(shouldUseGermanNotation),
    Note.DSharp.getName(shouldUseGermanNotation),
    Note.E.getName(shouldUseGermanNotation),
    Note.F.getName(shouldUseGermanNotation),
    Note.FSharp.getName(shouldUseGermanNotation),
    Note.G.getName(shouldUseGermanNotation),
    Note.GSharp.getName(shouldUseGermanNotation),
    Note.A.getName(shouldUseGermanNotation),
    Note.ASharp.getName(shouldUseGermanNotation),
    Note.B.getName(shouldUseGermanNotation)
).joinToString(", ")