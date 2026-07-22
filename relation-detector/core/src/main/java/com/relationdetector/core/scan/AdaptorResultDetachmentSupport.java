package com.relationdetector.core.scan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
