package com.pandulapeter.campfire.old.feature

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Window
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.feature.detail.DetailFragment
import com.pandulapeter.campfire.old.feature.home.HomeFragment
import com.pandulapeter.campfire.old.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.old.util.getIntentFor
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.IntentExtraDelegate
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.onPropertyChanged
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext


/**
 * Container for all Fragments in the application. Handles state saving and restoration.
 *
 * Controlled by [MainViewModel].
 */
class MainActivity : AppCompatActivity(), AlertDialogFragment.OnDialogItemsSelectedListener {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val viewModel by lazy { MainViewModel(userPreferenceRepository, MainViewModel.MainNavigationItem.fromStringValue(intent.mainNavigationItem)) }
    private var coroutine: CoroutineContext? = null
    private val currentFragment get() = supportFragmentManager.findFragmentById(android.R.id.content) as? CampfireFragment<*, *>
    private var Bundle.mainNavigationItem by BundleArgumentDelegate.String("main_navigation_item")

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (userPreferenceRepository.shouldUseDarkTheme) R.style.DarkTheme else R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { intent.mainNavigationItem = it.mainNavigationItem }
        viewModel.mainNavigationItem.onPropertyChanged {
            if (!isFinishing) {
                replaceActiveFragment(it)
            }
        }
        if (currentFragment == null) {
            viewModel.mainNavigationItem.get()?.let { replaceActiveFragment(it) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        MainViewModel.MainNavigationItem.fromStringValue(intent?.mainNavigationItem)?.let { setNavigationItem(it) }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        viewModel.mainNavigationItem.get()?.stringValue?.let { outState?.mainNavigationItem = it }
    }

    override fun onBackPressed() {
        if (currentFragment?.onBackPressed() != true) {
            if (userPreferenceRepository.shouldShowExitConfirmation) {
                AlertDialogFragment.show(
                    supportFragmentManager,
                    R.string.home_exit_confirmation_title,
                    R.string.home_exit_confirmation_message,
                    R.string.home_exit_confirmation_close,
                    R.string.cancel
                )
            } else {
                onPositiveButtonSelected()
            }
        }
    }

    override fun onPositiveButtonSelected() = supportFinishAfterTransition()

    fun setNavigationItem(navigationItem: MainViewModel.MainNavigationItem) {
        viewModel.mainNavigationItem.set(navigationItem)
    }

    fun updatePreviousNavigationItem(navigationItem: MainViewModel.MainNavigationItem) {
        viewModel.previousNavigationItem = navigationItem
    }

    fun navigateBack() = viewModel.mainNavigationItem.set(viewModel.previousNavigationItem)

    private fun replaceActiveFragment(mainNavigationItem: MainViewModel.MainNavigationItem) {
        if (currentFragment == null) {
            supportFragmentManager.beginTransaction().replace(android.R.id.content, mainNavigationItem.getFragment()).commitNow()
        } else {
            coroutine?.cancel()
            coroutine = async(UI) {
                val nextFragment = async(CommonPool) {
                    mainNavigationItem.getFragment()
                }.await()
                if (nextFragment is HomeFragment && currentFragment is DetailFragment) {
                    nextFragment.shouldPlayReturnAnimation = true
                }
                supportFragmentManager.beginTransaction().replace(Window.ID_ANDROID_CONTENT, nextFragment).commit()
            }
        }
    }

    companion object {
        private var Intent.mainNavigationItem by IntentExtraDelegate.String("main_navigation_item")

        fun getStartIntent(context: Context, mainNavigationItem: MainViewModel.MainNavigationItem? = null) =
            context.getIntentFor(MainActivity::class) { intent ->
                mainNavigationItem?.let { intent.mainNavigationItem = it.stringValue }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}