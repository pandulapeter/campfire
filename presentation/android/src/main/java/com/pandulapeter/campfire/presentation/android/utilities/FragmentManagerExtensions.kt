package com.pandulapeter.campfire.presentation.android.utilities

import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

inline fun <reified T : Fragment> FragmentManager.handleReplace(
    @IdRes containerId: Int,
    addToBackStack: Boolean = false,
    tag: String = T::class.java.name,
    sharedElements: List<View>? = null,
    crossinline newInstance: () -> T
) {
    beginTransaction().apply {
        val newFragment = findFragmentByTag(tag) ?: newInstance()
        sharedElements?.forEach { sharedElement -> ViewCompat.getTransitionName(sharedElement)?.let { addSharedElement(sharedElement, it) } }
        replace(containerId, newFragment, tag)
        if (addToBackStack) {
            addToBackStack(null)
        }
        setReorderingAllowed(true)
        commitAllowingStateLoss()
    }
}