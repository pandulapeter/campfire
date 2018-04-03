package com.pandulapeter.campfire.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

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