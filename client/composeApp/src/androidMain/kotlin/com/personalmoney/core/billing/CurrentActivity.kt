package com.personalmoney.core.billing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Whichever activity is in front, for the one thing that needs one.
 *
 * Play's purchase dialog is launched over an activity, and the object that launches it is a
 * singleton that outlives every activity there will ever be. Holding a strong reference
 * would leak one on every rotation, so this keeps a weak one and clears it as soon as the
 * activity stops being the visible one.
 *
 * Registered from [com.personalmoney.PersonalMoneyApplication]; nothing else should write it.
 */
object CurrentActivity {

    private var held: WeakReference<Activity>? = null

    /** Null when nothing of this app is on screen, which is a normal state, not a bug. */
    fun get(): Activity? = held?.get()

    fun track(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                held = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (held?.get() === activity) held = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (held?.get() === activity) held = null
            }
        })
    }
}
