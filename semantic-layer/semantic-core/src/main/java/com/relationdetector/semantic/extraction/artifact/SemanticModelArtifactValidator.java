package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallContext;
import com.relationdetector.semantic.extraction.runtime.SemanticModelCallResult;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 将不可信 model client 的声明复制到 writer 私有验证区，并独立复核路径、摘要、JSON 与 usage 边界。
 * EN: Copies an untrusted model client's declarations into a writer-private validation area and independently checks
 * path, digest, JSON, usage, and phase limits before any normalization or audit publication.
 */
public final class SemanticModelArtifactValidator {
    private final SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();

    /**
     * CN: 将一次不可信模型调用的三个声明产物复制到私有目录，再验证普通文件、摘要、JSON 上限、usage 与阶段 token 合同；任何检查失败均不返回可发布产物。
     * EN: Copies all three artifacts from an untrusted model call into private storage, then validates regular-file,
     * digest, JSON-bound, usage, and phase-token contracts; no publishable artifact is returned after any failure.
     */
    public ValidatedCall validate(
            SemanticModelCallResult result,
            SemanticModelCallContext context,
            int maxInputTokens,
            Path validationRoot
    ) {
        if (result == null || context == null || maxInputTokens <= 0 || validationRoot == null) {
            throw invalid();
        }
        if (result.inputTokens() < 0 || result.outputTokens() < 0
                || result.outputTokens() > context.maxOutputTokens()
                || result.transportAttempts() <= 0) {
            throw invalid();
        }
        try {
            requireScratchDirectory(context.scratchRoot());
            Path root = validationRoot.toAbsolutePath().normalize();
            Files.createDirectory(root);
            SemanticArtifactRef request = verifiedCopy(
                    result.request(), context.requestPath(), root.resolve("request.json"));
            SemanticArtifactRef response = verifiedCopy(
                    result.response(), context.responsePath(), root.resolve("response.json"));
            SemanticArtifactRef output = verifiedCopy(
                    result.output(), context.outputPath(), root.resolve("output.json"));

            ObjectNode requestJson = bounded.readObject(
                    request.path(),
                    new SemanticBoundedJsonReader.Limits(
                            SemanticBoundedJsonReader.responseEnvelopeByteLimit(maxInputTokens),
                            scaledTokenLimit(maxInputTokens, 4)),
                    "semantic model request");
            JsonNode declaredPhaseLimit = requestJson.path("max_output_tokens");
            if (!declaredPhaseLimit.isMissingNode()
                    && (!declaredPhaseLimit.isIntegralNumber()
                    || !declaredPhaseLimit.canConvertToInt()
                    || declaredPhaseLimit.intValue() != context.maxOutputTokens())) {
                throw invalid();
            }
            ObjectNode responseJson = bounded.readObject(
                    response.path(),
                    new SemanticBoundedJsonReader.Limits(
                            context.responseEnvelopeByteLimit(),
                            scaledTokenLimit(context.maxOutputTokens(), 33)),
                    "semantic model response");
            ObjectNode outputJson = bounded.readObject(
                    output.path(),
                    new SemanticBoundedJsonReader.Limits(
                            context.outputByteLimit(), context.maxOutputTokens()),
                    "semantic model output");
            if (usage(responseJson, "input_tokens") != result.inputTokens()
                    || usage(responseJson, "output_tokens") != result.outputTokens()) {
                throw invalid();
            }
            return new ValidatedCall(
                    new SemanticModelCallResult(
                            request, response, output,
                            result.inputTokens(), result.outputTokens(), result.transportAttempts()),
                    outputJson);
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | ArithmeticException failure) {
            throw invalid();
        }
    }

    private SemanticArtifactRef verifiedCopy(
            SemanticArtifactRef declared,
            Path expectedPath,
            Path target
    ) throws IOException {
        if (declared == null || !declared.path().equals(expectedPath.toAbsolutePath().normalize())) {
            throw invalid();
        }
        BasicFileAttributes attributes = Files.readAttributes(
                declared.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() != declared.bytes()) {
            throw invalid();
        }
        SemanticFileDigest.Digest digest = SemanticFileDigest.copyNoFollow(
                declared.path(), target, declared.bytes());
        if (digest.bytes() != declared.bytes() || !digest.sha256().equals(declared.sha256())) {
            throw invalid();
        }
        return new SemanticArtifactRef(target, digest.bytes(), digest.sha256());
    }

    private void requireScratchDirectory(Path scratch) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                scratch, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) {
            throw invalid();
        }
    }

    private int usage(ObjectNode response, String field) {
        JsonNode value = response.path("usage").path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw invalid();
        }
        return value.intValue();
    }

    private int scaledTokenLimit(int value, int multiplier) {
        long scaled = Math.multiplyExact((long) value, multiplier);
        return scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
    }

    private SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic model artifacts failed bounded validation");
    }

    public record ValidatedCall(SemanticModelCallResult artifacts, ObjectNode output) {
    }
}
