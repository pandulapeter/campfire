package com.pandulapeter.campfire.feature

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.animation.AnimationUtils
import com.pandulapeter.campfire.MainBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.HomeFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.IntentExtraDelegate
import com.pandulapeter.campfire.util.getIntentFor
import com.pandulapeter.campfire.util.onPropertyChanged
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import javax.inject.Inject
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Container for all Fragments in the application. Handles state saving and restoration.
 *
 * Controlled by [MainViewModel].
 */
class MainActivity : DaggerAppCompatActivity() {
    @Inject lateinit var userPreferenceRepository: UserPreferenceRepository
    private val viewModel by lazy { MainViewModel(userPreferenceRepository, MainViewModel.MainNavigationItem.fromStringValue(intent.mainNavigationItem)) }
    private var coroutine: CoroutineContext? = null
    private var Bundle.mainNavigationItem by BundleArgumentDelegate.String("main_navigation_item")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { intent.mainNavigationItem = it.mainNavigationItem }
        val binding = DataBindingUtil.setContentView<MainBinding>(this, R.layout.activity_main)
        binding.viewModel = viewModel
        viewModel.mainNavigationItem.onPropertyChanged { replaceActiveFragment(it) }
        if (getCurrentFragment() == null) {
            replaceActiveFragment(viewModel.mainNavigationItem.get())
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        MainViewModel.MainNavigationItem.fromStringValue(intent?.mainNavigationItem)?.let { setNavigationItem(it) }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.mainNavigationItem = viewModel.mainNavigationItem.get().stringValue
    }

    override fun onBackPressed() {
        if (getCurrentFragment()?.onBackPressed() != true) {
            super.onBackPressed()
        }
    }

    fun setNavigationItem(navigationItem: MainViewModel.MainNavigationItem) {
        viewModel.previousNavigationItem = viewModel.mainNavigationItem.get()
        viewModel.mainNavigationItem.set(navigationItem)
    }

    fun navigateBack() = viewModel.mainNavigationItem.set(viewModel.previousNavigationItem)

    private fun replaceActiveFragment(mainNavigationItem: MainViewModel.MainNavigationItem) {
        val currentFragment = getCurrentFragment()
        coroutine?.cancel()
        coroutine = async(UI) {
            val nextFragment = async(CommonPool) {
                when (mainNavigationItem) {
                    is MainViewModel.MainNavigationItem.Home -> HomeFragment.newInstance(mainNavigationItem.homeNavigationItem)
                    is MainViewModel.MainNavigationItem.Detail -> DetailFragment.newInstance(mainNavigationItem.songId, mainNavigationItem.playlistId)
                }
            }.await()
            currentFragment?.let {
                it.outAnimation = AnimationUtils.loadAnimation(this@MainActivity, android.R.anim.fade_out)
                (nextFragment as CampfireFragment<*, *>).inAnimation = AnimationUtils.loadAnimation(this@MainActivity, android.R.anim.fade_in)
            }
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, nextFragment).commit()
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? CampfireFragment<*, *>

    companion object {
        private var Intent.mainNavigationItem by IntentExtraDelegate.String("main_navigation_item")

        fun getStartIntent(context: Context, mainNavigationItem: MainViewModel.MainNavigationItem) = context.getIntentFor(MainActivity::class) {
            it.mainNavigationItem = mainNavigationItem.stringValue
        }
    }
}