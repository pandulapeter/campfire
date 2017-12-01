package com.pandulapeter.campfire.util

import android.content.Context
import android.support.v4.widget.DrawerLayout
import android.view.View
import android.view.inputmethod.InputMethodManager

fun consume(action: () -> Unit): Boolean {
    action()
    return true
}

fun DrawerLayout.addDrawerListener(
    onDrawerStateChanged: () -> Unit = {},
    onDrawerSlide: () -> Unit = {},
    onDrawerClosed: () -> Unit = {},
    onDrawerOpened: () -> Unit = {}) = addDrawerListener(object : DrawerLayout.DrawerListener {
    override fun onDrawerStateChanged(newState: Int) = onDrawerStateChanged()

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = onDrawerSlide()

    override fun onDrawerClosed(drawerView: View) = onDrawerClosed()

    override fun onDrawerOpened(drawerView: View) = onDrawerOpened()
})

fun hideKeyboard(focusedView: View?) = focusedView?.let {
    (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
}