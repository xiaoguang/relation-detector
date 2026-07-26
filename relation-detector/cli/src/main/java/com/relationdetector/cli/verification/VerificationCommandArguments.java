package com.relationdetector.cli.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VerificationCommandArguments {
    private final Map<String, List<String>> options;
    private final List<String> positional;

    private VerificationCommandArguments(Map<String, List<String>> options, List<String> positional) {
        this.options = options;
        this.positional = positional;
    }

    static VerificationCommandArguments parse(String[] arguments, int offset, String... flags) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        List<String> flagNames = List.of(flags);
        for (int index = offset; index < arguments.length; index++) {
            String value = arguments[index];
            if (!value.startsWith("--")) {
                positional.add(value);
                continue;
            }
            if (flagNames.contains(value)) {
                options.computeIfAbsent(value, ignored -> new ArrayList<>()).add("true");
                continue;
            }
            if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")) {
                throw new ReleaseVerificationException("missing value for " + value);
            }
            options.computeIfAbsent(value, ignored -> new ArrayList<>()).add(arguments[++index]);
        }
        return new VerificationCommandArguments(options, positional);
    }

    String required(String name) {
        List<String> values = options.get(name);
        if (values == null || values.size() != 1 || values.get(0).isBlank()) {
            throw new ReleaseVerificationException("exactly one " + name + " is required");
        }
        return values.get(0);
    }

    String optional(String name, String fallback) {
        List<String> values = options.get(name);
        if (values == null) {
            return fallback;
        }
        if (values.size() != 1) {
            throw new ReleaseVerificationException("at most one " + name + " is allowed");
        }
        return values.get(0);
    }

    List<String> repeated(String name) {
        return List.copyOf(options.getOrDefault(name, List.of()));
    }

    boolean flag(String name) {
        return options.containsKey(name);
    }

    List<String> positional() {
        return List.copyOf(positional);
    }
}
