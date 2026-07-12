package com.relationdetector.core.fullgrammar;

import java.sql.Connection;

import com.relationdetector.contracts.Enums.DatabaseType;

/** Inputs used to select a versioned full-grammar profile. */
public record FullGrammarProfileRequest(
        DatabaseType databaseType,
        String configuredProfile,
        String configuredVersion,
        String configuredVersionSource,
        Connection jdbcConnection
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DatabaseType databaseType;
        private String configuredProfile = "";
        private String configuredVersion = "";
        private String configuredVersionSource = "CONFIG";
        private Connection jdbcConnection;

        private Builder() {
        }

        public Builder databaseType(DatabaseType value) {
            this.databaseType = value;
            return this;
        }

        public Builder configuredProfile(String value) {
            this.configuredProfile = value == null ? "" : value;
            return this;
        }

        public Builder configuredVersion(String value) {
            this.configuredVersion = value == null ? "" : value;
            return this;
        }

        public Builder configuredVersionSource(String value) {
            this.configuredVersionSource = value == null || value.isBlank() ? "CONFIG" : value;
            return this;
        }

        public Builder jdbcConnection(Connection value) {
            this.jdbcConnection = value;
            return this;
        }

        public FullGrammarProfileRequest build() {
            return new FullGrammarProfileRequest(
                    databaseType,
                    configuredProfile,
                    configuredVersion,
                    configuredVersionSource,
                    jdbcConnection);
        }
    }
}
