package com.relationdetector.mysql.fullgrammar.common;

import java.util.Set;

/**
 *
 * MySQL full-grammar version capability descriptor.
 *
 * <p>Generated parser classes stay version-local. This policy is the small,
 * explicit place for semantic version labels and advertised capabilities.
 */
public record MySqlFullGrammarVersionPolicy(int major, int minor, Set<String> capabilities) {
    public MySqlFullGrammarVersionPolicy {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public String profileId() {
        return "mysql-" + major + "." + minor;
    }
}
