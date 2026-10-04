// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.security.BiometricHost
import com.raghim.herz.ui.HerzApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A [FragmentActivity] because `BiometricPrompt` needs one (door opening, spec D11). */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var biometricHost: BiometricHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricHost.attach(this)
        enableEdgeToEdge()
        setContent {
            HerzTheme {
                HerzApp()
            }
        }
    }
}
