package com.personal.audioapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.audioapp.R
import com.personal.audioapp.auth.AuthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    signedIn: Boolean,
    accountEmail: String?,
    message: String?,
    onMessageShown: () -> Unit,
    onSave: (SettingsUiState) -> Unit,
    onConnectDrive: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var clientId by remember(state.clientId) { mutableStateOf(state.clientId) }
    var folderId by remember(state.driveFolderId) { mutableStateOf(state.driveFolderId) }
    var autoUpload by remember(state.autoUpload) { mutableStateOf(state.autoUpload) }
    var autoDelete by remember(state.autoDeleteAfterUpload) {
        mutableStateOf(state.autoDeleteAfterUpload)
    }
    var autoDeleteSource by remember(state.autoDeleteSourceAfterCompression) {
        mutableStateOf(state.autoDeleteSourceAfterCompression)
    }

    val redirectUri = AuthRepository.REDIRECT_URI.toString()

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_oauth_section),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text(stringResource(R.string.settings_client_id)) },
                placeholder = { Text(stringResource(R.string.settings_client_id_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = folderId,
                onValueChange = { folderId = it },
                label = { Text(stringResource(R.string.settings_folder_id)) },
                placeholder = { Text(stringResource(R.string.settings_folder_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            InfoCard(
                title = stringResource(R.string.settings_redirect_uri),
                value = redirectUri,
                onCopy = { clipboard.setText(AnnotatedString(redirectUri)) }
            )

            Text(
                text = stringResource(R.string.settings_oauth_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_account_section),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    !signedIn -> stringResource(R.string.settings_not_connected)
                    accountEmail.isNullOrBlank() -> stringResource(R.string.settings_connected)
                    else -> stringResource(R.string.settings_connected_as, accountEmail)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnectDrive) {
                    Text(
                        stringResource(
                            if (signedIn) R.string.action_reconnect else R.string.action_connect
                        )
                    )
                }
                if (signedIn) {
                    OutlinedButton(onClick = onSignOut) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_automation),
                style = MaterialTheme.typography.titleMedium
            )

            ToggleRow(
                title = stringResource(R.string.settings_auto_upload),
                subtitle = stringResource(R.string.settings_auto_upload_hint),
                checked = autoUpload,
                onCheckedChange = { autoUpload = it }
            )
            ToggleRow(
                title = stringResource(R.string.settings_auto_delete),
                subtitle = stringResource(R.string.settings_auto_delete_hint),
                checked = autoDelete,
                onCheckedChange = { autoDelete = it }
            )
            ToggleRow(
                title = stringResource(R.string.settings_auto_delete_source),
                subtitle = stringResource(R.string.settings_auto_delete_source_hint),
                checked = autoDeleteSource,
                onCheckedChange = { autoDeleteSource = it }
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    onSave(
                        SettingsUiState(
                            clientId = clientId.trim(),
                            driveFolderId = folderId.trim(),
                            autoUpload = autoUpload,
                            autoDeleteAfterUpload = autoDelete,
                            autoDeleteSourceAfterCompression = autoDeleteSource
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save_settings))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
