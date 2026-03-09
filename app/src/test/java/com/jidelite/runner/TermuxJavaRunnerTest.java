package com.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.jidelite.model.RunResult;

import org.junit.jupiter.api.Test;

public class TermuxJavaRunnerTest {

    @Test
    void runJavaTrimsFileNameAndBuildsCommandTrace() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.runJava(" Demo.java ", "class Demo {}");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(126);
        assertThat(result.getStdout()).isEqualTo("$ javac Demo.java\n$ java Demo");
        assertThat(result.getStderr())
                .isEqualTo("Termux runner is disabled. J-IDE Lite now uses the local embedded runner instead.");
    }

    @Test
    void runJavaFallsBackToMainWhenExtensionIsMissing() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.runJava("Scratch", "class Scratch {}");

        assertThat(result.getStdout()).isEqualTo("$ javac Scratch\n$ java Main");
        assertThat(result.getExitCode()).isEqualTo(126);
    }

    @Test
    void runJavaUsesDefaultMainFileWhenNameIsNull() {
        TermuxJavaRunner runner = new TermuxJavaRunner();

        RunResult result = runner.runJava(null, "class Main {}");

        assertThat(result.getStdout()).isEqualTo("$ javac Main.java\n$ java Main");
        assertThat(result.getExitCode()).isEqualTo(126);
    }
}
