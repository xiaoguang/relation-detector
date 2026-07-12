package com.relationdetector.core.fullgrammar;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * 版本化 full-grammar 方言 module。
 *
 * <p>CN: adaptor 通过 ServiceLoader 注册该接口，实现具体版本 SQL/DDL parser 的构造。
 * core 只依赖接口，不直接 import MySQL/PostgreSQL 实现类。
 *
 * <p>EN: Versioned full-grammar dialect module registered by adaptors through
 * ServiceLoader. It constructs concrete versioned SQL/DDL parsers while core
 * depends only on this interface.
 */
public interface FullGrammarDialectModule {
    SqlGrammarProfile profile();

    String implementationName();

    StructuredSqlParser sqlParser();

    StructuredDdlParser structuredDdlParser();
}
