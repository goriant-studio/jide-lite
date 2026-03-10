package com.goriant.jidelite.ui.main

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EditorDiagnosticParserTest {

    @Test
    fun parsePrefersJavacStyleDiagnostics() {
        val diagnostic = EditorDiagnosticParser.parse(
            stderr = "src/main/java/demo/Main.java:7: error: ';' expected",
            stdout = ""
        )

        assertThat(diagnostic?.fileReference).isEqualTo("src/main/java/demo/Main.java")
        assertThat(diagnostic?.lineNumber).isEqualTo(7)
    }

    @Test
    fun parseSupportsJaninoStyleDiagnostics() {
        val diagnostic = EditorDiagnosticParser.parse("File /tmp/Main.java, Line 12, Column 8: Unexpected token")

        assertThat(diagnostic?.fileReference).isEqualTo("/tmp/Main.java")
        assertThat(diagnostic?.lineNumber).isEqualTo(12)
    }

    @Test
    fun parseFallsBackToRuntimeStackTraceLocation() {
        val diagnostic = EditorDiagnosticParser.parse(
            stderr = "Exception in thread \"main\"\n\tat demo.Main.main(Main.java:19)",
            stdout = ""
        )

        assertThat(diagnostic?.fileReference).isEqualTo("Main.java")
        assertThat(diagnostic?.lineNumber).isEqualTo(19)
    }

    @Test
    fun parseSupportsLineOnlyMessages() {
        val diagnostic = EditorDiagnosticParser.parse("Line 4, Column 13: Identifier expected")

        assertThat(diagnostic?.fileReference).isNull()
        assertThat(diagnostic?.lineNumber).isEqualTo(4)
    }
}
