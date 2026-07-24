package com.relationdetector.core.scan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * CN: 深复制 adaptor 结果 attributes 中允许的不可变标量、列表、集合与字符串键 map，防止插件在 core
 * 校验后继续修改嵌套容器；输入来自 SPI 边界，输出供契约 validator 装配副本。本类不解释属性语义，
 * 也不接受未知的可变对象类型。
 *
 * <p>EN: Deeply detaches supported immutable scalars, lists, sets, and string-keyed maps from adaptor result
 * attributes so plugins cannot mutate nested containers after validation. It does not interpret attribute semantics
 * or accept unknown mutable value types.
 */
public final class AdaptorResultDetachmentSupport {
    public RelationshipCandidate relationshipCandidate(
            RelationshipCandidate candidate,
            String boundary
    ) {
        require(candidate != null, boundary + " is null");
        RelationshipCandidate copy = new RelationshipCandidate(
                candidate.source(), candidate.target(), candidate.relationType(), candidate.relationSubType());
        copy.confidence(candidate.confidence());
        candidate.evidence().forEach(item -> copy.evidence().add(
                evidence(item, boundary + " evidence")));
        candidate.rawEvidence().forEach(item -> copy.rawEvidence().add(
                evidence(item, boundary + " raw evidence")));
        candidate.warnings().forEach(item -> copy.warnings().add(
                warning(item, boundary + " warning")));
        copy.attributes().putAll(attributes(candidate.attributes(), boundary + " attributes"));
        return copy;
    }

    public Evidence evidence(Evidence evidence, String boundary) {
        require(evidence != null, boundary + " is null");
        return new Evidence(
                evidence.type(),
                evidence.score(),
                evidence.sourceType(),
                evidence.source(),
                evidence.detail(),
                attributes(evidence.attributes(), boundary + " attributes"));
    }

    public WarningMessage warning(WarningMessage warning, String boundary) {
        require(warning != null, boundary + " is null");
        return new WarningMessage(
                warning.type(),
                warning.severity(),
                warning.code(),
                warning.message(),
                warning.source(),
                warning.line(),
                attributes(warning.attributes(), boundary + " attributes"));
    }

    public Map<String, Object> attributes(Map<String, Object> attributes, String boundary) {
        require(attributes != null, boundary + " are null");
        Map<String, Object> result = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            require(key != null && !key.isBlank(), boundary + " contain an invalid key");
            result.put(key, value(value, boundary));
        });
        return Collections.unmodifiableMap(result);
    }

    private Object value(Object value, String boundary) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || immutableNumber(value)) {
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = list.stream().map(item -> value(item, boundary)).toList();
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(item -> copy.add(value(item, boundary)));
            return Collections.unmodifiableSet(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                require(key instanceof String && !((String) key).isBlank(),
                        boundary + " contain an invalid key");
                copy.put((String) key, value(item, boundary));
            });
            return Collections.unmodifiableMap(copy);
        }
        throw new AdaptorContractException(
                "adaptor result contract violation: " + boundary + " contain an unsupported mutable value");
    }

    private boolean immutableNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new AdaptorContractException("adaptor result contract violation: " + message);
        }
    }
}
