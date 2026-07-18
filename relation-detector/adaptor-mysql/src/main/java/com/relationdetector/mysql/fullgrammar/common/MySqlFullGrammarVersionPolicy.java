package com.relationdetector.mysql.fullgrammar.common;

import java.util.Set;

/**
 * CN: 保存 full-grammar profile 明确声明的 MySQL major/minor 和 capability labels；它只描述版本能力，不加载 generated parser 或决定 SQL 语义。
 * EN: Carries the MySQL major/minor version and capability labels advertised by a full-grammar profile. It describes version boundaries without loading generated parsers or deciding SQL semantics.
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
