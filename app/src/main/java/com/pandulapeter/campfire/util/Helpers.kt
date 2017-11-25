package com.pandulapeter.campfire.util

fun consume(action: () -> Unit): Boolean {
    action()
    return true
}