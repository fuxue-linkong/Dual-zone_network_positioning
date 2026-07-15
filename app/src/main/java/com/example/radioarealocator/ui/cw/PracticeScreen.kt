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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.ui.theme.LocalCardAlpha

@Composable
fun PracticeScreen(
    currentText: String,
    morseCode: String,
    userInput: String,
    isPlaying: Boolean,
    isPaused: Boolean,
    accuracy: Float,
    courseTitle: String = "",
    lessonInfo: String = "",
    onUserInputChange: (String) -> Unit,
    onGenerateText: () -> Unit,
    onStartPractice: () -> Unit,
    onPausePractice: () -> Unit,
    onResumePractice: () -> Unit,
    onStopPractice: () -> Unit,
    onCheckResults: () -> Unit,
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
                text = if (courseTitle.isNotEmpty()) courseTitle else stringResource(R.string.cw_practice),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (lessonInfo.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalCardAlpha.current)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = lessonInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "原文:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentText.ifEmpty { "点击生成文本开始练习" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "摩尔斯电码:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = morseCode.ifEmpty { "生成文本后显示" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Button(
                onClick = onGenerateText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.generate_text))
            }
        }

        item {
            TextField(
                value = userInput,
                onValueChange = onUserInputChange,
                label = { Text("输入你听到的内容") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPlaying
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPlaying) {
                    Button(
                        onClick = onStartPractice,
                        modifier = Modifier.weight(1f),
                        enabled = currentText.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.start_practice))
                    }
                } else {
                    if (isPaused) {
                        Button(
                            onClick = onResumePractice,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.resume_practice))
                        }
                    } else {
                        Button(
                            onClick = onPausePractice,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.pause_practice))
                        }
                    }
                    Button(
                        onClick = onStopPractice,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.stop_practice))
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCheckResults,
                modifier = Modifier.fillMaxWidth(),
                enabled = userInput.isNotEmpty() && !isPlaying
            ) {
                Text(stringResource(R.string.check_results))
            }
        }

        item {
            if (accuracy > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.accuracy),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${accuracy.toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { accuracy / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
