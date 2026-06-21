package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import java.util.Comparator;
import java.util.stream.Collectors;

import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.Endpoint;

public final class DataLineageFingerprint {
    private DataLineageFingerprint() {
    }

    public static String of(DataLineageCandidate candidate) {
        String sources = candidate.sources().stream()
                .sorted(Comparator.comparing(Endpoint::normalizedKey))
                .map(endpoint -> endpoint.table().tableName() + "." + endpoint.column().columnName())
                .collect(Collectors.joining(","));
        return candidate.flowKind().name() + ":"
                + candidate.transformType().name() + ":"
                + sources + "->"
                + candidate.target().table().tableName() + "." + candidate.target().column().columnName();
    }
}
