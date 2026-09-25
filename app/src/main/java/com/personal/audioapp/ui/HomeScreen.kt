package com.personal.audioapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.audioapp.R
import com.personal.audioapp.data.FileTask
import com.personal.audioapp.data.TaskStatus
import com.personal.audioapp.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<FileTask>,
    isRecording: Boolean,
    signedIn: Boolean,
    accountEmail: String?,
    message: String?,
    onMessageShown: () -> Unit,
    onToggleRecording: () -> Unit,
    onPickFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectDrive: () -> Unit,
    onRetry: (FileTask) -> Unit,
    onDelete: (FileTask) -> Unit,
    onClearCompleted: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleRecording,
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (isRecording) R.string.action_stop_recording
                        else R.string.action_start_recording
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DriveStatusCard(signedIn, accountEmail, onConnectDrive) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onToggleRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                if (isRecording) R.string.action_stop_recording
                                else R.string.action_new_recording
                            )
                        )
                    }

                    OutlinedButton(onClick = onPickFile) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_pick_file))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.queue_title, tasks.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(task = task, onRetry = { onRetry(task) }, onDelete = { onDelete(task) })
            }

            if (tasks.any { it.status == TaskStatus.COMPLETED }) {
                item {
                    TextButton(onClick = onClearCompleted) {
                        Text(stringResource(R.string.action_clear_completed))
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun DriveStatusCard(
    signedIn: Boolean,
    accountEmail: String?,
    onConnectDrive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (signedIn) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    if (signedIn) R.string.drive_connected_title
                    else R.string.drive_disconnected_title
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = accountEmail ?: stringResource(
                    if (signedIn) R.string.drive_account_linked else R.string.drive_connect_hint
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (!signedIn) {
                Button(onClick = onConnectDrive) {
                    Text(stringResource(R.string.action_connect_drive))
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: FileTask,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val busy = task.status == TaskStatus.COMPRESSING || task.status == TaskStatus.UPLOADING

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.task_meta,
                        FileUtils.readableSize(task.sizeBytes),
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            .format(Date(task.createdAt))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusChip(task.status)
                if (!task.error.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3
                    )
                }
                if (task.status == TaskStatus.COMPLETED && task.remoteId != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.task_drive_id, task.remoteId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (task.status == TaskStatus.FAILED || task.status == TaskStatus.PENDING) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_retry)
                        )
                    }
                }
                if (!busy) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: TaskStatus) {
    val label = stringResource(
        when (status) {
            TaskStatus.PENDING -> R.string.status_pending
            TaskStatus.COMPRESSING -> R.string.status_compressing
            TaskStatus.UPLOADING -> R.string.status_uploading
            TaskStatus.COMPLETED -> R.string.status_completed
            TaskStatus.FAILED -> R.string.status_failed
        }
    )

    val color = when (status) {
        TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
        TaskStatus.COMPRESSING -> Color(0xFFEF6C00)
        TaskStatus.UPLOADING -> Color(0xFF1565C0)
        TaskStatus.COMPLETED -> Color(0xFF2E7D32)
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
