package com.pandulapeter.campfire.feature.detail

import android.animation.Animator
import android.animation.ObjectAnimator
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator

class FadeInTransition : Transition() {

    private val timeInterpolator = LinearInterpolator()

    override fun captureStartValues(transitionValues: TransitionValues) = Unit

    override fun captureEndValues(transitionValues: TransitionValues) = Unit

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator =
        ObjectAnimator.ofFloat((endValues.view as ViewGroup).getChildAt(0).apply {
            alpha = 1f
        }, View.ALPHA, 0f, 1f).apply {
            interpolator = timeInterpolator
        }
}