package com.pandulapeter.campfire.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
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
        viewModel.mainNavigationItem.onPropertyChanged {
            if (!isFinishing) {
                replaceActiveFragment(it)
            }
        }
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
        viewModel.mainNavigationItem.set(navigationItem)
    }

    fun updatePreviousNavigationItem(navigationItem: MainViewModel.MainNavigationItem) {
        viewModel.previousNavigationItem = navigationItem
    }

    fun navigateBack() = viewModel.mainNavigationItem.set(viewModel.previousNavigationItem)

    private fun replaceActiveFragment(mainNavigationItem: MainViewModel.MainNavigationItem) {
        val currentFragment = getCurrentFragment()
        if (currentFragment == null) {
            supportFragmentManager.beginTransaction().replace(android.R.id.content, mainNavigationItem.getFragment()).commitNow()
        } else {
            coroutine?.cancel()
            coroutine = async(UI) {
                val nextFragment = async(CommonPool) {
                    mainNavigationItem.getFragment()
                }.await()
                if (nextFragment is HomeFragment) {
                    nextFragment.shouldPlayReturnAnimation = true
                }
                supportFragmentManager.beginTransaction().replace(android.R.id.content, nextFragment).commit()
            }
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(android.R.id.content) as? CampfireFragment<*, *>

    companion object {
        private var Intent.mainNavigationItem by IntentExtraDelegate.String("main_navigation_item")

        fun getStartIntent(context: Context, mainNavigationItem: MainViewModel.MainNavigationItem) = context.getIntentFor(MainActivity::class) {
            it.mainNavigationItem = mainNavigationItem.stringValue
        }
    }
}