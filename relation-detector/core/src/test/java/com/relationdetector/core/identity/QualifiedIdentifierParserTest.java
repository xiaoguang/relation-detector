package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.identifier.QualifiedIdentifierParser;

final class QualifiedIdentifierParserTest {
    @Test
    void splitsTypedIdentifiersWithoutBreakingQuotedDots() {
        assertEquals(
                List.of("\"catalog.with.dot\"", "[schema.with.dot]", "`table.with.dot`"),
                QualifiedIdentifierParser.parts(
                        "\"catalog.with.dot\".[schema.with.dot].`table.with.dot`"));
    }

    @Test
    void separatesAQualifiedColumnAtTheLastTypedSegment() {
        assertEquals(
                new QualifiedIdentifierParser.LastSegment("\"shop.data\".orders", "\"order.id\""),
                QualifiedIdentifierParser.splitLast("\"shop.data\".orders.\"order.id\""));
    }

    @Test
    void rejectsIncompleteOrUnterminatedIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> QualifiedIdentifierParser.splitLast("orders"));
        assertThrows(IllegalArgumentException.class,
                () -> QualifiedIdentifierParser.parts("\"orders.id"));
    }
}
