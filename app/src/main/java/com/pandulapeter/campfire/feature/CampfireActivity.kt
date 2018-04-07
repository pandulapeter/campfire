package com.pandulapeter.campfire.feature

import android.animation.Animator
import android.animation.LayoutTransition
import android.app.ActivityManager
import android.databinding.DataBindingUtil
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.annotation.MenuRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.transition.Explode
import android.transition.Transition
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.manageDownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.options.OptionsFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class CampfireActivity : AppCompatActivity(), AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_EXIT_CONFIRMATION = 1
        private const val DIALOG_ID_PRIVACY_POLICY = 2
    }

    private var Bundle.isOnDetailScreen by BundleArgumentDelegate.Boolean("isOnDetailScreen")
    private var Bundle.currentScreenId by BundleArgumentDelegate.Int("currentScreenId")
    private var Bundle.isAppBarExpanded by BundleArgumentDelegate.Boolean("isAppBarExpanded")
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? TopLevelFragment<*, *>?
    private val drawableMenuToBack by lazy { animatedDrawable(R.drawable.avd_menu_to_back_24dp) }
    private val drawableBackToMenu by lazy { animatedDrawable(R.drawable.avd_back_to_menu_24dp) }
    private val appShortcutManager by inject<AppShortcutManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private var currentScreenId = R.id.library
    private var forceExpandAppBar = true
    val autoScrollControl get() = binding.autoScrollControl
    val toolbarContext get() = binding.appBarLayout.context!!
    val secondaryNavigationMenu get() = binding.secondaryNavigation.menu ?: throw IllegalStateException("The secondary navigation drawer has no menu inflated.")
    val snackbarRoot get() = binding.rootCoordinatorLayout

    override fun onCreate(savedInstanceState: Bundle?) {

        // Set the theme and the task description.
        setTheme(if (preferenceDatabase.shouldUseDarkTheme) R.style.DarkTheme else R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)

        // Update the app shortcuts
        appShortcutManager.updateAppShortcuts()

        // Initialize the app bar.
        val appBarElevation = dimension(R.dimen.toolbar_elevation).toFloat()
        binding.toolbarMainButton.setOnClickListener {
            if (currentFragment is DetailFragment) {
                supportFragmentManager.popBackStack()
                updateMainToolbarButton(false)
            } else {
                hideKeyboard(currentFocus)
                binding.drawerLayout.openDrawer(Gravity.START)
            }
        }
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, _ -> ViewCompat.setElevation(appBarLayout, appBarElevation) }
        binding.appBarLayout.layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
        binding.coordinatorLayout.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        // Initialize the drawer layout.
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            expandAppBar()
            currentFragment?.onDrawerStateChanged(it)
            if (it == DrawerLayout.STATE_DRAGGING) {
                hideKeyboard(currentFocus)
            }
        })

        // Initialize the primary side navigation drawer.
        binding.primaryNavigation.disableScrollbars()
        (binding.primaryNavigation.getHeaderView(0)?.findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.primaryNavigation.setNavigationItemSelectedListener { menuItem ->
            if (currentScreenId == menuItem.itemId) {
                consumeAndCloseDrawers()
            } else {
                currentScreenId = menuItem.itemId
                return@setNavigationItemSelectedListener when (menuItem.itemId) {
                    R.id.library -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { LibraryFragment() } }
                    R.id.history -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { HistoryFragment() } }
                    R.id.options -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { OptionsFragment() } }
                    R.id.manage_playlists -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManagePlaylistsFragment() } }
                    R.id.manage_downloads -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManageDownloadsFragment() } }
                    else -> false
                }
            }
        }

        // Initialize the secondary side navigation drawer.
        binding.secondaryNavigation.disableScrollbars()
        binding.secondaryNavigation.setNavigationItemSelectedListener { currentFragment?.onNavigationItemSelected(it) ?: false }

        // Initialize the floating action button.
        binding.floatingActionButton.setOnClickListener { currentFragment?.onFloatingActionButtonPressed() }

        // Restore instance state if possible.
        if (savedInstanceState == null) {
            supportFragmentManager.handleReplace { LibraryFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.library)
        } else {
            binding.toolbarMainButton.setImageDrawable(drawable(if (savedInstanceState.isOnDetailScreen) R.drawable.ic_back_24dp else R.drawable.ic_menu_24dp))
            currentScreenId = savedInstanceState.currentScreenId
            if (currentScreenId == R.id.options) {
                binding.appBarLayout.run {
                    forceExpandAppBar = savedInstanceState.isAppBarExpanded
                    layoutTransition = null
                    post {
                        layoutTransition = LayoutTransition().apply { setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0) }
                    }
                }
            }
        }
        binding.drawerLayout.setDrawerLockMode(if (currentFragment is DetailFragment) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START)

        // Show the privacy consent dialog if needed.
        if (preferenceDatabase.shouldShowPrivacyPolicy) {
            AlertDialogFragment.show(
                DIALOG_ID_PRIVACY_POLICY,
                supportFragmentManager,
                R.string.home_privacy_policy_title,
                R.string.home_privacy_policy_message,
                R.string.home_privacy_policy_positive,
                R.string.home_privacy_policy_negative
            )
            preferenceDatabase.shouldShowPrivacyPolicy = false
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
                        updateMainToolbarButton(false)
                        super.onBackPressed()
                    } else {
                        if (preferenceDatabase.shouldShowExitConfirmation) {
                            AlertDialogFragment.show(
                                DIALOG_ID_EXIT_CONFIRMATION,
                                supportFragmentManager,
                                R.string.home_exit_confirmation_title,
                                R.string.home_exit_confirmation_message,
                                R.string.home_exit_confirmation_close,
                                R.string.cancel
                            )
                        } else {
                            onPositiveButtonSelected(DIALOG_ID_EXIT_CONFIRMATION)
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isOnDetailScreen = currentFragment is DetailFragment
        outState?.currentScreenId = currentScreenId
        outState?.isAppBarExpanded = binding.appBarLayout.height - binding.appBarLayout.bottom == 0
    }

    override fun onPositiveButtonSelected(id: Int) {
        when (id) {
            DIALOG_ID_EXIT_CONFIRMATION -> supportFinishAfterTransition()
            DIALOG_ID_PRIVACY_POLICY -> preferenceDatabase.shouldShareUsageData = true
        }
    }

    fun updateMainToolbarButton(shouldShowBackButton: Boolean) {
        fun changeDrawable() = binding.toolbarMainButton.setImageDrawable((if (shouldShowBackButton) drawableMenuToBack else drawableBackToMenu).apply { this?.start() })
        if (shouldShowBackButton) {
            changeDrawable()
        } else {
            binding.toolbarMainButton.postDelayed({ changeDrawable() }, 100)
        }
        binding.drawerLayout.setDrawerLockMode(if (shouldShowBackButton) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START)
    }

    fun updateToolbarTitle(toolbar: View) {
        binding.toolbarTitleContainer.removeAllViews()
        binding.toolbarTitleContainer.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
    }

    //TODO: Find a better way to enforce animations than delaying.
    fun updateToolbarButtons(buttons: List<View>) = binding.toolbarButtonContainer.run {
        fun addViews() = buttons.forEach { addView(it, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        if (childCount == 0) {
            if (binding.appBarLayout.height - binding.appBarLayout.bottom != 0) {
                addViews()
            } else {
                postDelayed({
                    removeAllViews()
                    addViews()
                }, 400)
            }
        }
    }

    fun enableSecondaryNavigationDrawer(@MenuRes menuResourceId: Int) {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
        binding.secondaryNavigation.menu.clear()
        binding.secondaryNavigation.inflateMenu(menuResourceId)
    }

    fun openSecondaryNavigationDrawer() {
        hideKeyboard(currentFocus)
        binding.drawerLayout.openDrawer(Gravity.END)
    }

    fun closeSecondaryNavigationDrawer() = binding.drawerLayout.closeDrawer(Gravity.END)

    fun enableFloatingActionButton() = binding.floatingActionButton.show()

    fun disableFloatingActionButton() = binding.autoScrollControl.run {
        if (animatedVisibilityEnd) {
            animatedVisibilityEnd = false
            (tag as? Animator)?.let {
                it.addListener(onAnimationEnd = {
                    binding.floatingActionButton.hide()
                    tag = null
                    visibleOrGone = false

                })
            }
        } else {
            binding.floatingActionButton.hide()
        }
    }

    fun updateFloatingActionButtonDrawable(drawable: Drawable?) = binding.floatingActionButton.setImageDrawable(drawable)

    fun enableTabLayout(viewPager: ViewPager) {
        binding.tabLayout.run {
            setupWithViewPager(viewPager)
            visibleOrGone = true
        }
    }

    fun expandAppBar() {
        binding.appBarLayout.setExpanded(forceExpandAppBar, forceExpandAppBar)
        forceExpandAppBar = true
    }

    fun beforeScreenChanged() {

        // Hide the keyboard.
        hideKeyboard(currentFocus)

        // Reset the app bar.
        binding.toolbarButtonContainer.removeAllViews()
        expandAppBar()

        // Reset the tab layout.
        binding.tabLayout.run {
            visibleOrGone = false
            setupWithViewPager(null)
        }

        // Reset the secondary navigation drawer.
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)
        binding.secondaryNavigation.menu.clear()

        // Reset the floating action button.
        disableFloatingActionButton()
    }

    fun openDetailScreen(clickedView: View, songs: List<Song>, index: Int = 0, shouldShowManagePlaylist: Boolean = true) {
        fun createTransition(delay: Long) = Explode().apply {
            propagation = null
            epicenterCallback = object : Transition.EpicenterCallback() {
                override fun onGetEpicenter(transition: Transition?) = Rect().apply { clickedView.getGlobalVisibleRect(this) }
            }
            startDelay = delay
        }
        currentFragment?.run {
            exitTransition = createTransition(0)
            reenterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
        }
        supportFragmentManager.beginTransaction()
            .setAllowOptimization(true)
            .replace(R.id.fragment_container, DetailFragment.newInstance(songs, index, shouldShowManagePlaylist))
            .addSharedElement(clickedView, clickedView.transitionName)
            .addToBackStack(null)
            .commit()
    }

    private inline fun <reified T : TopLevelFragment<*, *>> FragmentManager.handleReplace(crossinline newInstance: () -> T) {
        currentFragment?.exitTransition = null
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(T::class.java.name) ?: newInstance.invoke(), T::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private inline fun consumeAndCloseDrawers(crossinline action: () -> Unit = {}) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }

    private fun NavigationView.disableScrollbars() {
        (getChildAt(0) as? NavigationMenuView)?.isVerticalScrollBarEnabled = false
    }
}