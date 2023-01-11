package com.pandulapeter.campfire.presentation.android

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.pandulapeter.campfire.presentation.android.databinding.FragmentCollectionsBinding
import com.pandulapeter.campfire.presentation.android.utilities.autoClearedValue
import com.pandulapeter.campfire.presentation.android.utilities.bind
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainFragment : Fragment(R.layout.fragment_collections) {

    private val viewModel by viewModel<MainViewModel>()
    private var binding by autoClearedValue<FragmentCollectionsBinding>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = bind(view)
        binding.viewModel = viewModel
    }

    companion object {
        fun newInstance() = MainFragment()
    }
}