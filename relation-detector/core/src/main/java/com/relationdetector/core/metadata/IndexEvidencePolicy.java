package com.relationdetector.core.metadata;

import com.relationdetector.contracts.metadata.MetadataIndexFact;

/**
 *
 * Defines when a multi-column index can support a single endpoint.
 */
public final class IndexEvidencePolicy {
    public boolean provesSingleColumnUnique(MetadataIndexFact index, String column) {
        return index != null
                && (index.unique() || index.primary())
                && index.columns().size() == 1
                && same(index.columns().get(0), column);
    }

    public boolean supportsLeadingColumnLookup(MetadataIndexFact index, String column) {
        if (index == null || !index.visible() || index.columns().isEmpty()) {
            return false;
        }
        String first = index.columns().get(0);
        int position = index.seqInIndex().isEmpty() ? 1 : index.seqInIndex().get(0);
        return first != null && !first.isBlank() && position == 1 && same(first, column);
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equals(right);
    }
}
