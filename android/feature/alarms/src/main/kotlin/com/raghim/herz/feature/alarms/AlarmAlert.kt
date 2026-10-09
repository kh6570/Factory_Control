// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raghim.herz.core.domain.sensor.DismissAlarmUseCase
import com.raghim.herz.core.domain.sensor.ObserveRingingAlarmUseCase
import com.raghim.herz.core.model.RingingAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlarmAlertViewModel @Inject constructor(
    observeRinging: ObserveRingingAlarmUseCase,
    private val dismissAlarm: DismissAlarmUseCase,
) : ViewModel() {
    val alarm: StateFlow<RingingAlarm?> = observeRinging()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stops the sound. Cameras stay on the wall. */
    fun stop() = dismissAlarm()
}

/** Shown over every screen while a sensor alarm is ringing. */
@Composable
fun AlarmAlertOverlay(
    onWatchLive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmAlertViewModel = hiltViewModel(),
) {
    val alarm by viewModel.alarm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(alarm != null) {
        val activity = context.findActivity()
        if (activity != null) activity.setVisibleOverLock(alarm != null)
        onDispose {
            context.findActivity()?.setVisibleOverLock(false)
        }
    }
    val current = alarm ?: return
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = current.sensorName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.alarm_detected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::stop) {
                    Text(stringResource(R.string.alarm_stop))
                }
                TextButton(
                    onClick = {
                        viewModel.stop()
                        onWatchLive()
                    },
                ) {
                    Text(stringResource(R.string.alarm_watch_live))
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.setVisibleOverLock(visible: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(visible)
        setTurnScreenOn(visible)
    } else {
        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (visible) window.addFlags(flags) else window.clearFlags(flags)
    }
}
