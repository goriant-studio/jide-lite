package com.jidelite.ui.main

import com.jidelite.model.RunResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunOutputFormatterTest {

    @Test
    fun formatMarksSuccessfulRuns() {
        val presentation = RunOutputFormatter.format(
            RunResult(
                true,
                "$ javac Main.java\nCompile success.\n$ java Main\n\nHello",
                "",
                0
            )
        )

        assertThat(presentation.statusText).isEqualTo("Run finished")
        assertThat(presentation.terminalText).contains("Compile success")
        assertThat(presentation.terminalText).contains("Exit code: 0")
    }

    @Test
    fun formatMarksFailedRuns() {
        val presentation = RunOutputFormatter.format(
            RunResult(
                false,
                "$ javac Main.java",
                "Compilation failed",
                1
            )
        )

        assertThat(presentation.statusText).isEqualTo("Run failed")
        assertThat(presentation.terminalText).contains("Run failed")
        assertThat(presentation.terminalText).contains("Compilation failed")
    }

    @Test
    fun formatMarksSimulatedRuns() {
        val presentation = RunOutputFormatter.format(
            RunResult(
                false,
                "[placeholder runner]",
                "",
                -1
            )
        )

        assertThat(presentation.statusText).isEqualTo("Placeholder runner")
        assertThat(presentation.terminalText).contains("Simulated run")
        assertThat(presentation.terminalText).contains("Execution mode: simulated")
    }
}
