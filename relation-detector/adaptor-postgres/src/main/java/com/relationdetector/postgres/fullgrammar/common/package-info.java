/**
 * Shared PostgreSQL full-grammar adaptor infrastructure.
 *
 * <p>CN: 放置 PostgreSQL v16/v17/v18 共同的 full-grammar parser 生命周期、
 * generated grammar binding 接口、diagnostic attributes、warning 处理，以及
 * SQL/DDL event visitor core。这里不持有任何具体 generated parser 类型；版本包
 * 仍然负责绑定自己的 `.g4` 生成类和版本差异。
 *
 * <p>EN: Shared PostgreSQL full-grammar adaptor infrastructure. This package
 * owns common parser lifecycle, generated grammar binding interfaces,
 * diagnostic attributes, warnings, and SQL/DDL event visitor cores for
 * v16/v17/v18. It does not hold concrete generated parser types; version
 * packages still bind their own `.g4` generated classes and version-specific
 * differences.
 */
package com.relationdetector.postgres.fullgrammar.common;
