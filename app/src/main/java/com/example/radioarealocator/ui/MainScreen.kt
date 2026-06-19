package com.example.radioarealocator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.db.HistoryRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LocationCard(
                isLoading = uiState.isLoading,
                result = uiState.result,
                hasPermission = viewModel.hasLocationPermission,
                onRequestPermission = onRequestPermission,
                onRefresh = { viewModel.refreshLocation() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.titleLarge
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HistoryList(history = history)
        }

        uiState.error?.let { message ->
            ErrorDialog(message = message, onDismiss = { viewModel.dismissError() })
        }
    }
}

@Composable
private fun LocationCard(
    isLoading: Boolean,
    result: LocationResult?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.locating))
                }

                result != null -> {
                    ResultContent(result = result)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) {
                        Text(stringResource(R.string.action_refresh))
                    }
                }

                else -> {
                    Text(
                        text = if (hasPermission) {
                            stringResource(R.string.tap_to_locate)
                        } else {
                            stringResource(R.string.location_permission_required)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = if (hasPermission) onRefresh else onRequestPermission) {
                        Text(
                            if (hasPermission) {
                                stringResource(R.string.action_refresh)
                            } else {
                                stringResource(R.string.grant_permission)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(result: LocationResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        InfoRow(label = stringResource(R.string.latitude), value = "%.5f".format(result.latitude))
        InfoRow(label = stringResource(R.string.longitude), value = "%.5f".format(result.longitude))
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(
            label = stringResource(R.string.cq_zone),
            value = result.cqZone?.toString() ?: "-"
        )
        InfoRow(
            label = stringResource(R.string.itu_zone),
            value = result.ituZone?.toString() ?: "-"
        )
        InfoRow(
            label = stringResource(R.string.maidenhead),
            value = result.maidenhead.uppercase()
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HistoryList(history: List<HistoryRecord>) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.empty_history),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(history, key = { it.id }) { record ->
            HistoryItem(record = record)
        }
    }
}

private val timeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

@Composable
private fun HistoryItem(record: HistoryRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = timeFormatter.format(record.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CQ ${record.cqZone ?: "-"} / ITU ${record.ituZone ?: "-"}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = record.maidenhead.uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "%.5f°, %.5f°".format(record.latitude, record.longitude),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text(stringResource(R.string.location_failed)) },
        text = { Text(message) }
    )
}
