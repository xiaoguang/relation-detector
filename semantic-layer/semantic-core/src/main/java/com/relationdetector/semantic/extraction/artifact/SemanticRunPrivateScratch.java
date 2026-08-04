package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;
import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;
import com.relationdetector.semantic.extraction.shard.SemanticShardingException;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallContext;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallResult;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Owns one run's private plan snapshot and fixed per-call model scratch files. */
final class SemanticRunPrivateScratch implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;
    private final SemanticModelArtifactValidator validator = new SemanticModelArtifactValidator();
    private final SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();

    private SemanticRunPrivateScratch(Path root) {
        this.root = root;
    }

    static SemanticRunPrivateScratch open(RunArtifactPublisher.RunDirectory run) {
        Path root = run.stagingDirectory().resolveSibling(
                ".semantic-private-" + run.runId() + "-" + UUID.randomUUID());
        try {
            Files.createDirectory(root);
            restrict(root);
            return new SemanticRunPrivateScratch(root);
        } catch (IOException failure) {
            throw invalid("semantic private scratch cannot be created");
        }
    }

    SemanticRunPlan snapshot(SemanticRunPlan plan) {
        return SemanticRunPlanSnapshot.capture(plan, root.resolve("plan"));
    }

    SemanticModelCallContext call(String relative, int maxOutputTokens) {
        Path call = root.resolve("calls").resolve(relative).normalize();
        Path client = call.resolve("client");
        if (!call.startsWith(root)) {
            throw invalid("semantic model scratch path is invalid");
        }
        try {
            Files.createDirectories(call);
            Files.createDirectory(client);
            return new SemanticModelCallContext(
                    client,
                    client.resolve("request.json"),
                    client.resolve("response.json"),
                    client.resolve("output.json"),
                    maxOutputTokens);
        } catch (IOException failure) {
            throw invalid("semantic model scratch cannot be allocated");
        }
    }

    Path renderTarget(String relative) {
        Path target = root.resolve("rendered").resolve(relative).resolve("request.json").normalize();
        if (!target.startsWith(root)) {
            throw invalid("semantic request render path is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            return target;
        } catch (IOException failure) {
            throw invalid("semantic request render scratch cannot be allocated");
        }
    }

    SemanticArtifactRef render(
            SemanticRequestRenderer renderer,
            SemanticExtractionPrompt prompt,
            String relative
    ) {
        Path target = renderTarget(relative);
        SemanticArtifactRef result = renderer.render(prompt, target);
        if (result == null || !result.path().equals(target)) {
            throw invalid("semantic request renderer returned an invalid artifact path");
        }
        return result;
    }

    SemanticModelArtifactValidator.ValidatedCall validate(
            SemanticModelCallResult result,
            SemanticModelCallContext context,
            int maxInputTokens
    ) {
        return validator.validate(
                result,
                context,
                maxInputTokens,
                context.scratchRoot().getParent().resolve("validated"));
    }

    ObjectNode readObject(Path path, int maxTokens, String label) {
        return bounded.readObject(path, maxTokens, label);
    }

    void requireBudget(SemanticExtractionPrompt prompt, int maxInputTokens) {
        if (new SemanticPromptBudgetEstimator().estimate(prompt) > maxInputTokens) {
            throw new SemanticShardingException(
                    "semantic prompt exceeds the configured estimated input-token limit");
        }
    }

    static SemanticModelCallResult codexResult(ObjectNode raw, SemanticModelCallContext context) {
        try {
            ObjectNode request = JSON.createObjectNode().put("provider", "codex-session");
            ObjectNode response = raw.deepCopy();
            response.putObject("usage").put("input_tokens", 0).put("output_tokens", 0);
            JSON.writeValue(context.requestPath().toFile(), request);
            JSON.writeValue(context.responsePath().toFile(), response);
            JSON.writeValue(context.outputPath().toFile(), raw);
            return new SemanticModelCallResult(
                    referenceOf(context.requestPath()),
                    referenceOf(context.responsePath()),
                    referenceOf(context.outputPath()),
                    0, 0, 1);
        } catch (IOException failure) {
            throw invalid("semantic Codex result cannot be staged");
        }
    }

    @Override
    public void close() {
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
    }

    private static SemanticArtifactRef referenceOf(Path path) throws IOException {
        SemanticFileDigest.Digest digest = SemanticFileDigest.computeNoFollow(path);
        return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
    }

    private static SemanticExtractionValidationException invalid(String message) {
        return new SemanticExtractionValidationException(message);
    }

    private static void restrict(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX platforms still receive a fresh, run-unique scratch root.
        }
    }
}
