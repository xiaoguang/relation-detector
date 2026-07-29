package com.relationdetector.mysql.ddl;

import java.util.ArrayList;
import java.util.List;

/**
 * CN: 将各MySQL parser从typed index context提取的成员归类为物理整列、物理前缀列或表达式，
 * 并统一决定lookup与单列唯一证据；它不读取SQL文本，也不访问任何generated parser。
 * EN: Classifies typed MySQL index members as full columns, prefix columns, or expressions and
 * consistently derives lookup and single-column uniqueness evidence. It reads neither SQL text
 * nor generated parser contexts.
 */
public final class MySqlIndexSemantics {
    private MySqlIndexSemantics() {
    }

    public enum MemberKind {
        FULL_COLUMN,
        PREFIX_COLUMN,
        EXPRESSION
    }

    public enum IndexVisibility {
        VISIBLE,
        INVISIBLE
    }

    public record Member(String column, MemberKind kind) {
        public Member {
            column = column == null ? "" : column;
            if (kind == null) {
                throw new IllegalArgumentException("MySQL index member kind is required");
            }
        }

        public static Member fullColumn(String column) {
            return new Member(column, MemberKind.FULL_COLUMN);
        }

        public static Member prefixColumn(String column) {
            return new Member(column, MemberKind.PREFIX_COLUMN);
        }

        public static Member expression() {
            return new Member("", MemberKind.EXPRESSION);
        }

        public boolean physical() {
            return kind != MemberKind.EXPRESSION && !column.isBlank();
        }
    }

    public record IndexEvidence(String column, String role, String kind) {
    }

    public static List<IndexEvidence> evidence(
            List<Member> members,
            boolean unique,
            IndexVisibility visibility,
            String kind
    ) {
        if (visibility == null) {
            throw new IllegalArgumentException("MySQL index visibility is required");
        }
        List<Member> safeMembers = members == null ? List.of() : List.copyOf(members);
        if (safeMembers.isEmpty() || visibility == IndexVisibility.INVISIBLE) {
            return List.of();
        }
        List<IndexEvidence> evidence = new ArrayList<>();
        Member first = safeMembers.get(0);
        if (first.physical()) {
            evidence.add(new IndexEvidence(first.column(), "SOURCE_INDEX", kind));
        }
        if (unique && safeMembers.size() == 1 && first.kind() == MemberKind.FULL_COLUMN
                && first.physical()) {
            evidence.add(new IndexEvidence(first.column(), "TARGET_UNIQUE", kind));
        }
        return List.copyOf(evidence);
    }
}
