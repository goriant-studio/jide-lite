package com.goriant.jidelite.ui.main

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
