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
}
