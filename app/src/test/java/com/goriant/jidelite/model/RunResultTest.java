package com.goriant.jidelite.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class RunResultTest {

    @Test
    void constructorNormalizesNullStreamsToEmptyStrings() {
        RunResult runResult = new RunResult(false, null, null, 9);

        assertThat(runResult.isSuccess()).isFalse();
        assertThat(runResult.getStdout()).isEmpty();
        assertThat(runResult.getStderr()).isEmpty();
        assertThat(runResult.getExitCode()).isEqualTo(9);
    }
}
