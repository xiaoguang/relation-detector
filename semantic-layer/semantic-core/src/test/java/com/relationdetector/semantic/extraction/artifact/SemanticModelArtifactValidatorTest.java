package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallContext;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallResult;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticModelArtifactValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsOnlyExpectedScratchFilesWithMatchingDigestsAndUsage() throws Exception {
        Fixture fixture = fixture("valid", 1000, 10);

        SemanticModelArtifactValidator.ValidatedCall validated =
                new SemanticModelArtifactValidator().validate(
                        fixture.result(), fixture.context(), 1000,
                        fixture.root().resolve("validated"));

        assertEquals(0, validated.output().size());
        assertEquals(10, validated.artifacts().outputTokens());
    }

    @Test
    void rejectsOutsidePathBadHashAndOutputUsageAbovePhaseLimit() throws Exception {
        Fixture fixture = fixture("malicious", 100, 10);
        Path outside = tempDir.resolve("outside.json");
        Files.writeString(outside, "{}");
        SemanticModelCallResult outsideResult = new SemanticModelCallResult(
                fixture.result().request(), fixture.result().response(), artifact(outside), 5, 10, 1);
        SemanticModelCallResult badHash = new SemanticModelCallResult(
                fixture.result().request(), fixture.result().response(),
                new SemanticArtifactRef(
                        fixture.result().output().path(), fixture.result().output().bytes(), "f".repeat(64)),
                5, 10, 1);
        SemanticModelCallResult badUsage = new SemanticModelCallResult(
                fixture.result().request(), fixture.result().response(), fixture.result().output(),
                5, 101, 1);

        assertThrows(SemanticExtractionValidationException.class,
                () -> validate(outsideResult, fixture, "outside-validated"));
        assertThrows(SemanticExtractionValidationException.class,
                () -> validate(badHash, fixture, "hash-validated"));
        assertThrows(SemanticExtractionValidationException.class,
                () -> validate(badUsage, fixture, "usage-validated"));
    }

    @Test
    void rejectsSymlinkedClientOutput() throws Exception {
        Fixture fixture = fixture("symlink", 100, 10);
        Path target = fixture.root().resolve("target.json");
        Files.move(fixture.context().outputPath(), target);
        try {
            Files.createSymbolicLink(fixture.context().outputPath(), target);
        } catch (UnsupportedOperationException failure) {
            return;
        }
        SemanticFileDigest.Digest digest = SemanticFileDigest.compute(target);
        SemanticModelCallResult linked = new SemanticModelCallResult(
                fixture.result().request(), fixture.result().response(),
                new SemanticArtifactRef(
                        fixture.context().outputPath(), digest.bytes(), digest.sha256()),
                5, 10, 1);

        assertThrows(SemanticExtractionValidationException.class,
                () -> validate(linked, fixture, "link-validated"));
    }

    private void validate(SemanticModelCallResult result, Fixture fixture, String name) {
        new SemanticModelArtifactValidator().validate(
                result, fixture.context(), 1000, fixture.root().resolve(name));
    }

    private Fixture fixture(String name, int maxOutputTokens, int outputTokens) throws Exception {
        Path root = tempDir.resolve(name);
        Files.createDirectory(root);
        SemanticModelCallContext context = new SemanticModelCallContext(
                root,
                root.resolve("request.json"),
                root.resolve("response.json"),
                root.resolve("output.json"),
                maxOutputTokens);
        Files.writeString(context.requestPath(),
                "{\"max_output_tokens\":" + maxOutputTokens + "}");
        Files.writeString(context.responsePath(),
                "{\"usage\":{\"input_tokens\":5,\"output_tokens\":" + outputTokens + "}}");
        Files.writeString(context.outputPath(), "{}");
        SemanticModelCallResult result = new SemanticModelCallResult(
                artifact(context.requestPath()),
                artifact(context.responsePath()),
                artifact(context.outputPath()),
                5, outputTokens, 1);
        return new Fixture(root, context, result);
    }

    private SemanticArtifactRef artifact(Path path) throws Exception {
        SemanticFileDigest.Digest digest = SemanticFileDigest.compute(path);
        return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
    }

    private record Fixture(
            Path root,
            SemanticModelCallContext context,
            SemanticModelCallResult result
    ) {
    }
}
