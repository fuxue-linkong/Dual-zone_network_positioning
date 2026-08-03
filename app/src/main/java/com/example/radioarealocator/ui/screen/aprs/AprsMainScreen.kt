package com.example.radioarealocator.ui.screen.aprs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radioarealocator.ui.appViewModel
import com.example.radioarealocator.ui.navigation3.Route
import com.example.radioarealocator.ui.viewmodel.AprsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun AprsMainScreen(
    onNavigate: (Route) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val viewModel = appViewModel<AprsViewModel>()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()

    val isConnected = connectionState == AprsViewModel.ConnectionState.CONNECTED ||
        connectionState == AprsViewModel.ConnectionState.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // 顶栏：返回 + 标题 + 设置
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "APRS",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "设置",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onNavigate(Route.AprsSettings) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // 连接状态卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●",
                        style = MiuixTheme.textStyles.title1,
                        color = when (connectionState) {
                            AprsViewModel.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            AprsViewModel.ConnectionState.CONNECTING -> Color(0xFFFFC107)
                            AprsViewModel.ConnectionState.ERROR -> Color(0xFFF44336)
                            else -> Color(0xFF9E9E9E)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "状态: ${connectionState.name}",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.connect() },
                        modifier = Modifier.weight(1f),
                        enabled = !isConnected
                    ) { Text("连接") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.weight(1f),
                        enabled = isConnected
                    ) { Text("断开") }
                }

                lastError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "错误: $error",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 站点列表入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.AprsStations) }
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "站点列表",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stations.distinctBy { it.callsign.substringBefore("-") }.size} 个站点",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 地图入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.AprsMap) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "站点地图",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "在地图上查看站点位置 →",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "最近站点",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(stations.take(5)) { station ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = station.callsign,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.4f, %.4f",
                                station.latitude,
                                station.longitude
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
