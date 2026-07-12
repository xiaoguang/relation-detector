package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.Locale;

record SqlServerProjectionKey(String owner, String column) {
    SqlServerProjectionKey {
        owner = normalize(owner);
        column = normalize(column);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
