package com.relationdetector.core.profile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.core.diagnostics.JdbcExceptionClassifier;
import com.relationdetector.core.diagnostics.JdbcFailureKind;

/**
 *
 * Shared exact JDBC profiler. Dialect classes only render SQL.
 */
public final class JdbcDataProfilerTemplate implements DataProfiler {
    private final DialectDataProfileQueryRenderer renderer;
    private final int[] permissionVendorCodes;
    private final DataProfileEvidenceBuilder evidenceBuilder = new DataProfileEvidenceBuilder();

    public JdbcDataProfilerTemplate(DialectDataProfileQueryRenderer renderer) {
        this(renderer, new int[0]);
    }

    public JdbcDataProfilerTemplate(
            DialectDataProfileQueryRenderer renderer,
            int... permissionVendorCodes
    ) {
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
        this.permissionVendorCodes = permissionVendorCodes == null ? new int[0] : permissionVendorCodes.clone();
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
        } catch (SQLException ex) {
            JdbcFailureKind kind = JdbcExceptionClassifier.classify(ex, permissionVendorCodes);
            return switch (kind) {
                case TIMEOUT -> failure(ProfileStatus.TIMEOUT, "PROFILE_QUERY_TIMEOUT", ex, request);
                case PERMISSION -> failure(ProfileStatus.PERMISSION_DENIED, "PROFILE_PERMISSION_DENIED", ex, request);
                case QUERY_FAILED -> failure(ProfileStatus.QUERY_FAILED, "PROFILE_QUERY_FAILED", ex, request);
            };
        }
        return new ProfileOutcome(ProfileStatus.NO_EVIDENCE, List.of(), List.of());
    }

    private ProfileOutcome failure(
            ProfileStatus status,
            String code,
            SQLException failure,
            ProfileRequest request
    ) {
        LiveDiagnosticSanitizer.Operation operation = switch (status) {
            case TIMEOUT -> LiveDiagnosticSanitizer.Operation.PROFILE_TIMEOUT;
            case PERMISSION_DENIED -> LiveDiagnosticSanitizer.Operation.PROFILE_PERMISSION;
            default -> LiveDiagnosticSanitizer.Operation.PROFILE_QUERY;
        };
        WarningMessage warning = LiveDiagnosticSanitizer.warning(
                WarningType.PROFILE_WARNING, code, operation, renderer.sourceName(), failure,
                java.util.Map.of(
                        "sourceEndpoint", request.candidate().source().normalizedKey(),
                        "targetEndpoint", request.candidate().target().normalizedKey(),
                        "profilerSource", renderer.sourceName()));
        return new ProfileOutcome(status, List.of(), List.of(warning));
    }

    private DataProfileMetrics metrics(ResultSet rs) throws java.sql.SQLException {
        long sourceRows = rs.getLong("source_non_null_rows");
        long sourceDistinct = rs.getLong("source_distinct");
        long matched = rs.getLong("matched_distinct");
        long targetDistinct = rs.getLong("target_distinct");
        long missing = Math.max(0, sourceDistinct - matched);
        return DataProfileMetrics.live(sourceRows, sourceDistinct, matched, missing, targetDistinct, false, false);
    }
}
