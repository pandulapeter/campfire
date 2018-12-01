package com.pandulapeter.campfire.feature.shared.behavior

import android.os.Bundle

abstract class Behavior {

    open fun onViewCreated(savedInstanceState: Bundle?) = Unit
}