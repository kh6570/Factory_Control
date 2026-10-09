// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.alarms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.ui.HoldToReorderColumn
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.Sensor
import com.raghim.herz.core.ui.LoadingState
import com.raghim.herz.core.ui.MessageState
import kotlinx.coroutines.delay
import java.time.Instant

private const val NAME_LIMIT = 40
private const val AREA_LIMIT = 40

@Composable
internal fun SensorsRouteContent(
    viewModel: SensorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SensorsScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SensorsScreen(
    state: SensorsUiState,
    onIntent: (SensorsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startTest = rememberTestStarter(onIntent)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sensors_title)) },
                actions = {
                    IconButton(onClick = { onIntent(SensorsIntent.Add) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sensors_add))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.sensors.isEmpty() -> MessageState(
                title = stringResource(R.string.sensors_empty_title),
                body = stringResource(R.string.sensors_empty_body),
                actionLabel = stringResource(R.string.sensors_add),
                onAction = { onIntent(SensorsIntent.Add) },
                modifier = Modifier.padding(padding),
            )
            else -> SensorList(
                sensors = state.sensors,
                cameras = state.cameras,
                pendingUntil = state.pendingUntil,
                onIntent = onIntent,
                onStartTest = startTest,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
    when (val dialog = state.dialog) {
        SensorsDialog.Add -> SensorEditorDialog(
            title = stringResource(R.string.sensors_add_title),
            name = "",
            area = "",
            confirmLabel = stringResource(R.string.sensors_add),
            onConfirm = { name, area -> onIntent(SensorsIntent.Save(name, area)) },
            onDismiss = { onIntent(SensorsIntent.Dismiss) },
        )
        is SensorsDialog.Edit -> SensorEditorDialog(
            title = stringResource(R.string.sensors_edit_title),
            name = dialog.name,
            area = dialog.area,
            confirmLabel = stringResource(R.string.sensors_save),
            onConfirm = { name, area -> onIntent(SensorsIntent.Save(name, area)) },
            onDismiss = { onIntent(SensorsIntent.Dismiss) },
        )
        is SensorsDialog.Remove -> AlertDialog(
            onDismissRequest = { onIntent(SensorsIntent.Dismiss) },
            title = { Text(stringResource(R.string.sensors_remove_title)) },
            text = { Text(stringResource(R.string.sensors_remove_body, dialog.name)) },
            confirmButton = {
                TextButton(onClick = { onIntent(SensorsIntent.ConfirmRemove) }) {
                    Text(stringResource(R.string.sensors_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SensorsIntent.Dismiss) }) {
                    Text(stringResource(R.string.sensors_cancel))
                }
            },
        )
        is SensorsDialog.Highlight -> ChoiceDialog(
            title = stringResource(R.string.sensors_highlight_title),
            choices = listOf(
                Choice(
                    label = stringResource(R.string.sensors_highlight_on),
                    body = stringResource(R.string.sensors_highlight_on_body),
                    selected = dialog.enabled,
                    onClick = { onIntent(SensorsIntent.SetHighlight(true)) },
                ),
                Choice(
                    label = stringResource(R.string.sensors_highlight_off),
                    body = stringResource(R.string.sensors_highlight_off_body),
                    selected = !dialog.enabled,
                    onClick = { onIntent(SensorsIntent.SetHighlight(false)) },
                ),
            ),
            onDismiss = { onIntent(SensorsIntent.Dismiss) },
        )
        is SensorsDialog.Alarm -> ChoiceDialog(
            title = stringResource(R.string.sensors_alarm_title),
            choices = alarmChoices(dialog.style, onIntent),
            onDismiss = { onIntent(SensorsIntent.Dismiss) },
        )
        is SensorsDialog.Link -> LinkCamerasDialog(
            sensorName = dialog.name,
            cameras = state.cameras,
            initial = dialog.cameraIds,
            onConfirm = { onIntent(SensorsIntent.SaveLinks(it)) },
            onDismiss = { onIntent(SensorsIntent.Dismiss) },
        )
        null -> Unit
    }
}

@Composable
private fun rememberTestStarter(onIntent: (SensorsIntent) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var pendingId by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingId?.let { onIntent(SensorsIntent.StartTest(it)) }
        pendingId = null
    }
    return { sensorId ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingId = sensorId
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onIntent(SensorsIntent.StartTest(sensorId))
        }
    }
}

@Composable
private fun SensorList(
    sensors: List<Sensor>,
    cameras: List<Camera>,
    pendingUntil: Map<String, Instant>,
    onIntent: (SensorsIntent) -> Unit,
    onStartTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val known = remember(cameras) { cameras.map { it.id }.toSet() }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.sensors_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item(key = "rows") {
            HoldToReorderColumn(
                items = sensors,
                key = { it.id },
                onCommit = { rows -> onIntent(SensorsIntent.Move(rows.map { it.id })) },
            ) { sensor, drag ->
                SensorCard(
                    sensor = sensor,
                    linkedCount = sensor.linkedCameraIds.count { it in known },
                    firesAt = pendingUntil[sensor.id],
                    onIntent = onIntent,
                    onStartTest = { onStartTest(sensor.id) },
                    modifier = drag,
                )
            }
        }
    }
}

