package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.ScanScope;

class OracleOwnerResolverTest {
    @Test
    void preservesQuotedExplicitOwner() {
        assertEquals("MixedOwner", OracleOwnerResolver.resolve(connection("IGNORED", "IGNORED"),
                new ScanScope(null, "\"MixedOwner\"", List.of(), List.of())));
    }

    @Test
    void foldsUnquotedExplicitOwner() {
        assertEquals("MIXEDOWNER", OracleOwnerResolver.resolve(connection("IGNORED", "IGNORED"),
                new ScanScope(null, "mixedowner", List.of(), List.of())));
    }

    @Test
    void preservesDatabaseReturnedCanonicalOwner() {
        assertEquals("MixedOwner", OracleOwnerResolver.resolve(connection("MixedOwner", "OtherOwner"),
                new ScanScope(null, null, List.of(), List.of())));
        assertEquals("MetadataOwner", OracleOwnerResolver.resolve(connection(null, "MetadataOwner"),
                new ScanScope(null, null, List.of(), List.of())));
    }

    private Connection connection(String schema, String user) {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUserName" -> user;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSchema" -> schema;
                    case "getMetaData" -> metadata;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
