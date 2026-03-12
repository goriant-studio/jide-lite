// Standalone sample extracted from J-IDE Lite's editor find/replace flow.
// This file lives in docs/ so it does not affect the Android build.

data class EditorSearchMatch(
    val start: Int,
    val endExclusive: Int
)

object EditorSearchEngine {

    fun findMatches(text: String, query: String, ignoreCase: Boolean = true): List<EditorSearchMatch> {
        if (text.isEmpty() || query.isEmpty()) {
            return emptyList()
        }

        val matches = mutableListOf<EditorSearchMatch>()
        var searchIndex = 0
        while (searchIndex <= text.length - query.length) {
            val matchIndex = text.indexOf(query, searchIndex, ignoreCase = ignoreCase)
            if (matchIndex < 0) {
                break
            }
            matches += EditorSearchMatch(matchIndex, matchIndex + query.length)
            searchIndex = matchIndex + maxOf(1, query.length)
        }
        return matches
    }

    fun replaceAll(text: String, matches: List<EditorSearchMatch>, replacement: String): String {
        if (matches.isEmpty()) {
            return text
        }

        val builder = StringBuilder(text.length + replacement.length * matches.size)
        var cursor = 0
        for (match in matches.sortedBy { it.start }) {
            if (match.start < cursor || match.endExclusive > text.length) {
                continue
            }
            builder.append(text, cursor, match.start)
            builder.append(replacement)
            cursor = match.endExclusive
        }
        builder.append(text, cursor, text.length)
        return builder.toString()
    }
}

/*
State + handlers used from MainScreen.kt

var isFindReplaceVisible by rememberSaveable { mutableStateOf(false) }
var findQuery by rememberSaveable { mutableStateOf("") }
var replaceQuery by rememberSaveable { mutableStateOf("") }
var activeFindMatchIndex by rememberSaveable { mutableStateOf(0) }

val findMatches = remember(uiState.editorText, findQuery, isFindReplaceVisible) {
    if (!isFindReplaceVisible) {
        emptyList()
    } else {
        EditorSearchEngine.findMatches(uiState.editorText, findQuery)
    }
}

LaunchedEffect(findMatches.size, findQuery, isFindReplaceVisible, uiState.selectedFilePath) {
    activeFindMatchIndex = when {
        !isFindReplaceVisible || findQuery.isBlank() || findMatches.isEmpty() -> 0
        else -> activeFindMatchIndex.coerceIn(0, findMatches.lastIndex)
    }
}

LaunchedEffect(isFindReplaceVisible, findQuery, findMatches, activeFindMatchIndex, uiState.editorText) {
    editorBridge.input?.let { input ->
        if (isFindReplaceVisible && findQuery.isNotBlank() && findMatches.isNotEmpty()) {
            input.showSearchMatches(findMatches, activeFindMatchIndex.coerceIn(0, findMatches.lastIndex))
        } else {
            input.clearSearchHighlights()
        }
    }
}

val openFindReplace: () -> Unit = {
    val selectedText = EditorInteractionHelper.selectedText(
        editorBridge.input?.text?.toString(),
        editorBridge.input?.selectionStart ?: 0,
        editorBridge.input?.selectionEnd ?: 0
    )
    if (selectedText.isNotBlank()) {
        findQuery = selectedText
    }
    isFindReplaceVisible = true
    activeFindMatchIndex = 0
}

val jumpToFindMatch: (Int) -> Unit = { index ->
    val editText = editorBridge.input
    val match = findMatches.getOrNull(index)
    if (editText != null && match != null) {
        editText.revealRange(match.start, match.endExclusive)
    }
}

val goToPreviousMatch: () -> Unit = {
    if (findMatches.isEmpty()) {
        showToast("No matches")
    } else {
        activeFindMatchIndex = if (activeFindMatchIndex <= 0) {
            findMatches.lastIndex
        } else {
            activeFindMatchIndex - 1
        }
        jumpToFindMatch(activeFindMatchIndex)
    }
}

val goToNextMatch: () -> Unit = {
    if (findMatches.isEmpty()) {
        showToast("No matches")
    } else {
        activeFindMatchIndex = if (activeFindMatchIndex >= findMatches.lastIndex) {
            0
        } else {
            activeFindMatchIndex + 1
        }
        jumpToFindMatch(activeFindMatchIndex)
    }
}

val replaceCurrentMatch: () -> Unit = {
    val editText = activeEditor(editorBridge, currentEditorActions)
    val match = findMatches.getOrNull(activeFindMatchIndex)
    if (editText == null) {
        Unit
    } else if (match == null) {
        currentEditorActions.showToast("No matches")
    } else {
        val editable = editText.text
        if (editable != null) {
            editable.replace(match.start, match.endExclusive, replaceQuery)
            val cursor = (match.start + replaceQuery.length).coerceIn(0, editable.length)
            editText.revealRange(cursor, cursor)
            currentEditorActions.onStatusChanged("Replaced match", null)
        }
    }
}

val replaceAllMatches: () -> Unit = {
    val editText = activeEditor(editorBridge, currentEditorActions)
    if (editText == null) {
        Unit
    } else if (findMatches.isEmpty()) {
        currentEditorActions.showToast("No matches")
    } else {
        val editable = editText.text
        if (editable != null) {
            val replacedText = EditorSearchEngine.replaceAll(
                text = editable.toString(),
                matches = findMatches,
                replacement = replaceQuery
            )
            editable.replace(0, editable.length, replacedText)
            val cursor = (findMatches.first().start + replaceQuery.length)
                .coerceIn(0, replacedText.length)
            editText.revealRange(cursor, cursor)
            currentEditorActions.onStatusChanged("Replaced ${findMatches.size} matches", null)
        }
    }
}
*/

/*
Composable overlay from MainScreen.kt

@Composable
private fun FindReplaceBar(
    modifier: Modifier = Modifier,
    query: String,
    replacement: String,
    matchCount: Int,
    activeMatchIndex: Int,
    onQueryChanged: (String) -> Unit,
    onReplacementChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.topBarSurface.copy(alpha = 0.98f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactOutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.find_label),
                fieldHeight = ChromeFindWidgetFieldHeight,
                textFontSize = 12.sp,
                placeholderFontSize = 11.sp,
                containerColor = themeColors.editorSurface.copy(alpha = 0.96f)
            )

            MatchCountPill(matchCount = matchCount, activeMatchIndex = activeMatchIndex)

            ChromeInlineTextButton(label = stringResource(R.string.find_prev), onClick = onPrevious)
            ChromeInlineTextButton(label = stringResource(R.string.find_next), onClick = onNext)
            ChromeInlineTextButton(label = "X", onClick = onClose)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = replacement,
                onValueChange = onReplacementChanged,
                placeholder = stringResource(R.string.replace_label),
                fieldHeight = ChromeFindWidgetFieldHeight,
                textFontSize = 12.sp,
                placeholderFontSize = 11.sp,
                containerColor = themeColors.editorSurface.copy(alpha = 0.96f)
            )

            ChromeInlineTextButton(label = stringResource(R.string.replace_action), onClick = onReplace)
            ChromeInlineTextButton(label = stringResource(R.string.replace_all_action), onClick = onReplaceAll)
        }
    }
}
*/
