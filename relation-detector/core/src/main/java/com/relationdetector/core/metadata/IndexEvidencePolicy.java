package com.relationdetector.core.metadata;

import com.relationdetector.contracts.metadata.MetadataIndexFact;

/**
 * CN: 明确组合索引对单 endpoint 的证据边界：首列可支持 lookup，但只有单列 unique 才证明单列唯一。
 * EN: Defines single-endpoint index evidence: a leading column may support lookup, but only a single-column unique index proves uniqueness.
 */
public final class IndexEvidencePolicy {
    public boolean provesSingleColumnUnique(MetadataIndexFact index, String column) {
        return index != null
                && index.visible()
                && (index.unique() || index.primary())
                && index.columns().size() == 1
                && hasValidPhysicalShape(index)
                && index.seqInIndex().get(0) == 1
                && hasNoPrefix(index, 0)
                && index.expressions().stream().noneMatch(this::hasText)
                && same(index.columns().get(0), column);
    }

    public boolean supportsLeadingColumnLookup(MetadataIndexFact index, String column) {
        if (index == null || !index.visible() || index.columns().isEmpty()
                || !hasValidPhysicalShape(index)) {
            return false;
        }
        String first = index.columns().get(0);
        int position = index.seqInIndex().get(0);
        return first != null && !first.isBlank() && position == 1 && same(first, column);
    }

    private boolean hasValidPhysicalShape(MetadataIndexFact index) {
        return index.seqInIndex().size() == index.columns().size()
                && (index.subParts().isEmpty() || index.subParts().size() == index.columns().size())
                && strictlyIncreasingPositive(index.seqInIndex());
    }

    private boolean strictlyIncreasingPositive(java.util.List<Integer> positions) {
        int previous = 0;
        for (Integer position : positions) {
            if (position == null || position <= previous) {
                return false;
            }
            previous = position;
        }
        return true;
    }

    private boolean hasNoPrefix(MetadataIndexFact index, int member) {
        return index.subParts().isEmpty() || !hasText(index.subParts().get(member));
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
