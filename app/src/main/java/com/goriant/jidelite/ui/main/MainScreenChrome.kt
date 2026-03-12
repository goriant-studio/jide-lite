package com.goriant.jidelite.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goriant.jidelite.R
import com.goriant.jidelite.ui.theme.JIdeLiteColors

internal val ChromeCompactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
internal val ChromeRegularButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
internal val ChromeCompactFieldHeight = 42.dp
internal val ChromeFindWidgetFieldHeight = 26.dp
internal const val ChromeFindWidgetWidthFraction = 0.26f
internal val ChromeFindWidgetMinWidth = 300.dp
internal val ChromeFindWidgetMaxWidth = 420.dp
internal val ChromeInlineButtonPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)

@Composable
internal fun TopBar(
    collapsed: Boolean,
    statusText: String,
    selectedFileName: String?,
    fileCount: Int,
    isBusy: Boolean,
    canResolveDependencies: Boolean,
    isDarkTheme: Boolean,
    isShortcutCheatsheetVisible: Boolean,
    canShareSelectedFile: Boolean,
    onResolveDependencies: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onToggleCollapse: () -> Unit,
    onToggleTheme: () -> Unit,
    onShowShortcuts: () -> Unit,
    onShareFile: () -> Unit,
    onImportProject: () -> Unit,
    onExportProject: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val selectedFileLabel = selectedFileName ?: stringResource(R.string.editor_empty_title)
    val fileCountLabel = rememberFileCountLabel(fileCount)
    val actionRowScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(themeColors.topBarSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (collapsed) 44.dp else 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (collapsed) 16.sp else 17.sp,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            ChromeActionButton(
                label = if (collapsed) "🔽" else "🔼",
                enabled = true,
                highlighted = false,
                compact = true,
                testTag = "topbar-toggle",
                onClick = onToggleCollapse
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(actionRowScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeActionButton(
                    label = stringResource(R.string.action_shortcuts).uppercase(),
                    enabled = true,
                    highlighted = isShortcutCheatsheetVisible,
                    compact = true,
                    testTag = "topbar-shortcuts",
                    onClick = onShowShortcuts
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_share).uppercase(),
                    enabled = canShareSelectedFile,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-share",
                    onClick = onShareFile
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_import).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-import",
                    onClick = onImportProject
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_export).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-export",
                    onClick = onExportProject
                )

                ChromeActionButton(
                    label = if (isDarkTheme) {
                        stringResource(R.string.action_theme_light).uppercase()
                    } else {
                        stringResource(R.string.action_theme_dark).uppercase()
                    },
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-theme",
                    onClick = onToggleTheme
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_resolve_short).uppercase(),
                    enabled = !isBusy && canResolveDependencies,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-resolve",
                    onClick = onResolveDependencies
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_save).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-save",
                    onClick = onSave
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_format_short).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-format",
                    onClick = onFormat
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_run).uppercase(),
                    enabled = !isBusy,
                    highlighted = true,
                    compact = true,
                    testTag = "topbar-run",
                    onClick = onRun
                )
            }
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "$selectedFileLabel  |  $fileCountLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ChromeActionButton(
    label: String,
    enabled: Boolean,
    highlighted: Boolean,
    compact: Boolean,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val shape = RoundedCornerShape(7.dp)
    val containerColor = if (highlighted) {
        themeColors.runButtonColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = if (highlighted) {
        themeColors.runButtonText
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(containerColor)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .then(
                if (highlighted) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (compact) ChromeCompactButtonPadding else ChromeRegularButtonPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (highlighted) "\u25B6 $label" else label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 10.sp else 13.sp,
            letterSpacing = if (compact) 0.2.sp else 0.45.sp
        )
    }
}

@Composable
internal fun ChromeInlineTextButton(
    label: String,
    enabled: Boolean = true,
    fontSize: TextUnit = 9.sp,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = ChromeInlineButtonPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun CompactOutlinedTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldHeight: Dp = ChromeCompactFieldHeight,
    textFontSize: TextUnit = 13.sp,
    placeholderFontSize: TextUnit = 12.sp,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    OutlinedTextField(
        modifier = modifier.requiredHeight(fieldHeight),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = textFontSize
        ),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor
        ),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = placeholderFontSize
            )
        }
    )
}

@Composable
internal fun FindWidgetIconButton(
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun FindWidgetTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val themeColors = JIdeLiteColors
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp
    )
    val shape = RoundedCornerShape(2.dp)
    val fieldModifier = modifier
        .height(24.dp)
        .clip(shape)
        .background(themeColors.editorSurface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .padding(horizontal = 6.dp)
        .then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )

    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        textStyle = textStyle,
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

internal data class ShortcutBinding(
    val keys: String,
    val action: String
)

internal val EditorShortcutBindings = listOf(
    ShortcutBinding("Tab", "Indent current line or selection"),
    ShortcutBinding("Enter", "Insert smart newline"),
    ShortcutBinding("Ctrl+Shift+?", "Reopen onboarding guide"),
    ShortcutBinding("Ctrl+/", "Open keyboard shortcuts"),
    ShortcutBinding("Ctrl+N", "Create a new Java file"),
    ShortcutBinding("Ctrl+Shift+N", "Create a new folder"),
    ShortcutBinding("Ctrl+S", "Save current file"),
    ShortcutBinding("Ctrl+R", "Run current file"),
    ShortcutBinding("Ctrl+F", "Find in editor"),
    ShortcutBinding("Ctrl+H", "Find & Replace"),
    ShortcutBinding("Ctrl+Shift+F", "Format current Java file"),
    ShortcutBinding("Ctrl+Shift+D", "Resolve Maven dependencies"),
    ShortcutBinding("Ctrl+A", "Select all"),
    ShortcutBinding("Ctrl+C", "Copy selection"),
    ShortcutBinding("Ctrl+X", "Cut selection"),
    ShortcutBinding("Ctrl+V", "Paste clipboard"),
    ShortcutBinding("Ctrl+Z", "Undo"),
    ShortcutBinding("Ctrl+Shift+Z / Ctrl+Y", "Redo")
)

@Composable
internal fun ShortcutCheatsheetDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("shortcuts-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.shortcuts_title),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                EditorShortcutBindings.forEach { binding ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = binding.keys,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.width(124.dp)
                        )

                        Text(
                            text = binding.action,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.shortcuts_close))
            }
        }
    )
}

@Composable
internal fun OnboardingDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("onboarding-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.onboarding_title),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_runner_title),
                    body = stringResource(R.string.onboarding_runner_body)
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_maven_title),
                    body = stringResource(R.string.onboarding_maven_body)
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_share_title),
                    body = stringResource(R.string.onboarding_share_body)
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("onboarding-dismiss"),
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.onboarding_cta))
            }
        }
    )
}

@Composable
internal fun OnboardingCard(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )

        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
internal fun rememberFileCountLabel(fileCount: Int): String {
    return pluralStringResource(R.plurals.file_count, fileCount, fileCount)
}
