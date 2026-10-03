// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.raghim.herz.feature.discovery.R
import com.raghim.herz.feature.discovery.model.ConnectError
import com.raghim.herz.feature.discovery.model.ConnectFormState
import com.raghim.herz.feature.discovery.model.DiscoveryIntent
import com.raghim.herz.feature.discovery.model.FormSource
import com.raghim.herz.feature.discovery.model.InputError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectSheet(
    form: ConnectFormState,
    onIntent: (DiscoveryIntent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(DiscoveryIntent.DismissForm) },
        sheetState = sheetState,
    ) {
        ConnectForm(
            form = form,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun ConnectForm(
    form: ConnectFormState,
    onIntent: (DiscoveryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !form.isTesting
    val submit = { onIntent(DiscoveryIntent.TestAndSave) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormHeader(form.source)

        when (form.source) {
            is FormSource.Device -> Unit
            FormSource.ManualHost -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.host,
                    onValueChange = { onIntent(DiscoveryIntent.HostChanged(it)) },
                    label = { Text(stringResource(R.string.discovery_field_host)) },
                    placeholder = { Text(stringResource(R.string.discovery_field_host_hint)) },
                    isError = form.inputError == InputError.HostRequired || form.inputError == InputError.InvalidHost,
                    supportingText = form.inputError.messageFor(InputError.HostRequired, InputError.InvalidHost),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = form.port,
                    onValueChange = { onIntent(DiscoveryIntent.PortChanged(it)) },
                    label = { Text(stringResource(R.string.discovery_field_port)) },
                    isError = form.inputError == InputError.InvalidPort,
                    supportingText = form.inputError.messageFor(InputError.InvalidPort),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            FormSource.ManualRtspUrl -> {
                OutlinedTextField(
                    value = form.mainUrl,
                    onValueChange = { onIntent(DiscoveryIntent.MainUrlChanged(it)) },
                    label = { Text(stringResource(R.string.discovery_field_main_url)) },
                    placeholder = { Text(stringResource(R.string.discovery_field_main_url_hint)) },
                    isError = form.inputError == InputError.InvalidMainUrl ||
                        form.inputError == InputError.CredentialsInUrl,
                    supportingText = form.inputError.messageFor(InputError.InvalidMainUrl, InputError.CredentialsInUrl),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = urlKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.subUrl,
                    onValueChange = { onIntent(DiscoveryIntent.SubUrlChanged(it)) },
                    label = { Text(stringResource(R.string.discovery_field_sub_url)) },
                    isError = form.inputError == InputError.InvalidSubUrl,
                    supportingText = {
                        val error = form.inputError?.takeIf { it == InputError.InvalidSubUrl }
                        Text(stringResource(error?.messageRes() ?: R.string.discovery_field_sub_url_help))
                    },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = urlKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        OutlinedTextField(
            value = form.name,
            onValueChange = { onIntent(DiscoveryIntent.NameChanged(it)) },
            label = { Text(stringResource(R.string.discovery_field_name)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.username,
            onValueChange = { onIntent(DiscoveryIntent.UsernameChanged(it)) },
            label = { Text(stringResource(R.string.discovery_field_username)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            value = form.password,
            enabled = enabled,
            onValueChange = { onIntent(DiscoveryIntent.PasswordChanged(it)) },
            onDone = submit,
        )
        Text(
            text = stringResource(R.string.discovery_login_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        form.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = submit,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (form.isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.discovery_testing), modifier = Modifier.padding(start = 12.dp))
            } else {
                Text(stringResource(R.string.discovery_test_and_save))
            }
        }
    }
}

@Composable
private fun FormHeader(source: FormSource) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val title = when (source) {
            is FormSource.Device -> source.device.displayName
            FormSource.ManualHost -> stringResource(R.string.discovery_form_ip_title)
            FormSource.ManualRtspUrl -> stringResource(R.string.discovery_form_url_title)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (source is FormSource.Device && source.device.displayName != source.device.host) {
            Text(
                text = source.device.host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.discovery_field_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(
                    stringResource(if (visible) R.string.discovery_hide_password else R.string.discovery_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val urlKeyboard = KeyboardOptions(
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)

/** Supporting text for a field, only when the current error belongs to that field. */
private fun InputError?.messageFor(vararg owned: InputError): (@Composable () -> Unit)? {
    val error = this?.takeIf { it in owned } ?: return null
    return { Text(stringResource(error.messageRes())) }
}

@StringRes
private fun InputError.messageRes(): Int = when (this) {
    InputError.HostRequired -> R.string.discovery_error_host_required
    InputError.InvalidHost -> R.string.discovery_error_invalid_host
    InputError.InvalidPort -> R.string.discovery_error_invalid_port
    InputError.InvalidMainUrl, InputError.InvalidSubUrl -> R.string.discovery_error_invalid_url
    InputError.CredentialsInUrl -> R.string.discovery_error_credentials_in_url
}

@StringRes
private fun ConnectError.messageRes(): Int = when (this) {
    ConnectError.WrongLogin -> R.string.discovery_error_wrong_login
    ConnectError.LoginRequired -> R.string.discovery_error_login_required
    ConnectError.Unreachable -> R.string.discovery_error_unreachable
    ConnectError.NoStreamFound -> R.string.discovery_error_no_stream
    ConnectError.Timeout -> R.string.discovery_error_timeout
    ConnectError.Offline -> R.string.discovery_error_offline
    ConnectError.Unknown -> R.string.discovery_error_unknown
}
