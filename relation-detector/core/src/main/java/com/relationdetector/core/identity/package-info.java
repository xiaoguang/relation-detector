/**
 * CN: catalog/schema/table 的 canonical identifier 与 alias scope 解析。
 *
 * <p>EN: Canonical catalog/schema/table identity and alias-scope resolution.
 * <p>Responsibility: 解析 catalog/schema/table/column 的 canonical identity / Resolves canonical physical identities.
 * <p>Inputs: explicit identifiers、namespace context 与 alias bindings / Identifiers, namespaces, and aliases.
 * <p>Outputs: namespace-aware table/endpoint keys / Namespace-aware table and endpoint keys.
 * <p>Upstream/Downstream: parser/metadata 输入，relation/lineage/naming 消费 / Feeds relation, lineage, and naming layers.
 * <p>Forbidden: 不把裸表自动等同任意 qualified table / Must not equate bare and arbitrary qualified tables.
 */
package com.relationdetector.core.identity;
