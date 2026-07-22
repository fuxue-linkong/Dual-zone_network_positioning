package com.example.radioarealocator.ui.cw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import kotlinx.coroutines.launch

private val morseMap = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..",
    '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
    '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
)

private val reverseMorseMap = morseMap.entries.associate { (k, v) -> v to k }

private fun encode(text: String): String {
    // 字符间单空格，词间 " / "，保证 encode→decode 可往返还原词间空格
    return text.uppercase()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" / ") { word ->
            word.filter { it in morseMap }
                .map { morseMap[it]!! }
                .joinToString(" ")
        }
}

private fun decode(morse: String): String {
    return morse.trim()
        .split("/")
        .joinToString(" ") { word ->
            word.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .mapNotNull { reverseMorseMap[it] }
                .joinToString("")
        }
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MorseCodeScreen(
    contentPadding: PaddingValues
) {
    var mode by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mode) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.morsecode_encode))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { mode = true; input = ""; output = "" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(stringResource(R.string.morsecode_encode))
                            }
                        }

                        if (!mode) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.morsecode_decode))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { mode = false; input = ""; output = "" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(stringResource(R.string.morsecode_decode))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (mode) stringResource(R.string.morsecode_input_hint_text)
                                else stringResource(R.string.morsecode_input_hint_morse)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surface,
                        unfocusedContainerColor = colorScheme.surface,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (mode) KeyboardType.Text else KeyboardType.Ascii
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    Text(
                        text = stringResource(R.string.morsecode_support_hint),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            if (input.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.morsecode_empty_input)
                                    )
                                }
                            } else {
                                output = if (mode) encode(input) else decode(input)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (mode) stringResource(R.string.morsecode_encode)
                                   else stringResource(R.string.morsecode_decode)
                        )
                    }

                    OutlinedTextField(
                        value = output,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                    val clip = ClipData.newPlainText("morse_code", output)
                                    clipboard.setPrimaryClip(clip)
                                    Toast
                                        .makeText(context, context.getString(R.string.morsecode_copied), Toast.LENGTH_SHORT)
                                        .show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.morsecode_copy),
                                    tint = colorScheme.primary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surface,
                        unfocusedContainerColor = colorScheme.surface,
                        focusedBorderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    ),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morsecode_reference_table),
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        morseMap.forEach { (char, code) ->
                            Text(
                                text = "$char $code",
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.widthIn(min = 72.dp),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(contentPadding)
    )
    }
}
