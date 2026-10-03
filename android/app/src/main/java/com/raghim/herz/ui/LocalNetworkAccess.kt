// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.raghim.herz.R

/** Android 17 blocks LAN sockets (RTSP, WS-Discovery) for apps targeting API 37 without this. */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

private fun Context.hasLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
        ContextCompat.checkSelfPermission(this, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Asks once on start; while denied, shows a banner that explains why cameras cannot load. */
@Composable
fun LocalNetworkAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocalNetworkAccess()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LifecycleResumeEffect(Unit) {
        granted = context.hasLocalNetworkAccess()
        onPauseOrDispose { }
    }
    LaunchedEffect(granted) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(ACCESS_LOCAL_NETWORK)
        }
    }

    if (granted) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.local_network_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { launcher.launch(ACCESS_LOCAL_NETWORK) }) {
                    Text(stringResource(R.string.local_network_allow))
                }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) {
                    Text(stringResource(R.string.local_network_settings))
                }
            }
        }
    }
}
