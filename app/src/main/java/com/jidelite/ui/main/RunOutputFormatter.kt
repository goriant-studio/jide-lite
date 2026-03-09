package com.jidelite.ui.main

import com.jidelite.model.RunResult

data class RunPresentation(
    val statusText: String,
    val terminalText: String
)

object RunOutputFormatter {

    fun format(runResult: RunResult): RunPresentation {
        val isSimulated = runResult.exitCode < 0 &&
                (runResult.stdout.contains("Execution mode: simulated", ignoreCase = true) ||
                        runResult.stdout.contains("[placeholder runner]", ignoreCase = true) ||
                        runResult.stderr.contains("placeholder", ignoreCase = true))
        val terminalBuilder = StringBuilder()

        val statusText = when {
            isSimulated -> {
                terminalBuilder.append("Simulated run")
                "Placeholder runner"
            }

            runResult.isSuccess -> {
                terminalBuilder.append("Compile success")
                "Run finished"
            }

            else -> {
                terminalBuilder.append("Run failed")
                "Run failed"
            }
        }

        if (runResult.stdout.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stdout.trim())
        }

        if (runResult.stderr.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stderr.trim())
        }

        if (isSimulated) {
            terminalBuilder.append("\n\nExecution mode: simulated")
        } else if (runResult.exitCode >= 0) {
            terminalBuilder.append("\n\nExit code: ").append(runResult.exitCode)
        }

        return RunPresentation(
            statusText = statusText,
            terminalText = terminalBuilder.toString()
        )
    }

    fun formatDependencyResolution(runResult: RunResult): RunPresentation {
        val terminalBuilder = StringBuilder()

        val statusText = if (runResult.isSuccess) {
            terminalBuilder.append("Dependencies resolved")
            "Dependencies resolved"
        } else {
            terminalBuilder.append("Dependency resolution failed")
            "Dependency resolution failed"
        }

        if (runResult.stdout.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stdout.trim())
        }

        if (runResult.stderr.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stderr.trim())
        }

        if (runResult.exitCode >= 0) {
            terminalBuilder.append("\n\nExit code: ").append(runResult.exitCode)
        }

        return RunPresentation(
            statusText = statusText,
            terminalText = terminalBuilder.toString()
        )
    }
}
