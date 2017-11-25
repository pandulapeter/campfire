package com.pandulapeter.campfire

import android.app.Activity
import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import com.pandulapeter.campfire.ioc.app.DaggerAppComponent
import com.pandulapeter.campfire.util.color
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication

/**
 * Custom Application class for initializing dependency injection.
 */
class CampfireApplication : DaggerApplication() {

    override fun onCreate() {
        super.onCreate()

        // Customize the way the overview card looks on different build types.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
                if (BuildConfig.BUILD_TYPE != "release" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity?.setTaskDescription(ActivityManager.TaskDescription("${getString(R.string.campfire)} (${BuildConfig.BUILD_TYPE})",
                        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round),
                        color(R.color.primary)))
                }
            }

            override fun onActivityStarted(activity: Activity?) = Unit

            override fun onActivityResumed(activity: Activity?) = Unit

            override fun onActivityPaused(activity: Activity?) = Unit

            override fun onActivityStopped(activity: Activity?) = Unit

            override fun onActivityDestroyed(activity: Activity?) = Unit

            override fun onActivitySaveInstanceState(activity: Activity?, savedInstanceState: Bundle?) = Unit
        })
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> = DaggerAppComponent.builder().create(this)
}