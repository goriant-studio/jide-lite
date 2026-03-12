package com.goriant.jidelite.ui.main

import com.goriant.jidelite.model.RunResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunOutputFormatterTest {

    @Test
    fun formatDependencyResolutionUsesResolveLabelsOnSuccess() {
        val presentation = RunOutputFormatter.formatDependencyResolution(
            RunResult(
                true,
                "\$ mvn dependency:resolve\nResolve success.",
                "",
                0
            )
        )

        assertThat(presentation.statusText).isEqualTo("Dependencies resolved")
        assertThat(presentation.terminalText).contains("Dependencies resolved")
        assertThat(presentation.terminalText).contains("\$ mvn dependency:resolve")
        assertThat(presentation.terminalText).contains("Exit code: 0")
    }

    @Test
    fun formatDependencyResolutionUsesFailureLabelsOnError() {
        val presentation = RunOutputFormatter.formatDependencyResolution(
            RunResult(
                false,
                "\$ mvn dependency:resolve",
                "Dependency resolution failed.\n\nBoom",
                1
            )
        )

        assertThat(presentation.statusText).isEqualTo("Dependency resolution failed")
        assertThat(presentation.terminalText).contains("Dependency resolution failed")
        assertThat(presentation.terminalText).contains("Boom")
        assertThat(presentation.terminalText).contains("Exit code: 1")
    }

    @Test
    fun formatExtractsEditorDiagnosticFromCompilerOutput() {
        val presentation = RunOutputFormatter.format(
            RunResult(
                false,
                "\$ javac Main.java",
                "src/main/java/demo/Main.java:9: error: ';' expected",
                1
            )
        )

        assertThat(presentation.editorDiagnostic?.fileReference).isEqualTo("src/main/java/demo/Main.java")
        assertThat(presentation.editorDiagnostic?.lineNumber).isEqualTo(9)
    }

    @Test
    fun formatUsesTimeoutLabelsWhenRunExceedsLimit() {
        val presentation = RunOutputFormatter.format(
            RunResult(
                false,
                "\$ javac incremental cache hit\nCompile success.\n\$ java demo.Main",
                "Runtime timed out after 10s and was terminated.",
                124
            )
        )

        assertThat(presentation.statusText).isEqualTo("Run timed out")
        assertThat(presentation.terminalText).contains("Run timed out")
        assertThat(presentation.terminalText).contains("Exit code: 124")
    }
}
