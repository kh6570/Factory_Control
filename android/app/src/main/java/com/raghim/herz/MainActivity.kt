// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.ui.HerzApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HerzTheme {
                HerzApp()
            }
        }
    }
}
