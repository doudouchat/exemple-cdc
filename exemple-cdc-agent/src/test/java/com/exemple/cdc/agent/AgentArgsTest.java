package com.exemple.cdc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AgentArgsTest {

    @Test
    void success() {

        // When instance
        var agentArgs = new AgentArgs("conf=/tmp/conf/exemple.yml");

        // Then check args
        assertThat(agentArgs.getCdcConfiguration()).isEqualTo("/tmp/conf/exemple.yml");

    }

    @ParameterizedTest
    @NullAndEmptySource
    void failIfNullOrEmpty(String mainArgs) {

        // When instance
        var throwable = catchThrowable(() -> new AgentArgs(mainArgs));

        // Then check exception
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessage("conf is required");

    }

    @ParameterizedTest
    @ValueSource(strings = { " ", "conf" })
    void failIfNoRespectPattern(String mainArgs) {

        // When instance
        var throwable = catchThrowable(() -> new AgentArgs(mainArgs));

        // Then check exception
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessage("conf is required");

    }

    @ParameterizedTest
    @ValueSource(strings = { "key=value", "conf=" })
    void failIfConfIsMissing(String mainArgs) {

        // When instance
        var throwable = catchThrowable(() -> new AgentArgs(mainArgs));

        // Then check exception
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessage("conf is required");

    }
}
