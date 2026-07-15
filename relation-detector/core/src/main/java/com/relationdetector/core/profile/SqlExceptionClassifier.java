package com.relationdetector.core.profile;

import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.relationdetector.contracts.spi.ProfileStatus;

/** Classifies JDBC failures without exposing driver messages or rendered SQL. */
public final class SqlExceptionClassifier {
    private static final SqlExceptionClassifier STANDARD = new SqlExceptionClassifier(Set.of());

    private final Set<Integer> permissionVendorCodes;

    private SqlExceptionClassifier(Set<Integer> permissionVendorCodes) {
        this.permissionVendorCodes = Set.copyOf(permissionVendorCodes);
    }

    public static SqlExceptionClassifier standard() {
        return STANDARD;
    }

    public static SqlExceptionClassifier withPermissionVendorCodes(int... vendorCodes) {
        Set<Integer> codes = Arrays.stream(vendorCodes == null ? new int[0] : vendorCodes)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
        return new SqlExceptionClassifier(codes);
    }

    public ProfileStatus classify(SQLException failure) {
        if (failure instanceof SQLInvalidAuthorizationSpecException
                || permissionSqlState(failure.getSQLState())
                || permissionVendorCodes.contains(failure.getErrorCode())) {
            return ProfileStatus.PERMISSION_DENIED;
        }
        return ProfileStatus.QUERY_FAILED;
    }

    private boolean permissionSqlState(String sqlState) {
        return sqlState != null && (sqlState.startsWith("28") || sqlState.equals("42501"));
    }
}
