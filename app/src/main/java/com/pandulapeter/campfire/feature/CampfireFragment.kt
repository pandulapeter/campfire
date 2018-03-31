package com.pandulapeter.campfire.feature

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentCampfireBinding

abstract class CampfireFragment : Fragment() {
    protected lateinit var binding: FragmentCampfireBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_campfire, container, false)
        return binding.root
    }
}