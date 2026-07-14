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

@Composable
fun PracticeScreen(
    currentText: String,
    morseCode: String,
    userInput: String,
    isPlaying: Boolean,
    isPaused: Boolean,
    accuracy: Float,
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
                text = stringResource(R.string.cw_practice),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                text = "原文: $currentText",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            Text(
                text = "摩尔斯电码: $morseCode",
                style = MaterialTheme.typography.bodyMedium
            )
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
                modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.weight(1f)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.check_results))
            }
        }

        item {
            if (accuracy > 0) {
                Text(
                    text = "${stringResource(R.string.accuracy)}: ${accuracy.toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
