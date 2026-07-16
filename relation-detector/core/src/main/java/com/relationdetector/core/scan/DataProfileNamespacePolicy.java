package com.relationdetector.core.scan;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * Restricts profile candidates to namespaces executable by the active dialect.
 * It does not infer or rewrite endpoint identity; it only prevents an omitted
 * catalog component from redirecting a profile query to a same-named table.
 */
final class DataProfileNamespacePolicy {
    boolean supports(DatabaseType databaseType, ScanScope scope,
            IdentifierRules identifierRules, RelationshipCandidate candidate) {
        return supports(databaseType, scope, identifierRules, candidate.source())
                && supports(databaseType, scope, identifierRules, candidate.target());
    }

    private boolean supports(DatabaseType databaseType, ScanScope scope,
            IdentifierRules identifierRules, Endpoint endpoint) {
        String endpointCatalog = endpoint.table().catalog();
        return switch (databaseType) {
            case POSTGRESQL -> blank(endpointCatalog)
                    || same(identifierRules, endpointCatalog, scope.catalog());
            case ORACLE -> blank(endpointCatalog);
            default -> true;
        };
    }

    private boolean same(IdentifierRules identifierRules, String left, String right) {
        return !blank(right) && identifierRules.normalize(left).equals(identifierRules.normalize(right));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
