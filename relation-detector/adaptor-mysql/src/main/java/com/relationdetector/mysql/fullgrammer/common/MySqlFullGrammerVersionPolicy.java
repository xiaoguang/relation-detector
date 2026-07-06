package com.relationdetector.mysql.fullgrammer.common;

import java.util.Set;

/**
 * MySQL full-grammer version capability descriptor.
 *
 * <p>Generated parser classes stay version-local. This policy is the small,
 * explicit place for semantic version labels and advertised capabilities.
 */
public record MySqlFullGrammerVersionPolicy(int major, int minor, Set<String> capabilities) {
    public MySqlFullGrammerVersionPolicy {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public String profileId() {
        return "mysql-" + major + "." + minor;
    }
}
