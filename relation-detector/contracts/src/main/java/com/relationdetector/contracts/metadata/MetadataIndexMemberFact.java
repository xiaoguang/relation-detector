package com.relationdetector.contracts.metadata;

/**
 * CN: 保存一个索引成员的真实 ordinal 和 typed 内容，供 collector 按数据库声明顺序输出并由 core 校验。
 * column member 与 expression member 互斥，prefix length 只属于前缀列；本 record 不信任 adaptor 输入，
 * 具体 shape 与连续 ordinal 由 core 契约边界统一拒绝或接纳。
 *
 * <p>EN: Carries the real ordinal and typed content of one index member for
 * collector output in database declaration order and core-side contract validation. Column and expression
 * members are mutually exclusive, and prefix length belongs only to a prefix column. The record itself does not
 * trust adaptor input; core owns shape and contiguous-ordinal validation.
 */
public record MetadataIndexMemberFact(
        int ordinal,
        MetadataIndexMemberKind kind,
        String columnName,
        String expression,
        Integer prefixLength
) {
    public static MetadataIndexMemberFact fullColumn(int ordinal, String columnName) {
        return new MetadataIndexMemberFact(
                ordinal, MetadataIndexMemberKind.FULL_COLUMN, columnName, null, null);
    }

    public static MetadataIndexMemberFact prefixColumn(int ordinal, String columnName, int prefixLength) {
        return new MetadataIndexMemberFact(
                ordinal, MetadataIndexMemberKind.PREFIX_COLUMN, columnName, null, prefixLength);
    }

    public static MetadataIndexMemberFact expression(int ordinal, String expression) {
        return new MetadataIndexMemberFact(
                ordinal, MetadataIndexMemberKind.EXPRESSION, null, expression, null);
    }
}
