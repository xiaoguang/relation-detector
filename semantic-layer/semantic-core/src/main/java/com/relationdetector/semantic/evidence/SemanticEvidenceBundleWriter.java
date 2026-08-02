package com.relationdetector.semantic.evidence;

import com.relationdetector.semantic.ingest.SemanticMetadataInventoryEnvelope;

import com.relationdetector.semantic.ingest.SemanticInputStore;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

/**
 * CN: 将已完成全局聚合的 SemanticEvidenceStore 按固定 wire section 顺序流式写为完整 evidence bundle，
 * 并在需要时对落盘字节计算 SHA-256。输入来自 store 的只读 descriptor 和 section 游标，输出是单个
 * bundle 文件及其摘要；本类不修改 store、不聚合 event，也不解释任何物理或语义事实。
 * EN: Streams a globally aggregated SemanticEvidenceStore into one complete evidence bundle in stable wire-section
 * order and optionally computes SHA-256 over the persisted bytes. It consumes only read-only descriptors and section
 * cursors and produces a bundle file plus its digest; it neither mutates the store, aggregates events, nor interprets
 * physical or semantic facts.
 */
final class SemanticEvidenceBundleWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    void write(SemanticEvidenceStore store, Path target) {
        if (store == null || target == null) {
            throw new IllegalArgumentException("semantic evidence store and bundle target are required");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
                generator.useDefaultPrettyPrinter();
                generator.writeStartObject();
                writeDatabase(generator, store.descriptor());
                generator.writeObjectField(
                        "metadataInventory",
                        SemanticMetadataInventoryEnvelope.from(store.descriptor().inventory()));
                writeStringArray(generator, "inputFiles", store.descriptor().inputFiles());
                writeStringArray(generator, "sources", store.descriptor().sources());
                for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
                    store.writeSectionArray(generator, section);
                }
                writeInstructions(generator);
                generator.writeEndObject();
                generator.writeRaw('\n');
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to stream semantic evidence bundle", failure);
        }
    }

    String writeAndHash(SemanticEvidenceStore store, Path target) {
        write(store, target);
        return sha256(target);
    }

    private void writeDatabase(
            JsonGenerator generator,
            SemanticInputStore.Descriptor descriptor
    ) throws IOException {
        generator.writeObjectFieldStart("database");
        generator.writeStringField("type", descriptor.databaseType());
        generator.writeStringField("catalog", descriptor.catalog());
        generator.writeStringField("schema", descriptor.schema());
        generator.writeEndObject();
    }

    private void writeStringArray(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (String value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }

    private void writeInstructions(JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("instructions");
        generator.writeBooleanField("allOutputsMustUseEvidenceRefs", true);
        generator.writeBooleanField("llmCannotCreateDatabaseFacts", true);
        generator.writeBooleanField("businessApprovedIsForbidden", true);
        generator.writeBooleanField("markUncertainItemsReviewNeeded", true);
        generator.writeEndObject();
    }

    private String sha256(Path path) {
        try {
            return SemanticFileDigest.compute(path).sha256();
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to hash semantic evidence bundle", failure);
        }
    }
}
