package com.pandulapeter.campfire.feature

import android.app.ActivityManager
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.annotation.MenuRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.transition.Fade
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.database.PreferenceDatabase
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.manageDownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.options.OptionsFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class CampfireActivity : AppCompatActivity(), AlertDialogFragment.OnDialogItemsSelectedListener {

    private var Bundle.isOnDetailScreen by BundleArgumentDelegate.Boolean("isOnDetailScreen")
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? CampfireFragment<*>?
    private val drawableMenuToBack by lazy { animatedDrawable(R.drawable.avd_menu_to_back_24dp) }
    private val drawableBackToMenu by lazy { animatedDrawable(R.drawable.avd_back_to_menu_24dp) }
    private val appShortcutManager by inject<AppShortcutManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val floatingActionButton get() = binding.floatingActionButton
    val autoScrollControl get() = binding.autoScrollControl
    val tabLayout get() = binding.tabLayout
    val toolbarContext: Context get() = binding.toolbar.context

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (preferenceDatabase.shouldUseDarkTheme) R.style.DarkTheme else R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)
        appShortcutManager.updateAppShortcuts()
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            currentFragment?.onDrawerStateChanged(it)
            if (it == DrawerLayout.STATE_DRAGGING) {
                currentFocus?.also {
                    it.clearFocus()
                    hideKeyboard(it)
                }
            }
        })
        (binding.primaryNavigation.getHeaderView(0).findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.primaryNavigation.disableScrollbars()
        binding.primaryNavigation.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { LibraryFragment() } }
                R.id.history -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { HistoryFragment() } }
                R.id.options -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { OptionsFragment() } }
                R.id.manage_playlists -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManagePlaylistsFragment() } }
                R.id.manage_downloads -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManageDownloadsFragment() } }
                else -> false
            }
        }
        binding.secondaryNavigation.disableScrollbars()
        if (savedInstanceState == null) {
            supportFragmentManager.handleReplace { LibraryFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.library)
        } else {
            binding.toolbarMainButton.setImageDrawable(drawable(if (savedInstanceState.isOnDetailScreen) R.drawable.ic_back_24dp else R.drawable.ic_menu_24dp))
        }
        binding.toolbarMainButton.setOnClickListener {
            if (currentFragment is DetailFragment) {
                supportFragmentManager.popBackStack()
                transformMainToolbarButton(false)
            } else {
                hideKeyboard(currentFocus)
                binding.drawerLayout.openDrawer(Gravity.START)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentFocus is EditText) {
            binding.drawerLayout.run { post { closeDrawers() } }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(Gravity.START)) {
            binding.drawerLayout.closeDrawer(Gravity.START)
        } else {
            if (binding.drawerLayout.isDrawerOpen(Gravity.END)) {
                binding.drawerLayout.closeDrawer(Gravity.END)
            } else {
                val fragment = currentFragment
                if (fragment == null || !fragment.onBackPressed()) {
                    if (fragment is DetailFragment) {
                        transformMainToolbarButton(false)
                        super.onBackPressed()
                    } else {
                        if (preferenceDatabase.shouldShowExitConfirmation) {
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
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isOnDetailScreen = currentFragment is DetailFragment
    }

    override fun onPositiveButtonSelected() = supportFinishAfterTransition()

    fun openSecondaryNavigationDrawer() {
        hideKeyboard(currentFocus)
        binding.drawerLayout.openDrawer(Gravity.END)
    }

    fun changeToolbarTitle(toolbar: View) {
        binding.toolbarTitleContainer.removeAllViews()
        binding.toolbarTitleContainer.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
    }

    fun changeToolbarButtons(buttons: List<View>) {
        binding.toolbarButtonContainer.removeAllViews()
        buttons.forEach {
            binding.toolbarButtonContainer.addView(it, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun openDetailScreen(songId: String, playlistId: String = "") {
        currentFocus?.also { hideKeyboard(it) }
        currentFragment?.exitTransition = Fade()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailFragment.newInstance(songId, playlistId), DetailFragment::class.java.name)
            .addToBackStack(null)
            .commit()
    }

    fun transformMainToolbarButton(shouldShowBackButton: Boolean) {
        binding.toolbarMainButton.setImageDrawable((if (shouldShowBackButton) drawableMenuToBack else drawableBackToMenu).apply { this?.start() })
        binding.drawerLayout.setDrawerLockMode(if (shouldShowBackButton) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START)
    }

    fun setSecondaryNavigationDrawerEnabled(@MenuRes menuResourceId: Int?) {
        binding.drawerLayout.setDrawerLockMode(if (menuResourceId == null) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
        menuResourceId?.let {
            binding.secondaryNavigation.menu.clear()
            binding.secondaryNavigation.inflateMenu(it)
        }
    }

    private inline fun <reified T : CampfireFragment<*>> FragmentManager.handleReplace(crossinline newInstance: () -> T) {
        currentFocus?.also { hideKeyboard(it) }
        currentFragment?.exitTransition = null
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(T::class.java.name) ?: newInstance.invoke(), T::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private inline fun consumeAndCloseDrawers(crossinline action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }


    private fun NavigationView.disableScrollbars() {
        (getChildAt(0) as? NavigationMenuView)?.isVerticalScrollBarEnabled = false
    }
}