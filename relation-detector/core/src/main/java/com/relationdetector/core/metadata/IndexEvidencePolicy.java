package com.relationdetector.core.metadata;

import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberKind;

/**
 * CN: 明确组合索引对单 endpoint 的证据边界：首列可支持 lookup，但只有单列 unique 才证明单列唯一。
 * EN: Defines single-endpoint index evidence: a leading column may support lookup, but only a single-column unique index proves uniqueness.
 */
public final class IndexEvidencePolicy {
    public boolean provesSingleColumnUnique(MetadataIndexFact index, String column) {
        return index != null
                && index.visible()
                && (index.unique() || index.primary())
                && hasValidMemberShape(index)
                && index.members().size() == 1
                && index.members().get(0).kind() == MetadataIndexMemberKind.FULL_COLUMN
                && same(index.members().get(0).columnName(), column);
    }

    public boolean supportsLeadingColumnLookup(MetadataIndexFact index, String column) {
        if (index == null || !index.visible() || !hasValidMemberShape(index)) {
            return false;
        }
        MetadataIndexMemberFact first = index.members().get(0);
        return first.kind() != MetadataIndexMemberKind.EXPRESSION
                && same(first.columnName(), column);
    }

    private boolean hasValidMemberShape(MetadataIndexFact index) {
        if (index.members().isEmpty()) {
            return false;
        }
        int expectedOrdinal = 1;
        for (MetadataIndexMemberFact member : index.members()) {
            if (member == null || member.kind() == null || member.ordinal() != expectedOrdinal++) {
                return false;
            }
            boolean column = hasText(member.columnName());
            boolean expression = hasText(member.expression());
            if (member.kind() == MetadataIndexMemberKind.FULL_COLUMN
                    && (!column || expression || member.prefixLength() != null)) {
                return false;
            }
            if (member.kind() == MetadataIndexMemberKind.PREFIX_COLUMN
                    && (!column || expression || member.prefixLength() == null || member.prefixLength() <= 0)) {
                return false;
            }
            if (member.kind() == MetadataIndexMemberKind.EXPRESSION
                    && (column || !expression || member.prefixLength() != null)) {
                return false;
            }
        }
        return true;
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
