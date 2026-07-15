package com.relationdetector.core.profile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;

/**
 * Shared bounded JDBC profiler. Dialect classes only render SQL.
 */
public final class JdbcDataProfilerTemplate implements DataProfiler {
    private final DialectDataProfileQueryRenderer renderer;
    private final SqlExceptionClassifier exceptionClassifier;
    private final DataProfileEvidenceBuilder evidenceBuilder = new DataProfileEvidenceBuilder();

    public JdbcDataProfilerTemplate(DialectDataProfileQueryRenderer renderer) {
        this(renderer, SqlExceptionClassifier.standard());
    }

    public JdbcDataProfilerTemplate(
            DialectDataProfileQueryRenderer renderer,
            SqlExceptionClassifier exceptionClassifier
    ) {
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
        this.exceptionClassifier = java.util.Objects.requireNonNull(exceptionClassifier, "exceptionClassifier");
    }

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
        if (connection == null || request == null || !request.candidate().source().isColumnLevel()
                || !request.candidate().target().isColumnLevel()) {
            return new ProfileOutcome(ProfileStatus.SKIPPED_INVALID_ENDPOINT, List.of(), List.of());
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(request.options().timeoutSeconds());
            try (ResultSet rs = statement.executeQuery(renderer.render(request))) {
                if (rs.next()) {
                    return ProfileOutcome.success(evidenceBuilder.build(request, metrics(rs), renderer.sourceName()));
                }
            }
        } catch (SQLTimeoutException ex) {
            return failure(ProfileStatus.TIMEOUT, "PROFILE_QUERY_TIMEOUT", ex, request);
        } catch (SQLException ex) {
            ProfileStatus status = exceptionClassifier.classify(ex);
            if (status == ProfileStatus.PERMISSION_DENIED) {
                return failure(ProfileStatus.PERMISSION_DENIED, "PROFILE_PERMISSION_DENIED", ex, request);
            }
            return failure(ProfileStatus.QUERY_FAILED, "PROFILE_QUERY_FAILED", ex, request);
        }
        return new ProfileOutcome(ProfileStatus.NO_EVIDENCE, List.of(), List.of());
    }

    private ProfileOutcome failure(
            ProfileStatus status,
            String code,
            SQLException failure,
            ProfileRequest request
    ) {
        WarningMessage warning = WarningMessage.warn(
                WarningType.PROFILE_WARNING,
                code,
                safeMessage(status),
                renderer.sourceName(),
                0,
                java.util.Map.of(
                        "sourceEndpoint", request.candidate().source().normalizedKey(),
                        "targetEndpoint", request.candidate().target().normalizedKey(),
                        "sqlState", failure.getSQLState() == null ? "" : failure.getSQLState(),
                        "vendorCode", failure.getErrorCode(),
                        "exceptionClass", failure.getClass().getName(),
                        "profilerSource", renderer.sourceName()));
        return new ProfileOutcome(status, List.of(), List.of(warning));
    }

    private String safeMessage(ProfileStatus status) {
        return switch (status) {
            case TIMEOUT -> "Data profiling query timed out";
            case PERMISSION_DENIED -> "Data profiling query permission denied";
            default -> "Data profiling query failed";
        };
    }

    private DataProfileMetrics metrics(ResultSet rs) throws java.sql.SQLException {
        long sourceDistinct = rs.getLong("source_distinct");
        long matched = rs.getLong("matched_distinct");
        long missing = Math.max(0, sourceDistinct - matched);
        return DataProfileMetrics.live(sourceDistinct, sourceDistinct, matched, missing, sourceDistinct, false, false);
    }
}
