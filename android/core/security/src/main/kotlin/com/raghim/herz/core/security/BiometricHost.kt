// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The activity that hosts the biometric prompt. `MainActivity` attaches itself, so ViewModels
 * can call [BiometricDoorSigner] like any suspend function without holding an activity.
 */
@Singleton
class BiometricHost @Inject constructor() {
    private var current: WeakReference<FragmentActivity>? = null

    val activity: FragmentActivity?
        get() = current?.get()

    fun attach(activity: FragmentActivity) {
        current = WeakReference(activity)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (current?.get() === activity) current = null
            }
        })
    }
}
