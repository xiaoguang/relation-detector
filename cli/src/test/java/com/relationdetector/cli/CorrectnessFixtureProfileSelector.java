package com.relationdetector.cli;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class CorrectnessFixtureProfileSelector {
    private static final Set<String> SMOKE_FIXTURES = Set.of(
            "common/sql-basic-join",
            "mysql/basic-correctness-case-01-ddl",
            "postgres/postgres-basic-correctness-case-01-ddl",
            "oracle/oracle-sample-data-full-01-schema-01-tables-ddl");

    private CorrectnessFixtureProfileSelector() {
    }

    static boolean matches(Path correctnessRoot, Path manifest, String profile) {
        Set<String> profiles = Arrays.stream(profile.split("[,|]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (profiles.isEmpty()) {
            profiles = Set.of("smoke");
        }
        Path fixtureDir = manifest.getParent();
        Path relative = correctnessRoot.relativize(fixtureDir);
        String fixtureKey = relative.toString().replace('\\', '/');
        String dialect = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        String version = relative.getNameCount() > 1 ? relative.getName(1).toString().toLowerCase(Locale.ROOT) : "";
        for (String rawProfile : profiles) {
            String requested = rawProfile.toLowerCase(Locale.ROOT);
            if (requested.equals("full") || requested.equals("all")) {
                return true;
            }
            if (requested.equals("smoke")) {
                return SMOKE_FIXTURES.contains(fixtureKey);
            }
            if (requested.equals(dialect)) {
                return true;
            }
            if (requested.equals(dialect + "-root")) {
                return !version.startsWith("v");
            }
            if (requested.equals(dialect + "/" + version) || requested.equals(dialect + "-" + version)) {
                return true;
            }
            if (requested.equals("mysql80") || requested.equals("mysql8") || requested.equals("mysql-v8_0")) {
                return dialect.equals("mysql") && version.equals("v8_0");
            }
            if (requested.equals("mysql57") || requested.equals("mysql5.7") || requested.equals("mysql-v5_7")) {
                return dialect.equals("mysql") && version.equals("v5_7");
            }
        }
        return false;
    }
}
