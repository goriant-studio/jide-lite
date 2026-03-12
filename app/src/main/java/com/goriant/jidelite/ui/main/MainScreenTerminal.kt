package com.goriant.jidelite.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goriant.jidelite.R
import com.goriant.jidelite.ui.theme.JIdeLiteColors

@Composable
internal fun TerminalPane(
    modifier: Modifier,
    collapsed: Boolean,
    terminalText: String,
    workspacePath: String,
    isRunning: Boolean,
    pendingImportSuggestion: ImportSuggestion?,
    onClearTerminal: () -> Unit,
    onToggleCollapse: () -> Unit,
    onSubmitStdin: (String) -> Unit,
    onAcceptImportSuggestion: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val scrollState = rememberScrollState()
    val readyText = stringResource(R.string.terminal_ready)

    LaunchedEffect(terminalText) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val terminalColor = when {
        terminalText.startsWith(readyText) -> themeColors.terminalReady
        terminalText.contains("failed", ignoreCase = true) -> themeColors.dotPink
        terminalText.contains("simulated", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier.background(themeColors.terminalSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(themeColors.dotPink))
                Box(modifier = Modifier.size(8.dp).background(themeColors.dotSand))
                Box(modifier = Modifier.size(8.dp).background(themeColors.dotMint))

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.terminal_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_clear).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable(onClick = onClearTerminal)
                )

                ChromeActionButton(
                    label = if (!collapsed) "\uD83D\uDD3D" else "\uD83D\uDD3C",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    testTag = "terminal-toggle",
                    onClick = onToggleCollapse
                )
            }
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Import suggestion banner
            if (pendingImportSuggestion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\uD83D\uDCA1 Add import: ${pendingImportSuggestion.qualifiedName}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    ChromeActionButton(
                        label = "ADD",
                        enabled = true,
                        highlighted = true,
                        compact = true,
                        testTag = "terminal-add-import",
                        onClick = onAcceptImportSuggestion
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.terminal_intro),
                            color = themeColors.terminalInfo,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${stringResource(R.string.terminal_workspace_prefix)} $workspacePath",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = terminalText,
                            color = terminalColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Stdin input field (visible when program is running)
            if (isRunning) {
                var stdinInput by remember { mutableStateOf("") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ">",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    FindWidgetTextField(
                        modifier = Modifier.weight(1f),
                        value = stdinInput,
                        onValueChange = { stdinInput = it },
                        placeholder = "Enter input..."
                    )

                    ChromeActionButton(
                        label = "SEND",
                        enabled = stdinInput.isNotEmpty(),
                        highlighted = true,
                        compact = true,
                        testTag = "terminal-send-stdin",
                        onClick = {
                            onSubmitStdin(stdinInput)
                            stdinInput = ""
                        }
                    )
                }
            }
        }
    }
}

