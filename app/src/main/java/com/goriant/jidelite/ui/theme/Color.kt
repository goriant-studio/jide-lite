package com.goriant.jidelite.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

internal val DarkBackground = Color(0xFF0E1020)
internal val DarkTopBarSurface = Color(0xFF14172A)
internal val DarkExplorerSurface = Color(0xFF111424)
internal val DarkEditorSurface = Color(0xFF1C1F35)
internal val DarkTerminalSurface = Color(0xFF090B18)
internal val DarkSelectedSurface = Color(0xFF2B2A4C)
internal val DarkRunButtonColor = Color(0xFFA8E58E)
internal val DarkRunButtonText = Color(0xFF0B1208)
internal val DarkPrimaryAccent = Color(0xFFD0A6FF)
internal val DarkSecondaryAccent = Color(0xFFF2DE9F)
internal val DarkOutlineSubtle = Color(0xFF303458)
internal val DarkSurfaceContainer = Color(0xFF2B2E46)
internal val DarkTerminalInfo = Color(0xFF8DB0FF)
internal val DarkTerminalReady = Color(0xFF9CE285)
internal val DarkDotPink = Color(0xFFF188B1)
internal val DarkDotSand = Color(0xFFF1D39C)
internal val DarkDotMint = Color(0xFF94E39A)
internal val DarkEditorText = Color(0xFFF4F6F8)
internal val DarkEditorHint = Color(0xFF6C7486)
internal val DarkEditorGutterBackground = Color(0xFF11161D)
internal val DarkEditorGutterDivider = Color(0xFF2A3140)
internal val DarkEditorLineNumber = Color(0xFF5B6578)
internal val DarkEditorLineNumberActive = Color(0xFFA7B6CE)
internal val DarkEditorLineNumberError = Color(0xFFF2838F)
internal val DarkEditorErrorLineBackground = Color(0xFF2C1C24)
internal val DarkEditorSearchMatch = Color(0xFF524620)
internal val DarkEditorSearchMatchActive = Color(0xFF8D7530)
internal val DarkSyntaxKeyword = Color(0xFFFF9E64)
internal val DarkSyntaxString = Color(0xFF98C379)
internal val DarkSyntaxComment = Color(0xFF6B7285)
internal val DarkSyntaxAnnotation = Color(0xFF7DCFFF)
internal val DarkSyntaxNumber = Color(0xFFD19A66)
internal val DarkFindWidgetBackground = Color(0xFF252526)
internal val DarkFindWidgetBorder = Color(0xFF454545)

internal val LightBackground = Color(0xFFF4F1EA)
internal val LightTopBarSurface = Color(0xFFFBF8F2)
internal val LightExplorerSurface = Color(0xFFF1ECE2)
internal val LightEditorSurface = Color(0xFFFFFCF7)
internal val LightTerminalSurface = Color(0xFFEDE7DC)
internal val LightSelectedSurface = Color(0xFFE3DDD1)
internal val LightRunButtonColor = Color(0xFF29463A)
internal val LightRunButtonText = Color(0xFFF8F7F2)
internal val LightPrimaryAccent = Color(0xFF446557)
internal val LightSecondaryAccent = Color(0xFFAC7D41)
internal val LightOutlineSubtle = Color(0xFFD5CEC1)
internal val LightSurfaceContainer = Color(0xFFEBE5D9)
internal val LightTerminalInfo = Color(0xFF496777)
internal val LightTerminalReady = Color(0xFF4C7059)
internal val LightDotPink = Color(0xFFB96675)
internal val LightDotSand = Color(0xFFB88B4C)
internal val LightDotMint = Color(0xFF5D8C70)
internal val LightEditorText = Color(0xFF1B221E)
internal val LightEditorHint = Color(0xFF7E837B)
internal val LightEditorGutterBackground = Color(0xFFF1EBE0)
internal val LightEditorGutterDivider = Color(0xFFD8D0C2)
internal val LightEditorLineNumber = Color(0xFF7D7A73)
internal val LightEditorLineNumberActive = Color(0xFF425E53)
internal val LightEditorLineNumberError = Color(0xFFB35C68)
internal val LightEditorErrorLineBackground = Color(0xFFF1D9DE)
internal val LightEditorSearchMatch = Color(0xFFE9DBB7)
internal val LightEditorSearchMatchActive = Color(0xFFD1B26A)
internal val LightSyntaxKeyword = Color(0xFF46695A)
internal val LightSyntaxString = Color(0xFFA1703C)
internal val LightSyntaxComment = Color(0xFF8A918A)
internal val LightSyntaxAnnotation = Color(0xFF4E7F91)
internal val LightSyntaxNumber = Color(0xFFB26F49)
internal val LightFindWidgetBackground = Color(0xFFF3F3F3)
internal val LightFindWidgetBorder = Color(0xFFCBCBCB)

