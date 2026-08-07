package com.relationdetector.core.adaptor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.IdentityHashMap;

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
    private static final int MAX_DEPTH = 64;
    private static final int MAX_CONTAINER_ELEMENTS = 10_000;
    private static final String STRUCTURAL_LIMIT_FAILURE =
            "adaptor result contract violation: adaptor attributes exceed structural limits";

    public RelationshipCandidate relationshipCandidate(
            RelationshipCandidate candidate,
            String boundary
    ) {
        Traversal traversal = new Traversal();
        require(candidate != null, boundary + " is null");
        RelationshipCandidate copy = new RelationshipCandidate(
                candidate.source(), candidate.target(), candidate.relationType(), candidate.relationSubType());
        copy.confidence(candidate.confidence());
        candidate.evidence().forEach(item -> copy.evidence().add(
                evidence(item, boundary + " evidence", traversal)));
        candidate.rawEvidence().forEach(item -> copy.rawEvidence().add(
                evidence(item, boundary + " raw evidence", traversal)));
        candidate.warnings().forEach(item -> copy.warnings().add(
                warning(item, boundary + " warning", traversal)));
        copy.attributes().putAll(attributes(candidate.attributes(), boundary + " attributes", traversal));
        return copy;
    }

    public Evidence evidence(Evidence evidence, String boundary) {
        return evidence(evidence, boundary, new Traversal());
    }

    private Evidence evidence(Evidence evidence, String boundary, Traversal traversal) {
        require(evidence != null, boundary + " is null");
        return new Evidence(
                evidence.type(),
                evidence.score(),
                evidence.sourceType(),
                evidence.source(),
                evidence.detail(),
                attributes(evidence.attributes(), boundary + " attributes", traversal));
    }

    public WarningMessage warning(WarningMessage warning, String boundary) {
        return warning(warning, boundary, new Traversal());
    }

    private WarningMessage warning(WarningMessage warning, String boundary, Traversal traversal) {
        require(warning != null, boundary + " is null");
        return new WarningMessage(
                warning.type(),
                warning.severity(),
                warning.code(),
                warning.message(),
                warning.source(),
                warning.line(),
                attributes(warning.attributes(), boundary + " attributes", traversal));
    }

    public Map<String, Object> attributes(Map<String, Object> attributes, String boundary) {
        return attributes(attributes, boundary, new Traversal());
    }

    private Map<String, Object> attributes(
            Map<String, Object> attributes,
            String boundary,
            Traversal traversal
    ) {
        require(attributes != null, boundary + " are null");
        return detachedMap(attributes, boundary, traversal, 0);
    }

    private Object value(Object value, String boundary, Traversal traversal, int depth) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || immutableNumber(value)) {
            return value;
        }
        if (value instanceof List<?> list) {
            enter(list, list.size(), traversal, depth);
            try {
                List<Object> copy = new ArrayList<>(list.size());
                list.forEach(item -> copy.add(value(item, boundary, traversal, depth + 1)));
                return Collections.unmodifiableList(copy);
            } finally {
                leave(list, traversal);
            }
        }
        if (value instanceof Set<?> set) {
            enter(set, set.size(), traversal, depth);
            try {
                Set<Object> copy = new LinkedHashSet<>();
                set.forEach(item -> copy.add(value(item, boundary, traversal, depth + 1)));
                return Collections.unmodifiableSet(copy);
            } finally {
                leave(set, traversal);
            }
        }
        if (value instanceof Map<?, ?> map) {
            return detachedMap(map, boundary, traversal, depth);
        }
        throw new AdaptorContractException(
                "adaptor result contract violation: " + boundary + " contain an unsupported mutable value");
    }

    private Map<String, Object> detachedMap(
            Map<?, ?> map,
            String boundary,
            Traversal traversal,
            int depth
    ) {
        enter(map, map.size(), traversal, depth);
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                require(key instanceof String && !((String) key).isBlank(),
                        boundary + " contain an invalid key");
                copy.put((String) key, value(item, boundary, traversal, depth + 1));
            });
            return Collections.unmodifiableMap(copy);
        } finally {
            leave(map, traversal);
        }
    }

    private void enter(Object container, int elements, Traversal traversal, int depth) {
        if (depth > MAX_DEPTH || traversal.elements > MAX_CONTAINER_ELEMENTS - elements
                || traversal.active.put(container, Boolean.TRUE) != null) {
            throw new AdaptorContractException(STRUCTURAL_LIMIT_FAILURE);
        }
        traversal.elements += elements;
    }

    private void leave(Object container, Traversal traversal) {
        traversal.active.remove(container);
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

    private static final class Traversal {
        private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
        private int elements;
    }
}
