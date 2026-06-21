/**
 * Shared PostgreSQL full-grammer adaptor infrastructure.
 *
 * <p>CN: 放置 PostgreSQL v16/v17/v18 共同的 full-grammer parser 生命周期、
 * diagnostic attributes、warning 处理和 DDL event sink。这里不持有任何具体
 * generated parser 类型；版本包仍然负责绑定自己的 `.g4` 生成类和版本差异。
 *
 * <p>EN: Shared PostgreSQL full-grammer adaptor infrastructure. This package
 * owns common parser lifecycle, diagnostic attributes, warnings, and DDL event
 * sink behavior for v16/v17/v18. It does not hold concrete generated parser
 * types; version packages still bind their own `.g4` generated classes and
 * version-specific differences.
 */
package com.relationdetector.postgres.fullgrammer.common;