@Immutable
data class JIdeLiteExtraColors(
    val topBarSurface: Color,
    val explorerSurface: Color,
    val editorSurface: Color,
    val terminalSurface: Color,
    val selectedSurface: Color,
    val runButtonColor: Color,
    val runButtonText: Color,
    val terminalInfo: Color,
    val terminalReady: Color,
    val dotPink: Color,
    val dotSand: Color,
    val dotMint: Color,
    val editorText: Color,
    val editorHint: Color,
    val editorGutterBackground: Color,
    val editorGutterDivider: Color,
    val editorLineNumber: Color,
    val editorLineNumberActive: Color,
    val editorLineNumberError: Color,
    val editorErrorLineBackground: Color,
    val editorSearchMatch: Color,
    val editorSearchMatchActive: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxAnnotation: Color,
    val syntaxNumber: Color,
    val findWidgetBackground: Color,
    val findWidgetBorder: Color
)

internal val DarkExtraColors = JIdeLiteExtraColors(
    topBarSurface = DarkTopBarSurface,
    explorerSurface = DarkExplorerSurface,
    editorSurface = DarkEditorSurface,
    terminalSurface = DarkTerminalSurface,
    selectedSurface = DarkSelectedSurface,
    runButtonColor = DarkRunButtonColor,
    runButtonText = DarkRunButtonText,
    terminalInfo = DarkTerminalInfo,
    terminalReady = DarkTerminalReady,
    dotPink = DarkDotPink,
    dotSand = DarkDotSand,
    dotMint = DarkDotMint,
    editorText = DarkEditorText,
    editorHint = DarkEditorHint,
    editorGutterBackground = DarkEditorGutterBackground,
    editorGutterDivider = DarkEditorGutterDivider,
    editorLineNumber = DarkEditorLineNumber,
    editorLineNumberActive = DarkEditorLineNumberActive,
    editorLineNumberError = DarkEditorLineNumberError,
    editorErrorLineBackground = DarkEditorErrorLineBackground,
    editorSearchMatch = DarkEditorSearchMatch,
    editorSearchMatchActive = DarkEditorSearchMatchActive,
    syntaxKeyword = DarkSyntaxKeyword,
    syntaxString = DarkSyntaxString,
    syntaxComment = DarkSyntaxComment,
    syntaxAnnotation = DarkSyntaxAnnotation,
    syntaxNumber = DarkSyntaxNumber,
    findWidgetBackground = DarkFindWidgetBackground,
    findWidgetBorder = DarkFindWidgetBorder
)

internal val LightExtraColors = JIdeLiteExtraColors(
    topBarSurface = LightTopBarSurface,
    explorerSurface = LightExplorerSurface,
    editorSurface = LightEditorSurface,
    terminalSurface = LightTerminalSurface,
    selectedSurface = LightSelectedSurface,
    runButtonColor = LightRunButtonColor,
    runButtonText = LightRunButtonText,
    terminalInfo = LightTerminalInfo,
    terminalReady = LightTerminalReady,
    dotPink = LightDotPink,
    dotSand = LightDotSand,
    dotMint = LightDotMint,
    editorText = LightEditorText,
    editorHint = LightEditorHint,
    editorGutterBackground = LightEditorGutterBackground,
    editorGutterDivider = LightEditorGutterDivider,
    editorLineNumber = LightEditorLineNumber,
    editorLineNumberActive = LightEditorLineNumberActive,
    editorLineNumberError = LightEditorLineNumberError,
    editorErrorLineBackground = LightEditorErrorLineBackground,
    editorSearchMatch = LightEditorSearchMatch,
    editorSearchMatchActive = LightEditorSearchMatchActive,
    syntaxKeyword = LightSyntaxKeyword,
    syntaxString = LightSyntaxString,
    syntaxComment = LightSyntaxComment,
    syntaxAnnotation = LightSyntaxAnnotation,
    syntaxNumber = LightSyntaxNumber,
    findWidgetBackground = LightFindWidgetBackground,
    findWidgetBorder = LightFindWidgetBorder
)