@Composable
private fun SensorCard(
    sensor: Sensor,
    linkedCount: Int,
    firesAt: Instant?,
    onIntent: (SensorsIntent) -> Unit,
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sensor.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sensor.area ?: stringResource(R.string.sensors_no_area),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SensorMenu(sensor = sensor, onIntent = onIntent)
            }
            Text(
                text = if (linkedCount == 0) {
                    stringResource(R.string.sensors_no_cameras)
                } else {
                    pluralStringResource(R.plurals.sensors_linked_cameras, linkedCount, linkedCount)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = sensorStyleLine(sensor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(SensorsIntent.OpenLinks(sensor.id)) }) {
                    Text(stringResource(R.string.sensors_link))
                }
                if (firesAt == null) {
                    FilledTonalButton(onClick = onStartTest) {
                        Text(stringResource(R.string.sensors_test))
                    }
                } else {
                    TestCountdown(firesAt = firesAt, onCancel = { onIntent(SensorsIntent.CancelTest(sensor.id)) })
                }
            }
        }
    }
}

@Composable
private fun TestCountdown(firesAt: Instant, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(firesAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val seconds = ((firesAt.toEpochMilli() - now) / 1000).toInt().coerceAtLeast(0)
    Column {
        Text(
            text = pluralStringResource(R.plurals.sensors_test_remaining, seconds, seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.sensors_cancel_test)) }
    }
}

@Composable
private fun SensorMenu(sensor: Sensor, onIntent: (SensorsIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sensors_more, sensor.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuRow(stringResource(R.string.sensors_edit)) {
                expanded = false
                onIntent(SensorsIntent.Edit(sensor.id))
            }
            MenuRow(
                label = stringResource(R.string.sensors_menu_highlight),
                detail = stringResource(
                    if (sensor.highlightOnAlarm) R.string.sensors_choice_on else R.string.sensors_choice_off,
                ),
            ) {
                expanded = false
                onIntent(SensorsIntent.OpenHighlight(sensor.id))
            }
            MenuRow(
                label = stringResource(R.string.sensors_menu_alarm),
                detail = alarmStyleLabel(sensor.alarmStyle),
            ) {
                expanded = false
                onIntent(SensorsIntent.OpenAlarm(sensor.id))
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sensors_remove), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onIntent(SensorsIntent.Remove(sensor.id))
                },
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    detail: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

private data class Choice(
    val label: String,
    val body: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ChoiceDialog(title: String, choices: List<Choice>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = choice.onClick)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.selected, onClick = choice.onClick)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = choice.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensors_close)) }
        },
    )
}

@Composable
private fun alarmChoices(current: AlarmStyle, onIntent: (SensorsIntent) -> Unit): List<Choice> = listOf(
    Choice(
        label = stringResource(R.string.sensors_alarm_vibration),
        body = stringResource(R.string.sensors_alarm_vibration_body),
        selected = current is AlarmStyle.VibrationOnly,
        onClick = { onIntent(SensorsIntent.SetAlarmStyle(AlarmStyle.VibrationOnly)) },
    ),
) + AlarmSound.entries.map { sound ->
    Choice(
        label = stringResource(sound.label()),
        body = stringResource(sound.body()),
        selected = current is AlarmStyle.Sound && current.sound == sound,
        onClick = { onIntent(SensorsIntent.SetAlarmStyle(AlarmStyle.Sound(sound))) },
    )
}

@Composable
private fun sensorStyleLine(sensor: Sensor): String = stringResource(
    R.string.sensors_style_line,
    stringResource(if (sensor.highlightOnAlarm) R.string.sensors_highlight_short_on else R.string.sensors_highlight_short_off),
    alarmStyleLabel(sensor.alarmStyle),
)

@Composable
private fun alarmStyleLabel(style: AlarmStyle): String = when (style) {
    AlarmStyle.VibrationOnly -> stringResource(R.string.sensors_alarm_vibration)
    is AlarmStyle.Sound -> stringResource(style.sound.label())
}

private fun AlarmSound.label(): Int = when (this) {
    AlarmSound.ALARM -> R.string.sensors_sound_alarm
    AlarmSound.RINGTONE -> R.string.sensors_sound_ringtone
    AlarmSound.NOTIFICATION -> R.string.sensors_sound_notification
}

private fun AlarmSound.body(): Int = when (this) {
    AlarmSound.ALARM -> R.string.sensors_sound_alarm_body
    AlarmSound.RINGTONE -> R.string.sensors_sound_ringtone_body
    AlarmSound.NOTIFICATION -> R.string.sensors_sound_notification_body
}

@Composable
private fun SensorEditorDialog(
    title: String,
    name: String,
    area: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName by rememberSaveable { mutableStateOf(name) }
    var draftArea by rememberSaveable { mutableStateOf(area) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(NAME_LIMIT) },
                    label = { Text(stringResource(R.string.sensors_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = draftArea,
                    onValueChange = { draftArea = it.take(AREA_LIMIT) },
                    label = { Text(stringResource(R.string.sensors_area)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draftName, draftArea) },
                enabled = draftName.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensors_cancel)) }
        },
    )
}

@Composable
private fun LinkCamerasDialog(
    sensorName: String,
    cameras: List<Camera>,
    initial: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(sensorName, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sensors_link_title, sensorName)) },
        text = {
            if (cameras.isEmpty()) {
                Text(stringResource(R.string.sensors_link_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(cameras, key = { it.id }) { camera ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = camera.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + camera.id else selected - camera.id
                                },
                            )
                            Text(camera.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = cameras.isNotEmpty()) {
                Text(stringResource(R.string.sensors_link_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensors_cancel)) }
        },
    )
}
