package com.relationdetector.core.adaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AdaptorResultDetachmentSupportTest {
    private static final String LIMIT_FAILURE =
            "adaptor result contract violation: adaptor attributes exceed structural limits";

    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    @Test
    void rejectsContainerBackEdgesWithoutRenderingPluginContent() {
        List<Object> cycle = new ArrayList<>();
        cycle.add("secret-canary");
        cycle.add(cycle);

        AdaptorContractException failure = assertThrows(
                AdaptorContractException.class,
                () -> detachment.attributes(Map.of("plugin", cycle), "profile secret-canary"));

        assertEquals(LIMIT_FAILURE, failure.getMessage());
        assertFalse(failure.getMessage().contains("secret-canary"));
    }

    @Test
    void permitsSharedAcyclicContainersAndDetachesEachReference() {
        List<Object> shared = new ArrayList<>(List.of("safe"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("left", shared);
        source.put("right", shared);

        Map<String, Object> copy = detachment.attributes(source, "profile attributes");

        assertEquals(List.of("safe"), copy.get("left"));
        assertEquals(List.of("safe"), copy.get("right"));
        assertNotSame(copy.get("left"), copy.get("right"));
    }

    @Test
    void rejectsDepthBeyondTheCallLimit() {
        Object value = "leaf";
        for (int depth = 0; depth < 65; depth++) {
            value = List.of(value);
        }
        Object tooDeep = value;

        AdaptorContractException failure = assertThrows(
                AdaptorContractException.class,
                () -> detachment.attributes(Map.of("value", tooDeep), "profile attributes"));

        assertEquals(LIMIT_FAILURE, failure.getMessage());
    }

    @Test
    void rejectsMoreThanTenThousandContainerElementsAcrossTheGraph() {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < 10_001; index++) {
            values.add(index);
        }

        AdaptorContractException failure = assertThrows(
                AdaptorContractException.class,
                () -> detachment.attributes(Map.of("values", values), "profile attributes"));

        assertEquals(LIMIT_FAILURE, failure.getMessage());
    }
}
