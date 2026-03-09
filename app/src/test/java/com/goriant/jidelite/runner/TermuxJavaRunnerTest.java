package com.goriant.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.goriant.jidelite.model.RunResult;

import org.junit.jupiter.api.Test;

public class TermuxJavaRunnerTest {

    @Test
    void runBuildsCommandTraceFromSelectedPath() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.run(" Demo.java ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(126);
        assertThat(result.getStdout()).isEqualTo("$ javac Demo.java\n$ java Demo");
        assertThat(result.getStderr())
                .isEqualTo("Termux runner is disabled. J-IDE Lite now uses the local embedded runner instead.");
    }

    @Test
    void runFallsBackToMainWhenExtensionIsMissing() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.run("Scratch");

        assertThat(result.getStdout()).isEqualTo("$ javac Scratch\n$ java Main");
        assertThat(result.getExitCode()).isEqualTo(126);
    }

    @Test
    void runUsesDefaultMainFileWhenSelectionIsNull() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.run(null);

        assertThat(result.getStdout()).isEqualTo("$ javac Main.java\n$ java Main");
        assertThat(result.getExitCode()).isEqualTo(126);
    }

    @Test
    void resolveDependenciesReportsTermuxRunnerDisabled() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.resolveDependencies();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStdout()).isEqualTo("$ mvn dependency:resolve");
        assertThat(result.getStderr())
                .isEqualTo("Termux runner is disabled. J-IDE Lite now uses the local embedded runner instead.");
        assertThat(result.getExitCode()).isEqualTo(126);
    }
}
