package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.DatabaseObjectType;

/**
 * 数据库对象定义。
 *
 * <p>CN: procedure、function、view、trigger 等对象可以来自数据库采集或对象文件，
 * 后续按 source type 进入 SQL parser。
 *
 * <p>EN: Database object definition for procedures, functions, views, triggers,
 * and related objects collected from the database or object files before SQL parsing.
 */
public record DatabaseObjectDefinition(
        DatabaseObjectType type,
        String catalog,
        String schema,
        String name,
        String sql,
        String source
) {
}
