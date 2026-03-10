package com.goriant.jidelite.ui.main

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EditorSearchEngineTest {

    @Test
    fun findMatchesIsCaseInsensitiveByDefault() {
        val matches = EditorSearchEngine.findMatches(
            text = "Main main MAIN",
            query = "main"
        )

        assertThat(matches).containsExactly(
            EditorSearchMatch(0, 4),
            EditorSearchMatch(5, 9),
            EditorSearchMatch(10, 14)
        )
    }

    @Test
    fun replaceAllRebuildsTextFromFoundMatches() {
        val matches = EditorSearchEngine.findMatches(
            text = "foo bar foo",
            query = "foo",
            ignoreCase = false
        )

        val replaced = EditorSearchEngine.replaceAll(
            text = "foo bar foo",
            matches = matches,
            replacement = "baz"
        )

        assertThat(replaced).isEqualTo("baz bar baz")
    }
}
