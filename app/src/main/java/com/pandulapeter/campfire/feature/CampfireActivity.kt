package com.pandulapeter.campfire.feature

import android.app.ActivityManager
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.annotation.MenuRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.NavigationView
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.util.*

class CampfireActivity : AppCompatActivity() {
    private var Bundle.isOnDetailScreen by BundleArgumentDelegate.Boolean("isOnDetailScreen")
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as CampfireFragment<*>
    private val drawableMenuToBack by lazy { AnimatedVectorDrawableCompat.create(this, R.drawable.avd_menu_to_back_24dp) }
    private val drawableBackToMenu by lazy { AnimatedVectorDrawableCompat.create(this, R.drawable.avd_back_to_menu_24dp) }
    val floatingActionButton get() = binding.floatingActionButton
    val toolbarContext: Context get() = binding.toolbar.context

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
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
                R.id.settings -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { SettingsFragment() } }
                else -> false
            }
        }
        binding.secondaryNavigation.disableScrollbars()
        if (savedInstanceState == null) {
            supportFragmentManager.handleReplace { LibraryFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.library)
        } else {
            if (savedInstanceState.isOnDetailScreen) {
                binding.toolbarMainButton.setImageDrawable(drawable(R.drawable.ic_back_24dp))
            }
        }
        binding.toolbarMainButton.setOnClickListener {
            if (currentFragment is DetailFragment) {
                onBackPressed()
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
                if (!fragment.onBackPressed()) {
                    if (fragment is DetailFragment) {
                        transformMainToolbarButton(false)
                    }
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isOnDetailScreen = currentFragment is DetailFragment
    }

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

    fun openDetailScreen() {
        currentFocus?.also { hideKeyboard(it) }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailFragment(), DetailFragment::class.java.name)
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

    private inline fun <reified T : Fragment> FragmentManager.handleReplace(crossinline newInstance: () -> T) {
        currentFocus?.also { hideKeyboard(it) }
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(T::class.java.name) ?: newInstance.invoke(), T::class.java.name)
//            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
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