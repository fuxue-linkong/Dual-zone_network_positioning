package com.example.radioarealocator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.satellite.SatelliteInfo
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState
    val history by viewModel.history.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    BackHandler(enabled = showAbout || showHistory) {
        when {
            showAbout -> showAbout = false
            showHistory -> showHistory = false
        }
    }

    when {
        showAbout -> {
            AboutScreen(onBackClick = { showAbout = false })
            return
        }

        showHistory -> {
            HistoryScreen(
                history = history,
                onClearHistory = { viewModel.clearHistory() },
                onBackClick = { showHistory = false }
            )
            return
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showAbout = true }) {
                        Text(
                            text = stringResource(R.string.about),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocationCard(
                isLoading = uiState.isLoading,
                result = uiState.result,
                hasPermission = viewModel.hasLocationPermission,
                onRequestPermission = onRequestPermission,
                onRefresh = { viewModel.refreshLocation() }
            )

            SatelliteCard(
                isLoading = uiState.isLoading,
                satellites = uiState.satellites,
                satelliteError = uiState.satelliteError,
                hasLocation = uiState.result != null
            )

            HistoryEntryCard(onClick = { showHistory = true })
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
private fun HistoryEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.titleMedium
            )
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
private fun SatelliteCard(
    isLoading: Boolean,
    satellites: List<SatelliteInfo>,
    satelliteError: String?,
    hasLocation: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nearby_satellites),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (satellites.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.satellite_count, satellites.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading && satellites.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                satelliteError != null -> {
                    Text(
                        text = stringResource(R.string.satellite_load_failed, satelliteError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                !hasLocation -> {
                    Text(
                        text = stringResource(R.string.satellite_need_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                satellites.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_satellites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    satellites.forEachIndexed { index, sat ->
                        SatelliteRow(satellite = sat)
                        if (index < satellites.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private val satelliteTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    val startTime = satellite.aosTime.atZone(ZoneId.systemDefault())
        .format(satelliteTimeFormatter)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = satellite.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${stringResource(R.string.max_elevation)} ${satellite.maxElevation.toInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                satellite.modes.forEach { mode ->
                    ModeChip(mode = mode)
                }
            }
            Text(
                text = "${stringResource(R.string.aos_time)} $startTime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeChip(mode: String) {
    val color = when (mode.uppercase()) {
        "FM" -> MaterialTheme.colorScheme.primaryContainer
        "SSTV" -> MaterialTheme.colorScheme.secondaryContainer
        "DSTAR" -> MaterialTheme.colorScheme.tertiaryContainer
        "CW" -> MaterialTheme.colorScheme.errorContainer
        "USB", "LSB" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (mode.uppercase()) {
        "FM" -> MaterialTheme.colorScheme.onPrimaryContainer
        "SSTV" -> MaterialTheme.colorScheme.onSecondaryContainer
        "DSTAR" -> MaterialTheme.colorScheme.onTertiaryContainer
        "CW" -> MaterialTheme.colorScheme.onErrorContainer
        "USB", "LSB" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .background(color = color, shape = MaterialTheme.shapes.small)
    ) {
        Text(
            text = mode,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
