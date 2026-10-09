// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.doors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.ui.HoldToReorderColumn
import com.raghim.herz.core.ui.LoadingState
import com.raghim.herz.core.ui.MessageState

private const val NAME_LIMIT = 40
private const val AREA_LIMIT = 40

@Composable
internal fun DoorsRouteContent(
    onOpenSettings: () -> Unit,
    viewModel: DoorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DoorsScreen(state = state, onIntent = viewModel::onIntent, onOpenSettings = onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoorsScreen(
    state: DoorsUiState,
    onIntent: (DoorsIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doors_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.doors_settings))
                    }
                    IconButton(onClick = { onIntent(DoorsIntent.Add) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.doors_add))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.doors.isEmpty() -> MessageState(
                title = stringResource(R.string.doors_empty_title),
                body = stringResource(R.string.doors_empty_body),
                actionLabel = stringResource(R.string.doors_add),
                onAction = { onIntent(DoorsIntent.Add) },
                modifier = Modifier.padding(padding),
            )
            else -> DoorList(state.doors, onIntent, Modifier.fillMaxSize().padding(padding))
        }
    }
    when (val dialog = state.dialog) {
        DoorsDialog.Add -> DoorEditorDialog(
            title = stringResource(R.string.doors_add_title),
            name = "",
            area = "",
            confirmLabel = stringResource(R.string.doors_add),
            onConfirm = { name, area -> onIntent(DoorsIntent.Save(name, area)) },
            onDismiss = { onIntent(DoorsIntent.Dismiss) },
        )
        is DoorsDialog.Edit -> DoorEditorDialog(
            title = stringResource(R.string.doors_edit_title),
            name = dialog.name,
            area = dialog.area,
            confirmLabel = stringResource(R.string.doors_save),
            onConfirm = { name, area -> onIntent(DoorsIntent.Save(name, area)) },
            onDismiss = { onIntent(DoorsIntent.Dismiss) },
        )
        is DoorsDialog.Remove -> AlertDialog(
            onDismissRequest = { onIntent(DoorsIntent.Dismiss) },
            title = { Text(stringResource(R.string.doors_remove_title)) },
            text = { Text(stringResource(R.string.doors_remove_body, dialog.name)) },
            confirmButton = {
                TextButton(onClick = { onIntent(DoorsIntent.ConfirmRemove) }) {
                    Text(stringResource(R.string.doors_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(DoorsIntent.Dismiss) }) { Text(stringResource(R.string.doors_cancel)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun DoorList(doors: List<Door>, onIntent: (DoorsIntent) -> Unit, modifier: Modifier = Modifier) {
    val onLive = doors.filter { it.onLivePanel }
    val available = doors.filter { !it.onLivePanel }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.doors_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectAllRow(
                allSelected = doors.all { it.onLivePanel },
                noneSelected = doors.none { it.onLivePanel },
                onSelectAll = { onIntent(DoorsIntent.ShowAllOnLive) },
                onDeselectAll = { onIntent(DoorsIntent.HideAllFromLive) },
                selectLabel = stringResource(R.string.doors_select_all),
                deselectLabel = stringResource(R.string.doors_deselect_all),
            )
        }
        if (onLive.isNotEmpty()) {
            item(key = "on_live_header") { SectionLabel(stringResource(R.string.doors_section_on_live)) }
            item(key = "on_live_rows") {
                HoldToReorderColumn(
                    items = onLive,
                    key = { it.id },
                    onCommit = { rows -> onIntent(DoorsIntent.Move(onLive = true, orderedIds = rows.map { it.id })) },
                ) { door, drag ->
                    DoorCard(door, onIntent, drag)
                }
            }
        }
        if (available.isNotEmpty()) {
            item(key = "available_header") { SectionLabel(stringResource(R.string.doors_section_available)) }
            item(key = "available_rows") {
                HoldToReorderColumn(
                    items = available,
                    key = { it.id },
                    onCommit = { rows -> onIntent(DoorsIntent.Move(onLive = false, orderedIds = rows.map { it.id })) },
                ) { door, drag ->
                    DoorCard(door, onIntent, drag)
                }
            }
        }
    }
}

@Composable
private fun SelectAllRow(
    allSelected: Boolean,
    noneSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    selectLabel: String,
    deselectLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll, enabled = !allSelected) { Text(selectLabel) }
        TextButton(onClick = onDeselectAll, enabled = !noneSelected) { Text(deselectLabel) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun DoorCard(door: Door, onIntent: (DoorsIntent) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = door.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = door.area ?: stringResource(R.string.doors_no_area),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DoorMenu(
                    doorName = door.name,
                    onEdit = { onIntent(DoorsIntent.Edit(door.id)) },
                    onRemove = { onIntent(DoorsIntent.Remove(door.id)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.doors_show_on_live), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = door.onLivePanel,
                    onCheckedChange = { onIntent(DoorsIntent.SetOnLive(door.id, it)) },
                )
            }
        }
    }
}

@Composable
private fun DoorMenu(doorName: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.doors_more, doorName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.doors_edit)) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = {
                expanded = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.doors_remove)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onRemove()
            },
        )
    }
    }
}

@Composable
private fun DoorEditorDialog(
    title: String,
    name: String,
    area: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameText by rememberSaveable(name) { mutableStateOf(name) }
    var areaText by rememberSaveable(area) { mutableStateOf(area) }
    val canSave = nameText.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it.take(NAME_LIMIT) },
                    label = { Text(stringResource(R.string.doors_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = areaText,
                    onValueChange = { areaText = it.take(AREA_LIMIT) },
                    label = { Text(stringResource(R.string.doors_area_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nameText, areaText) }, enabled = canSave) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.doors_cancel)) }
        },
    )
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun DoorsPreview() {
    HerzTheme(darkTheme = true) {
        DoorsScreen(
            state = DoorsUiState(
                doors = listOf(
                    Door("1", "Main gate", "Gate", contact = DoorContact.CLOSED, onLivePanel = true),
                    Door("2", "Loading bay", "Warehouse", contact = DoorContact.CLOSED),
                ),
                isLoading = false,
            ),
            onIntent = {},
            onOpenSettings = {},
        )
    }
}
