package com.example.radioarealocator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    satelliteSource: String,
    onSourceSelected: (String) -> Unit,
    onAboutClick: () -> Unit,
    contentPadding: PaddingValues
) {
    val options = listOf("ALL", "CT", "SNOGS")
    val labels = listOf(
        stringResource(R.string.source_all),
        stringResource(R.string.source_celestrak),
        stringResource(R.string.source_satnogs)
    )
    val selectedIndex = options.indexOf(satelliteSource).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                WindowDropdownPreference(
                    title = stringResource(R.string.satellite_source),
                    summary = "选择卫星 TLE 数据的来源",
                    items = labels,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { onSourceSelected(options[it]) }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.about_app),
                    summary = stringResource(R.string.about_description),
                    startAction = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                    },
                    onClick = onAboutClick
                )
            }
        }
    }
}
