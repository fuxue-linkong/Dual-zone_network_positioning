package com.example.radioarealocator.ui.cw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.cw.CharacterSet
import com.example.radioarealocator.data.cw.CWSettings
import com.example.radioarealocator.data.cw.PlayMode

@Composable
fun FreePracticeSettingsScreen(
    settings: CWSettings,
    onSettingsChange: (CWSettings) -> Unit,
    onStartPractice: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.wpm),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.wpm.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(wpm = it.toInt())) },
                valueRange = 5f..50f,
                steps = 45
            )
            Text(text = "${settings.wpm} WPM")
        }

        item {
            Text(
                text = stringResource(R.string.frequency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.frequency.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(frequency = it.toInt())) },
                valueRange = 400f..800f,
                steps = 400
            )
            Text(text = "${settings.frequency} Hz")
        }

        item {
            Text(
                text = stringResource(R.string.character_set),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // 字符集选择器
            Text(text = settings.characterSet.name)
        }

        item {
            Text(
                text = stringResource(R.string.practice_length),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.practiceLength.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(practiceLength = it.toInt())) },
                valueRange = 10f..500f,
                steps = 49
            )
            Text(text = "${settings.practiceLength} 字符")
        }

        item {
            Text(
                text = stringResource(R.string.practice_duration),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.practiceDuration.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(practiceDuration = it.toInt())) },
                valueRange = 1f..30f,
                steps = 29
            )
            Text(text = "${settings.practiceDuration} 分钟")
        }

        item {
            Text(
                text = stringResource(R.string.play_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (settings.playMode == PlayMode.CONTINUOUS) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.continuous))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSettingsChange(settings.copy(playMode = PlayMode.CONTINUOUS)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.continuous))
                    }
                }
                if (settings.playMode == PlayMode.INTERVAL) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.interval))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSettingsChange(settings.copy(playMode = PlayMode.INTERVAL)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.interval))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartPractice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.start_practice))
            }
        }
    }
}
