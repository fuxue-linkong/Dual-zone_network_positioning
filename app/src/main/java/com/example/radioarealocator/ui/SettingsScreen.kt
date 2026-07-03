package com.example.radioarealocator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.DropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    satelliteSource: String,
    onSourceSelected: (String) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 28.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    DropdownPreference(
                        title = stringResource(R.string.satellite_source),
                        summary = "选择卫星 TLE 数据的来源",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null
                            )
                        },
                        value = satelliteSource,
                        items = listOf(
                            "ALL" to stringResource(R.string.source_all),
                            "CT" to stringResource(R.string.source_celestrak),
                            "SNOGS" to stringResource(R.string.source_satnogs)
                        ),
                        onValueChange = { onSourceSelected(it) }
                    )
                }
            }
        }
    }
}
