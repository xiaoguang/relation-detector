package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

final class ReleaseVerificationJson {
    static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ReleaseVerificationJson() {
    }

    static void write(Path output, Object value) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(output.toFile(), value);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to write verification JSON", error);
        }
    }
}
