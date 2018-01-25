package com.pandulapeter.campfire.util

import android.content.Context
import android.support.v4.widget.DrawerLayout
import android.view.View
import android.view.inputmethod.InputMethodManager

fun consume(action: () -> Unit): Boolean {
    action()
    return true
}


fun consumeAndCloseDrawer(drawerLayout: DrawerLayout, action: () -> Unit) = consume {
    action()
    drawerLayout.closeDrawers()
}

fun showKeyboard(focusedView: View?) = focusedView?.let {
    (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(it, 0)
}

fun hideKeyboard(focusedView: View?) = focusedView?.let {
    (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
}