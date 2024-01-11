package com.exemple.cdc.agent;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.datastax.oss.driver.shaded.guava.common.base.Strings;

import lombok.Getter;

@Getter
public class AgentArgs {

    private static final Pattern ARGS_PATTERN = Pattern.compile("(?<key>[\\p{Alnum}]*)=(?<value>[\\p{Alnum}_\\-\\./]*)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private final String cdcConfiguration;

    public AgentArgs(String mainArgs) {

        var agentArgs = Arrays.stream(Objects.requireNonNullElse(mainArgs, "").split(","))
                .map(ARGS_PATTERN::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.toMap(arg -> arg.group("key"), arg -> arg.group("value")));

        if (Strings.isNullOrEmpty(agentArgs.get("conf"))) {
            throw new IllegalArgumentException("conf is required");
        }

        cdcConfiguration = agentArgs.get("conf");
    }

}
