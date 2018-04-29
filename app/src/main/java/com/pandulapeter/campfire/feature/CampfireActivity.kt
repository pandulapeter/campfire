package com.pandulapeter.campfire.feature

import android.animation.Animator
import android.animation.LayoutTransition
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.annotation.MenuRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.AppBarLayout
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.view.ViewCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.transition.Explode
import android.view.Gravity
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.jakewharton.processphoenix.ProcessPhoenix
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.home.collections.detail.CollectionDetailFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.manageDownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.options.OptionsFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.PrivacyConsentDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class CampfireActivity : AppCompatActivity(), BaseDialogFragment.OnDialogItemSelectedListener, PlaylistRepository.Subscriber {

    companion object {
        private const val DIALOG_ID_EXIT_CONFIRMATION = 1
        private const val DIALOG_ID_PRIVACY_POLICY = 2
        const val SCREEN_LIBRARY = "library"
        const val SCREEN_COLLECTIONS = "collections"
        const val SCREEN_HISTORY = "history"
        const val SCREEN_OPTIONS = "options"
        const val SCREEN_MANAGE_PLAYLISTS = "managePlaylists"
        const val SCREEN_MANAGE_DOWNLOADS = "manageDownloads"

        private var Intent.screenToOpen by IntentExtraDelegate.String("screenToOpen")

        private fun getStartIntent(context: Context) = Intent(context, CampfireActivity::class.java)

        fun getLibraryIntent(context: Context) = getStartIntent(context).apply { screenToOpen = SCREEN_LIBRARY }

        fun getCollectionsIntent(context: Context) = getStartIntent(context).apply { screenToOpen = SCREEN_COLLECTIONS }

        fun getPlaylistIntent(context: Context, playlistId: String) = getStartIntent(context).apply { screenToOpen = playlistId }
    }

    private var Bundle.currentScreenId by BundleArgumentDelegate.Int("currentScreenId")
    private var Bundle.currentPlaylistId by BundleArgumentDelegate.String("currentPlaylistId")
    private var Bundle.currentCollectionId by BundleArgumentDelegate.String("currentCollectionId")
    private var Bundle.isAppBarExpanded by BundleArgumentDelegate.Boolean("isAppBarExpanded")
    private var Bundle.toolbarContainerScrollFlags by BundleArgumentDelegate.Boolean("shouldAllowAppBarScrolling")
    private var Bundle.lastSongId by BundleArgumentDelegate.String("lastSongId")
    private var Bundle.lastColelctionId by BundleArgumentDelegate.String("lastCollectionId")
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? TopLevelFragment<*, *>?
    private val drawableMenuToBack by lazy { animatedDrawable(R.drawable.avd_menu_to_back_24dp) }
    private val drawableBackToMenu by lazy { animatedDrawable(R.drawable.avd_back_to_menu_24dp) }
    private val appShortcutManager by inject<AppShortcutManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val playlistRepository by inject<PlaylistRepository>()
    private var currentPlaylistId = ""
    private var currentCollectionId = ""
    private var currentScreenId = R.id.library
    private var forceExpandAppBar = true
    private val colorWhite by lazy { color(R.color.white) }
    private val playlistsContainerItem by lazy { binding.primaryNavigation.menu.findItem(R.id.playlists).subMenu }
    private val playlistIdMap = mutableMapOf<Int, String>()
    private var newPlaylistId = 0
    private var startTime = 0L
    private val isBackStackEmpty get() = supportFragmentManager.backStackEntryCount == 0
    var lastSongId: String = ""
    var lastCollectionId: String = ""
    val autoScrollControl get() = binding.autoScrollControl
    val toolbarContext get() = binding.appBarLayout.context!!
    val secondaryNavigationMenu get() = binding.secondaryNavigation.menu ?: throw IllegalStateException("The secondary navigation drawer has no menu inflated.")
    val snackbarRoot get() = binding.rootCoordinatorLayout
    var shouldAllowAppBarScrolling
        get() = (binding.toolbarContainer.layoutParams as AppBarLayout.LayoutParams).scrollFlags != 0
        set(value) {
            if (shouldAllowAppBarScrolling != value) {
                // Seems to cause glitches on older Android versions.
                binding.toolbarContainer.run {
                    layoutParams = (layoutParams as AppBarLayout.LayoutParams).apply {
                        scrollFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && value) {
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                        } else 0
                    }
                }
            }
        }
    var transitionMode: Boolean? = null
        set(value) {
            if (field != value) {
                when (value) {
                    true -> {
                        binding.appBarLayout.layoutTransition = LayoutTransition().apply {
                            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
                        }
                        binding.coordinatorLayout.layoutTransition = LayoutTransition().apply {
                            enableTransitionType(LayoutTransition.CHANGING)
                        }
                    }
                    false -> {
                        binding.appBarLayout.layoutTransition = LayoutTransition().apply {
                            disableTransitionType(LayoutTransition.DISAPPEARING)
                            enableTransitionType(LayoutTransition.CHANGING)
                            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
                        }
                        binding.coordinatorLayout.layoutTransition = LayoutTransition().apply {
                            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
                        }
                    }
                    null -> {
                        binding.appBarLayout.layoutTransition = null
                        binding.coordinatorLayout.layoutTransition = null
                    }
                }
                field = value
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Set the theme and the task description.
        setTheme(if (preferenceDatabase.shouldUseDarkTheme) R.style.DarkTheme else R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityManager.TaskDescription(
                    getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                    R.mipmap.ic_launcher_foreground,
                    color(R.color.primary)
                )
            } else {
                @Suppress("DEPRECATION")
                ActivityManager.TaskDescription(
                    getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground),
                    color(R.color.primary)
                )
            }
        )
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()

        // Initialize the app bar.
        val appBarElevation = dimension(R.dimen.toolbar_elevation).toFloat()
        binding.toolbarMainButton.setOnClickListener {
            if (isBackStackEmpty) {
                hideKeyboard(currentFocus)
                binding.drawerLayout.openDrawer(Gravity.START)
            } else {
                supportFragmentManager.popBackStack()
            }
        }
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, _ -> ViewCompat.setElevation(appBarLayout, appBarElevation) }

        // Initialize the drawer layout.
        binding.drawerLayout.addDrawerListener(
            onDrawerStateChanged = {
                if (it == DrawerLayout.STATE_DRAGGING) {
                    expandAppBar()
                }
                currentFragment?.onDrawerStateChanged(it)
                if (it == DrawerLayout.STATE_DRAGGING) {
                    hideKeyboard(currentFocus)
                }
            })

        // Initialize the primary side navigation drawer.
        binding.primaryNavigation.disableScrollbars()
        val headerView = binding.primaryNavigation.getHeaderView(0)
        (headerView?.findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.rootCoordinatorLayout.insetChangeListener = {
            headerView?.apply { setPadding(paddingStart, it, paddingEnd, paddingBottom) }
        }
        binding.primaryNavigation.setNavigationItemSelectedListener { menuItem ->
            if (currentScreenId == menuItem.itemId) {
                consumeAndCloseDrawers()
            } else {
                if (menuItem.itemId != newPlaylistId) {
                    currentScreenId = menuItem.itemId
                }
                return@setNavigationItemSelectedListener when (menuItem.itemId) {
                    R.id.library -> consumeAndCloseDrawers {
                        appShortcutManager.onLibraryOpened()
                        supportFragmentManager.handleReplace { LibraryFragment() }
                    }
                    R.id.collections -> consumeAndCloseDrawers {
                        supportFragmentManager.handleReplace {
                            appShortcutManager.onCollectionsOpened()
                            CollectionsFragment()
                        }
                    }
                    R.id.history -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { HistoryFragment() } }
                    R.id.options -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { OptionsFragment() } }
                    R.id.manage_playlists -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManagePlaylistsFragment() } }
                    R.id.manage_downloads -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManageDownloadsFragment() } }
                    newPlaylistId -> {
                        currentFragment?.hideSnackbar()
                        NewPlaylistDialogFragment.show(supportFragmentManager)
                        binding.drawerLayout.closeDrawers()
                        false
                    }
                    else -> consumeAndCloseDrawers { playlistIdMap[menuItem.itemId]?.let { openPlaylistScreen(it) } }
                }
            }
        }

        // Initialize the secondary side navigation drawer.
        binding.secondaryNavigation.disableScrollbars()
        binding.secondaryNavigation.setNavigationItemSelectedListener { currentFragment?.onNavigationItemSelected(it) ?: false }

        // Initialize the floating action button.
        binding.floatingActionButton.setOnClickListener {
            if (binding.autoScrollControl.tag == null && binding.floatingActionButton.isShown && it.tag == null) {
                currentFragment?.onFloatingActionButtonPressed()
            }
        }

        // Restore instance state if possible.
        if (savedInstanceState == null) {
            handleNewIntent()
        } else {
            currentScreenId = savedInstanceState.currentScreenId
            currentPlaylistId = savedInstanceState.currentPlaylistId
            currentCollectionId = savedInstanceState.currentCollectionId
            lastSongId = savedInstanceState.lastSongId
            lastCollectionId = savedInstanceState.lastColelctionId
            shouldAllowAppBarScrolling = savedInstanceState.toolbarContainerScrollFlags
            if (currentScreenId == R.id.options) {
                forceExpandAppBar = savedInstanceState.isAppBarExpanded
            }
        }
        binding.drawerLayout.setDrawerLockMode(
            if (currentFragment is DetailFragment || currentFragment is CollectionDetailFragment) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED,
            Gravity.START
        )

        // Show the privacy consent dialog if needed.
        if (preferenceDatabase.shouldShowPrivacyPolicy) {
            PrivacyConsentDialogFragment.show(DIALOG_ID_PRIVACY_POLICY, supportFragmentManager)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent
        handleNewIntent()
    }

    override fun onResume() {
        super.onResume()
        playlistRepository.subscribe(this)
        if (currentFocus is EditText) {
            binding.drawerLayout.run { post { closeDrawers() } }
        }
    }

    override fun onPause() {
        super.onPause()
        playlistRepository.unsubscribe(this)
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
                    if (isBackStackEmpty) {
                        if (preferenceDatabase.shouldShowExitConfirmation) {
                            AlertDialogFragment.show(
                                id = DIALOG_ID_EXIT_CONFIRMATION,
                                fragmentManager = supportFragmentManager,
                                title = R.string.home_exit_confirmation_title,
                                message = R.string.home_exit_confirmation_message,
                                positiveButton = R.string.home_exit_confirmation_close,
                                negativeButton = R.string.cancel
                            )
                        } else {
                            onPositiveButtonSelected(DIALOG_ID_EXIT_CONFIRMATION)
                        }
                    } else {
                        super.onBackPressed()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.currentScreenId = currentScreenId
        outState?.isAppBarExpanded = binding.appBarLayout.height - binding.appBarLayout.bottom == 0
        outState?.toolbarContainerScrollFlags = shouldAllowAppBarScrolling
        outState?.currentPlaylistId = playlistIdMap[currentScreenId] ?: ""
        outState?.currentCollectionId = currentCollectionId
        outState?.lastSongId = lastSongId
        outState?.lastColelctionId = lastCollectionId
    }

    override fun onPositiveButtonSelected(id: Int) {
        when (id) {
            DIALOG_ID_EXIT_CONFIRMATION -> supportFinishAfterTransition()
            DIALOG_ID_PRIVACY_POLICY -> {
                preferenceDatabase.shouldShowPrivacyPolicy = false
                preferenceDatabase.shouldShareUsageData = true
                restartProcess()
            }
        }
    }

    override fun onNegativeButtonSelected(id: Int) {
        if (id == DIALOG_ID_PRIVACY_POLICY) {
            preferenceDatabase.shouldShowPrivacyPolicy = false
        }
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) = updatePlaylists(playlists)

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = updatePlaylists(playlists)

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) = Unit

    override fun onSongRemovedFromAllPlaylists(songId: String) = Unit

    fun invalidateAppBar() = binding.toolbarContainer.requestLayout()

    fun updateAppBarView(view: View?, immediately: Boolean = false) {
        binding.appBarLayout.apply {
            fun removeViews() {
                while (childCount > 1) {
                    removeView(getChildAt(1))
                }
            }
            if (view == null) {
                if (childCount > 1) {
                    if (currentFragment is LibraryFragment || currentFragment is DetailFragment) {
                        post { removeViews() }
                    } else {
                        postDelayed({
                            transitionMode = true
                            removeViews()
                        }, 150)
                    }
                }
            } else {
                if (getChildAt(1) != view) {
                    if (immediately || System.currentTimeMillis() - startTime < 800) {
                        layoutTransition = null
                        removeViews()
                        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        transitionMode = true
                    } else {
                        postDelayed({
                            transitionMode = true
                            removeViews()
                            addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        }, 250)
                    }
                }
            }
        }
    }

    fun expandAppBar() {
        binding.appBarLayout.setExpanded(forceExpandAppBar, forceExpandAppBar)
        forceExpandAppBar = true
    }

    private fun updateMainToolbarButton(shouldShowBackButton: Boolean) {
        binding.toolbarMainButton.run {
            setImageDrawable(if (if (tag == null) false else tag != shouldShowBackButton) {
                (if (shouldShowBackButton) drawableMenuToBack else drawableBackToMenu).apply { this?.start() }
            } else {
                drawable(if (shouldShowBackButton) R.drawable.ic_back_24dp else R.drawable.ic_menu_24dp)
            })
            tag = shouldShowBackButton
        }
    }

    fun updateToolbarTitleView(toolbar: View, width: Int = 0) {
        val oldView = binding.toolbarTitleContainer.run {
            while (childCount > 1) {
                removeViewAt(1)
            }
            if (childCount > 0) getChildAt(0) else null
        }
        if (toolbar != oldView) {
            binding.toolbarTitleContainer.addView(
                toolbar.apply { visibleOrGone = oldView?.id == R.id.default_toolbar },
                FrameLayout.LayoutParams(if (width == 0) ViewGroup.LayoutParams.MATCH_PARENT else width, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
            oldView?.run {
                visibleOrGone = false
                postOnAnimation { binding.toolbarTitleContainer.removeView(this) }
            }
        }
        toolbar.run { postOnAnimation { visibleOrGone = true } }
    }

    fun updateToolbarButtons(buttons: List<View>) = binding.toolbarButtonContainer.run {
        val size = dimension(R.dimen.toolbar_button_size)
        if (childCount == 0) {
            buttons.forEach { addView(it, size, size) }
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

    fun isFloatingActionButtonEnabled() = binding.floatingActionButton.visibleOrInvisible

    fun enableFloatingActionButton() {
        binding.floatingActionButton.show()
        binding.autoScrollControl.run {
            (tag as? Animator)?.let {
                it.addListener(onAnimationEnd = { binding.floatingActionButton.show() })
            }
        }
        binding.floatingActionButton.tag = null
    }

    fun disableFloatingActionButton() = binding.autoScrollControl.run {
        binding.floatingActionButton.tag = "Hiding"
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

    fun updateFloatingActionButtonDrawable(drawable: Drawable?) = binding.floatingActionButton.setImageDrawable(drawable.apply { this?.setTint(colorWhite) })

    fun beforeScreenChanged() {

        // Hide the keyboard.
        hideKeyboard(currentFocus)

        // Reset the app bar.
        transitionMode = false
        binding.toolbarButtonContainer.removeAllViews()
        expandAppBar()
        updateMainToolbarButton(!isBackStackEmpty)

        // Reset the primary navigation drawer.
        binding.drawerLayout.setDrawerLockMode(if (isBackStackEmpty) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.START)

        // Reset the secondary navigation drawer.
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)
        binding.secondaryNavigation.menu.clear()

        // Reset the floating action button.
        disableFloatingActionButton()
    }

    private fun handleNewIntent() {
        if (currentFragment is DetailFragment) {
            if (intent.screenToOpen.isEmpty()) {
                return
            } else {
                supportFragmentManager.popBackStackImmediate()
            }
        }
        when (intent.screenToOpen) {
            "" -> preferenceDatabase.lastScreen.let {
                when (it) {
                    "" -> openLibraryScreen()
                    SCREEN_LIBRARY -> openLibraryScreen()
                    SCREEN_COLLECTIONS -> openCollectionsScreen()
                    SCREEN_HISTORY -> openHistoryScreen()
                    SCREEN_OPTIONS -> openOptionsScreen()
                    SCREEN_MANAGE_PLAYLISTS -> openManagePlaylistsScreen()
                    SCREEN_MANAGE_DOWNLOADS -> openManageDownloadsScreen()
                    else -> openPlaylistScreen(it)
                }
            }
            SCREEN_LIBRARY -> openLibraryScreen()
            SCREEN_COLLECTIONS -> openCollectionsScreen()
            else -> openPlaylistScreen(intent.screenToOpen)
        }
    }

    fun openLibraryScreen() {
        if (currentFragment !is LibraryFragment) {
            supportFragmentManager.handleReplace { LibraryFragment() }
            currentScreenId = R.id.library
            binding.primaryNavigation.setCheckedItem(R.id.library)
            appShortcutManager.onLibraryOpened()
        }
    }

    private fun openCollectionsScreen() {
        if (currentFragment !is CollectionsFragment) {
            supportFragmentManager.handleReplace { CollectionsFragment() }
            currentScreenId = R.id.collections
            binding.primaryNavigation.setCheckedItem(R.id.collections)
            appShortcutManager.onCollectionsOpened()
        }
    }

    fun openCollectionDetailsScreen(collection: Collection, clickedView: View?, shouldExplode: Boolean) {
        lastCollectionId = collection.id
        if (currentFragment !is CollectionDetailFragment || currentCollectionId != collection.id) {
            currentCollectionId = collection.id
            fun createTransition(delay: Long) = Explode().apply {
                propagation = null
                startDelay = delay
                duration = DetailFragment.TRANSITION_DURATION
            }
            currentFragment?.run {
                if (shouldExplode) {
                    exitTransition = createTransition(0)
                    reenterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
                } else {
                    exitTransition = null
                    reenterTransition = null
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CollectionDetailFragment.newInstance(collection))
                .apply {
                    if (clickedView == null) {
                        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    } else {
                        setAllowOptimization(true)
                        clickedView.transitionName = getString(R.string.campfire)
                        addSharedElement(clickedView, clickedView.transitionName)
                    }
                }
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openHistoryScreen() {
        if (currentFragment !is HistoryFragment) {
            supportFragmentManager.handleReplace { HistoryFragment() }
            currentScreenId = R.id.history
            binding.primaryNavigation.setCheckedItem(R.id.history)
        }
    }

    private fun openOptionsScreen() {
        if (currentFragment !is OptionsFragment) {
            supportFragmentManager.handleReplace { OptionsFragment() }
            currentScreenId = R.id.options
            binding.primaryNavigation.setCheckedItem(R.id.options)
        }
    }

    fun openPlaylistScreen(playlistId: String) {
        if (currentFragment !is PlaylistFragment || currentPlaylistId != playlistId) {
            playlistIdMap.forEach {
                if (it.value == playlistId) {
                    currentScreenId = it.key
                    binding.primaryNavigation.setCheckedItem(it.key)
                }
            }
            currentPlaylistId = playlistId
            appShortcutManager.onPlaylistOpened(playlistId)
            supportFragmentManager.handleReplace("${PlaylistFragment::class.java.simpleName}-$playlistId") { PlaylistFragment.newInstance(playlistId) }
        }
    }

    private fun openManagePlaylistsScreen() {
        if (currentFragment !is ManagePlaylistsFragment) {
            currentScreenId = R.id.manage_playlists
            supportFragmentManager.handleReplace { ManagePlaylistsFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.manage_playlists)
        }
    }

    private fun openManageDownloadsScreen() {
        if (currentFragment !is ManageDownloadsFragment) {
            currentScreenId = R.id.manage_downloads
            supportFragmentManager.handleReplace { ManageDownloadsFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.manage_downloads)
        }
    }

    fun openDetailScreen(clickedView: View?, songs: List<Song>, shouldExplode: Boolean, index: Int, shouldShowManagePlaylist: Boolean) {
        lastSongId = songs[index].id
        fun createTransition(delay: Long) = Explode().apply {
            propagation = null
            startDelay = delay
            duration = DetailFragment.TRANSITION_DURATION
        }
        currentFragment?.run {
            if (this !is PlaylistFragment) {
                currentPlaylistId = ""
            }
            if (this !is CollectionDetailFragment) {
                currentCollectionId = ""
            }
            if (shouldExplode) {
                exitTransition = createTransition(0)
                reenterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
            } else {
                exitTransition = null
                reenterTransition = null
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailFragment.newInstance(songs, index, shouldShowManagePlaylist, clickedView == null))
            .apply {
                if (clickedView == null) {
                    setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                } else {
                    setAllowOptimization(true)
                    addSharedElement(clickedView, clickedView.transitionName)
                }
            }
            .addToBackStack(null)
            .commit()
    }

    fun restartProcess() {
        currentFragment?.showSnackbar(
            message = R.string.options_preferences_share_usage_data_restart_hint,
            actionText = R.string.options_preferences_share_usage_data_restart_action,
            action = { ProcessPhoenix.triggerRebirth(this, getStartIntent(this)) })
    }

    private fun updatePlaylists(playlists: List<Playlist>) {
        fun SubMenu.addPlaylistItem(index: Int, id: Int, title: String, shouldUseAddIcon: Boolean = false) =
            add(R.id.playlist_container, id, index, title).run {
                setIcon(if (shouldUseAddIcon) R.drawable.ic_new_playlist_24dp else R.drawable.ic_playlist_24dp)
            }

        val isLookingForUpdatedId = currentFragment is PlaylistFragment || currentFragment is DetailFragment
        playlistsContainerItem.run {
            clear()
            playlistIdMap.clear()
            playlists
                .asSequence()
                .sortedBy { it.order }
                .filter { it.id != playlistRepository.hiddenPlaylistId }
                .forEachIndexed { index, playlist ->
                    val id = View.generateViewId()
                    playlistIdMap[id] = playlist.id
                    if (isLookingForUpdatedId && playlist.id == currentPlaylistId) {
                        currentScreenId = id
                        binding.primaryNavigation.run { post { setCheckedItem(id) } }
                    }
                    addPlaylistItem(index, id, playlist.title ?: getString(R.string.home_favorites))
                }
            if (playlists.size < Playlist.MAXIMUM_PLAYLIST_COUNT) {
                newPlaylistId = View.generateViewId()
                addPlaylistItem(playlists.size, newPlaylistId, getString(R.string.home_new_playlist), true)
            }
            setGroupCheckable(R.id.playlist_container, true, true)
            appShortcutManager.updateAppShortcuts()
        }
    }

    private inline fun <reified T : TopLevelFragment<*, *>> FragmentManager.handleReplace(tag: String = T::class.java.name, crossinline newInstance: () -> T) {
        currentFragment?.exitTransition = null
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(tag) ?: newInstance.invoke(), tag)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
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