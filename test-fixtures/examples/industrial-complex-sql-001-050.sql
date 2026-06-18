-- Industrial complex SQL examples, SQL 001-050.
-- These statements are saved as parser stress samples and are not expected to run
-- against the project test databases without matching demo schemas.

-- SQL 001: 窗口函数无缝嵌套多层 CASE WHEN 与字段双竖线动态组合
SELECT 
    tr.transaction_id,
    'TX_' || tr.merchant_code || '_' || TO_CHAR(tr.created_at, 'YYYYMMDD') || '_' ||
    LEAD(tr.status, 1, 'END') OVER (PARTITION BY tr.user_id ORDER BY tr.created_at) AS sequence_fingerprint
FROM payment_transactions tr
WHERE tr.narrative LIKE '%' || (SELECT config_val FROM system_config WHERE config_key = 'audit_pattern') || '%';

-- SQL 002: 多路 LEFT JOIN 下的 COALESCE 密集拼接与 ILIKE 大小写模糊过滤
SELECT 
    u.id,
    COALESCE(p.first_name, '') || ' ' || COALESCE(p.last_name, 'ANONYMOUS') || ' <' || 
    COALESCE(u.email, 'no-email') || '> [Role: ' || COALESCE(r.role_name, 'GUEST') || ']' AS user_manifest
FROM users u
LEFT JOIN profiles p ON u.id = p.user_id
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
WHERE COALESCE(p.biography, '') ILIKE '%senior%' || COALESCE(r.role_name, 'developer') || '%';

-- SQL 003: 复杂递归图结构路径组装与前缀 LIKE 语义校验
WITH RECURSIVE asset_hierarchy AS (
    SELECT id, parent_id, asset_name, CAST(asset_name AS TEXT) AS node_path, 1 AS depth
    FROM infrastructure_nodes WHERE parent_id IS NULL
    UNION ALL
    SELECT i.id, i.parent_id, i.asset_name, ah.node_path || ' / ' || i.asset_name, ah.depth + 1
    FROM infrastructure_nodes i
    JOIN asset_hierarchy ah ON i.parent_id = ah.id
)
SELECT * FROM asset_hierarchy 
WHERE node_path LIKE 'DATACENTER_01 / RACK_%' AND depth > 2;

-- SQL 004: 聚合函数 STRING_AGG 内部混合条件判断与动态变量过滤
SELECT 
    tenant_id,
    'SUMMARY_FOR_' || group_name || ': ' || 
    STRING_AGG(CASE WHEN is_active THEN 'ACT_' || item_code ELSE 'INACT' END, '|' ORDER BY priority DESC) AS processed_batch
FROM inventory_groups
GROUP BY tenant_id, group_name
HAVING STRING_AGG(item_code, ',') LIKE '%' || :input_critical_sku || '%';

-- SQL 005: 关联子查询深度嵌套：外层字段驱动内层动态模糊 LIKE 校验
SELECT a.account_id, a.account_number
FROM financial_accounts a
WHERE EXISTS (
    SELECT 1 FROM audit_trail au
    WHERE au.payload_details LIKE '%' || a.account_number || '%action=TRANSFER%'
      AND au.severity = 'CRITICAL'
);

-- SQL 006: 嵌套子查询实现非对称字符串连接与复杂正则表达式代入
SELECT 
    sub.batch_id,
    'BATCH_REF: ' || sub.raw_hash || ' (Count: ' || sub.total_cnt || ')' AS batch_descriptor
FROM (
    SELECT batch_id, MD5(STRING_AGG(payload, '')) AS raw_hash, COUNT(1) AS total_cnt
    FROM raw_events GROUP BY batch_id
) sub
WHERE sub.batch_descriptor NOT LIKE '%[a-f0-9]{32}%';

-- SQL 007: 交叉联接 CROSS JOIN LATERAL 驱动的动态多字段交叉组合
SELECT 
    p.product_id,
    p.product_name || ' -> ' || lat.variant_desc AS full_catalog_name
FROM products p
CROSS JOIN LATERAL (
    SELECT STRING_AGG(v.color || '/' || v.size, '; ') AS variant_desc
    FROM product_variants v WHERE v.product_id = p.product_id
) lat
WHERE lat.variant_desc LIKE '%XL%' AND p.product_name LIKE 'Summer%';

-- SQL 008: 复杂 NULLIF 混杂模式下的多类型安全转换与字符串联结
SELECT 
    client_id,
    'CLIENT_TAG: ' || COALESCE(NULLIF(company_name, ''), NULLIF(legal_name, ''), 'INDIVIDUAL') || 
    ' (' || UPPER(country_code) || ')' AS compliance_label
FROM client_registers
WHERE COALESCE(company_name, '') || COALESCE(legal_name, '') LIKE '%Holdings%';

-- SQL 009: 窗口函数 DENSE_RANK 伴随的字符串前缀树特征值组装
SELECT 
    category,
    'RANK_' || DENSE_RANK() OVER (PARTITION BY category ORDER BY score DESC) || '_' || item_name AS ranking_uid
FROM contest_entries
WHERE item_name LIKE '%\_OFFICIAL\_%' ESCAPE '\';

-- SQL 010: 带有复杂的时区转换（AT TIME ZONE）的动态时间戳标签拼接
SELECT 
    event_id,
    'SERVER_TIME: ' || TO_CHAR(server_time AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS TZH:TZM') || 
    ' | LOCAL_TIME: ' || TO_CHAR(server_time AT TIME ZONE user_timezone, 'YYYY-MM-DD HH24:MI:SS') AS timezone_manifest
FROM system_logs sl
JOIN user_preferences up ON sl.triggered_by = up.user_id
WHERE user_timezone LIKE 'America/%';

-- SQL 011: 极其复杂的双重多层 EXISTS 嵌套内的多级动态模糊匹配
SELECT m.menu_id, m.menu_title
FROM system_menus m
WHERE EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.resource_path LIKE m.route_url || '%_write'
      AND EXISTS (
          SELECT 1 FROM user_roles_mapping urm
          WHERE urm.role_id = rp.role_id AND urm.user_id = :current_active_user
      )
);

-- SQL 012: 内联视图中的多表笛卡尔积组合与长字符串流水线构造
SELECT 
    comb.base_code || '-' || comb.ext_code || '-' || comb.vendor_suffix AS global_sku
FROM (
    SELECT b.code AS base_code, e.code AS ext_code, v.suffix AS vendor_suffix
    FROM base_catalog b, extension_catalog e, vendor_registry v
    WHERE b.status = 'ACTIVE' AND e.is_approved = true
) comb
WHERE comb.base_code || comb.ext_code LIKE '%999%';

-- SQL 013: 带有 OVERLAPS 时间判定和历史审计字段的组合链条
SELECT 
    booking_id,
    'ROOM_' || room_number || ' booked by ' || guest_name || ' (Period: ' || 
    check_in::text || ' to ' || check_out::text || ')' AS booking_summary
FROM hotel_reservations
WHERE (check_in, check_out) OVERLAPS ('2026-06-01'::date, '2026-08-31'::date)
  AND guest_name ILIKE '%smith%';

-- SQL 014: 深度分级下的多字段字符串前缀压缩与尾缀 LIKE 逻辑
SELECT 
    node_id,
    LEFT(node_type, 3) || '_' || REPEAT('0', 4 - LENGTH(id::text)) || id AS internal_matrix_id
FROM network_vertices
WHERE LEFT(node_type, 3) || '_' || id::text LIKE 'SRV\_%1' ESCAPE '\';

-- SQL 015: 包含复杂的数学运算与三角函数转换后的度量值字符串拼接
SELECT 
    point_id,
    'GEO_COORD: X=' || ROUND(CAST(sin(latitude) AS NUMERIC), 4)::text || 
    ' | Y=' || ROUND(CAST(cos(longitude) AS NUMERIC), 4)::text AS spatial_fingerprint
FROM telemetry_points
WHERE 'GEO_COORD: X=' || ROUND(CAST(sin(latitude) AS NUMERIC), 4)::text LIKE '%0.71%';

-- SQL 016: 带有 PARTITION BY 动态哈希分布特征的字符串流式拼接
SELECT 
    file_id,
    'FS_BUCKET_' || MOD(HASHTEXT(file_path), 10)::text || '/' || file_name AS distributed_storage_path
FROM user_uploaded_files
WHERE file_path LIKE '/data/secure/%' AND file_name NOT LIKE '%.tmp';

-- SQL 017: 复杂的多表联合 UNION ALL 结构中的非对称组合字段过滤
SELECT full_identity FROM (
    SELECT 'USR_' || username || '_' || employee_number AS full_identity, dept_id FROM internal_staff
    UNION ALL
    SELECT 'VND_' || vendor_name || '_' || tax_identifier AS full_identity, company_id FROM external_vendors
) consolidated
WHERE consolidated.full_identity LIKE '%\_GLOBAL%' ESCAPE '\' AND consolidated.dept_id = 100;

-- SQL 018: 基于窗口函数 PERCENT_RANK 复杂分位数判断的字符串流
SELECT 
    invoice_id,
    'INV_REF: ' || invoice_number || ' [Tier: ' || 
    CASE 
        WHEN PERCENT_RANK() OVER (ORDER BY grand_total DESC) <= 0.1 THEN 'TOP_TIER'
        ELSE 'STANDARD'
    END || ']' AS dynamic_invoice_class
FROM client_invoices
WHERE invoice_number LIKE 'INV-2026-%';

-- SQL 019: 多级复杂条件标量子查询组合链条
SELECT 
    p.project_id,
    'PROJ_' || p.project_code || ' [Lead: ' || 
    (SELECT e.first_name || ' ' || e.last_name FROM employees e WHERE e.id = p.manager_id) || ']' AS project_descriptor
FROM R_projects p
WHERE p.project_code LIKE 'RESEARCH\_%' ESCAPE '\';

-- SQL 020: 结合不等于关联条件的非等值连接字符串多路连接
SELECT 
    a.rule_id,
    a.rule_name || ' conflicts with ' || b.rule_name AS conflict_manifesto
FROM security_rules a
JOIN security_rules b ON a.rule_id < b.rule_id AND a.port_range && b.port_range
WHERE a.rule_name LIKE 'ALLOW%' AND b.rule_name LIKE 'DENY%';

-- SQL 021: 深度多层嵌套 CASE 表达式驱动的反向 LIKE 条件触发
SELECT 
    order_id,
    'ORD_' || customer_id || '_' || 
    CASE CASE WHEN shipping_country = 'US' THEN 1 ELSE 0 END
        WHEN 1 THEN 'DOMESTIC_' || shipping_state
        ELSE 'INTERNATIONAL_' || shipping_country
    END AS regional_routing_tag
FROM sales_orders
WHERE shipping_method LIKE '%PRIORITY%';

-- SQL 022: 用于复杂分析的 CUBE 维度交叉多字段合并展现
SELECT 
    region, year, product_line,
    'GROUPING: ' || COALESCE(region, 'ALL_REGIONS') || ' | ' || 
    COALESCE(year::text, 'ALL_YEARS') || ' | ' || COALESCE(product_line, 'ALL_LINES') AS cube_signature
FROM sales_analytics_cube
GROUP BY CUBE (region, year, product_line)
HAVING COALESCE(region, 'ALL_REGIONS') LIKE '%PACIFIC%';

-- SQL 023: 过滤含有数据库特定类型转换（如 MACADDR/INET）的字符串组合
SELECT 
    host_id,
    'IP: ' || host_ip::text || ' | MAC: ' || hardware_mac::text AS host_network_signature
FROM datacenter_inventory
WHERE host_ip::text LIKE '10.0.%' AND hardware_mac::text LIKE '00:1a:%';

-- SQL 024: 含有复杂的位运算 (Bitwise Operators) 提取后的数值转化为状态字符串
SELECT 
    device_id,
    'SYS_STATUS_HEX_' || TO_HEX(status_bitmask) || ' [ERR_VAL: ' || 
    CAST((status_bitmask & 255) AS TEXT) || ']' AS machine_hardware_tag
FROM industrial_telemetry
WHERE 'SYS_STATUS_HEX_' || TO_HEX(status_bitmask) LIKE '%_a0';

-- SQL 025: 结合复杂的自连接 (Self-Join) 生成树状链路图字符串
SELECT 
    curr.task_id,
    'TASK_PATH: ' || prev.task_name || ' ==> ' || curr.task_name AS dependency_line
FROM workflow_tasks curr
JOIN workflow_tasks prev ON curr.predecessor_id = prev.task_id
WHERE prev.task_name LIKE '%INITIALIZATION%' AND curr.task_name NOT LIKE '%DEPRECATED%';

-- SQL 026: 嵌套 JSONB 数据强类型转化后的高密集字符串路径组装
SELECT 
    ticket_id,
    'TKT_' || (meta_payload->'routing'->>'queue')::text || '_' || 
    (meta_payload->'customer'->>'priority')::text || '_' || 
    COALESCE(meta_payload->>'sla_tier', 'STANDARD') AS engine_routing_key
FROM helpdesk_tickets
WHERE (meta_payload->'customer'->>'email') LIKE '%@enterprise.com';

-- SQL 027: 复杂的 JSONB 数组横向展开并配合动态 || 字符流生成
SELECT 
    audit_id,
    'AUDIT_RECORD_FOR_' || target_table || ' => CHANGES: ' || 
    STRING_AGG(jsonb_object_keys(delta_json), ', ') AS total_mutation_log
FROM system_audit_json
GROUP BY audit_id, target_table
HAVING STRING_AGG(jsonb_object_keys(delta_json), ', ') LIKE '%auth_token%';

-- SQL 028: 使用布尔路径（jsonb_path_exists）的高级 JSON 查询与 LIKE 联动
SELECT device_uuid, hardware_profile
FROM edge_devices
WHERE jsonb_path_exists(hardware_profile, '$.sensors[*].type')
  AND (hardware_profile->'location'->>'building_id') LIKE 'BLDG\_%_NORTH' ESCAPE '\';

-- SQL 029: 混合 JSONB 数据包提取与窗口函数计算的组合字段
SELECT 
    item_id,
    'JSON_EXTRACT_VAL: ' || (attributes->>'manufacturer') || ' [Rank ' || 
    ROW_NUMBER() OVER (PARTITION BY (attributes->>'category') ORDER BY (attributes->>'price')::numeric DESC) || ']' AS dynamic_json_metric
FROM ecom_catalog_json
WHERE (attributes->>'model_no') LIKE 'MOD-%';

-- SQL 030: 多层 JSONB 级联构建（jsonb_build_object）中的动态字段字符串捕获
SELECT 
    user_id,
    'METADATA_STRING: ' || jsonb_build_object('uname', login_name, 'domain', user_domain)::text AS json_string_blob
FROM user_credentials
WHERE login_name || '@' || user_domain LIKE '%admin%';

-- SQL 031: JSONB 过滤特殊字符与转义的高密度 LIKE 模式串
SELECT 
    config_id,
    'CFG_PATH: ' || (config_blob->'routing'->>'endpoint') AS endpoint_path
FROM service_mesh_configs
WHERE (config_blob->'routing'->>'endpoint') LIKE 'https://api.internal.%/v[1-3]/%';

-- SQL 032: 利用 jsonb_each_text 动态抽取嵌套字典并完成拼接过滤
SELECT 
    kv_store.id,
    'EXTRACTED ==> Key: ' || entry.key || ' | Value: ' || entry.value AS fully_flattened_pair
FROM kv_store,
LATERAL jsonb_each_text(kv_store.custom_properties) AS entry
WHERE entry.value LIKE 'VAL_CONFIRMED_%';

-- SQL 033: 在复合过滤条件中提取 JSON 数组元素结合字符串拼接
SELECT 
    order_id,
    'ORD_JSON_SUMMARY: ' || (order_json->>'order_no') || ' Total items: ' || 
    jsonb_array_length(order_json->'items')::text AS order_json_summary
FROM complex_sales_json
WHERE order_json->>'status' LIKE '%SHIPPED%' AND jsonb_array_length(order_json->'items') > 5;

-- SQL 034: 高并发环境下的操作日志 JSONB 对象包含关系判断与组合字段
SELECT 
    session_id,
    'SESSION_USER: ' || (context_json->>'user_id') || ' IP: ' || (context_json->>'remote_ip') AS user_session_stamp
FROM user_session_contexts
WHERE context_json @> '{"auth_method": "OAUTH2"}'
  AND (context_json->>'user_id') LIKE 'USR\_2026%' ESCAPE '\';

-- SQL 035: 处理包含未知字段的动态 JSON 强行合并输出
SELECT 
    doc_id,
    'DOC_HASH: ' || MD5(raw_document::text) || ' | OWNER: ' || COALESCE(raw_document->>'author', 'SYSTEM') AS doc_fingerprint
FROM unorganized_documents
WHERE raw_document::text LIKE '%"classification": "TOP_SECRET"%';

-- SQL 036: CTE 结构中对大规模 JSON 数据重组、扁平化与模糊验证
WITH flattened_json_cte AS (
    SELECT id, (json_data->>'category') AS cat, (json_data->>'sku') AS sku_code
    FROM product_json_dump
)
SELECT id, 'CAT_CODE: ' || cat || ' / ' || sku_code AS generated_sku
FROM flattened_json_cte
WHERE sku_code LIKE '%-X[0-9]%';

-- SQL 037: 带有复杂的 JSONB 深度更新函数（jsonb_set）后的字符串展示
SELECT 
    app_id,
    'UPDATED_CONFIG_TEXT: ' || jsonb_set(app_config, '{env,database,port}', '"5432"'::jsonb)::text AS mutated_config_text
FROM enterprise_apps
WHERE app_name LIKE 'Core\_%' ESCAPE '\';

-- SQL 038: 提取复杂的错误堆栈 JSON 属性组合成易读的可视化日志
SELECT 
    err_id,
    'EXCEPTION_CAUGHT_AT: ' || (error_payload->'context'->>'class') || 
    '::' || (error_payload->'context'->>'method') || ' Line: ' || (error_payload->'context'->>'line') AS stack_trace_line
FROM application_error_logs
WHERE error_payload->>'message' LIKE '%NullPointerException%';

-- SQL 039: 带有 JSON 属性合并操作符（|| 针对 jsonb）和常规字符串 || 的大杂烩
SELECT 
    record_id,
    'COMBINED_METADATA_STR: ' || ((base_meta || extended_meta)->>'version')::text || 
    '_PROD_' || record_code AS deep_json_concat_string
FROM product_metadata_variants
WHERE (base_meta || extended_meta)->>'status' LIKE '%PRODUCTION%';

-- SQL 040: 基于 JSON 属性的长文本 LIKE 匹配排查潜在 SQL 注入
SELECT 
    req_id,
    'REQ_IP: ' || client_ip || ' -> COMPROMISED_PARAM: ' || (request_params->>'query_string') AS injection_alert_log
FROM http_request_dumps
WHERE request_params->>'query_string' LIKE '%UNION%SELECT%' OR request_params->>'query_string' LIKE '%OR%1=1%';

-- SQL 041: 结合多层复合嵌套对象的 JSONB 数组检索与字段拼接
SELECT 
    id,
    'USER_GROUP: ' || (group_info->>'group_name') || ' | PERM_COUNT: ' || 
    jsonb_array_length(group_info->'permissions')::text AS authorization_payload
FROM security_groups_json
WHERE (group_info->'permissions') @> '"administrator"'::jsonb;

-- SQL 042: 对动态嵌套表进行行转列、提取键、组合键前缀的超级匹配
SELECT 
    t.id,
    'DYNAMIC_KEY: ' || j.key || ' | MAPPED_VALUE: ' || j.value AS dynamic_key_value_descriptor
FROM dynamic_attribute_tables t,
LATERAL jsonb_each_text(t.flexible_properties) j
WHERE j.key LIKE 'custom\_attr\_%' ESCAPE '\' AND j.value LIKE '%CRITICAL%';

-- SQL 043: 使用大范围 CASE WHEN 判定 JSONB 数据类型并进行安全组合
SELECT 
    node_id,
    'NODE_TYPE: ' || jsonb_typeof(node_data->'configuration') || 
    ' | ASSIGNED_IP: ' || COALESCE(node_data->>'ip_address', '0.0.0.0') AS generalized_node_signature
FROM variable_network_nodes
WHERE node_data->>'ip_address' LIKE '192.168.%';

-- SQL 044: 将多态 JSON 转换并应用窗口函数提取特定拼接行
SELECT 
    ranked_json.entity_id,
    'TOP_PROPERTY: ' || ranked_json.prop_key || ' = ' || ranked_json.prop_val AS leading_property_manifest
FROM (
    SELECT entity_id, entry.key AS prop_key, entry.value AS prop_val,
           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY entry.key) AS rnk
    FROM polymorphic_entities, LATERAL jsonb_each_text(polymorphic_properties) AS entry
) ranked_json
WHERE ranked_json.rnk = 1 AND ranked_json.prop_val LIKE 'System%';

-- SQL 045: 针对复杂嵌套嵌套再嵌套的 JSON 树的深度节点过滤与组装
SELECT 
    container_id,
    'INNER_VAL: ' || (nested_tree->'level1'->'level2'->'level3'->>'target_value') AS leaf_node_value
FROM highly_nested_json_structures
WHERE (nested_tree->'level1'->'level2'->'level3'->>'target_value') LIKE '%_VALIDATED' ESCAPE '\';

-- SQL 046: 综合应用子查询、JSONB 映射过滤、多字段结合的数据库元数据报告
SELECT 
    tbl.id,
    'METADATA_REPORT_FOR: ' || tbl.table_name || ' | SCHEMA: ' || 
    (tbl.schema_info->>'schema_owner') || ' | STRUCT: ' || (tbl.schema_info->>'storage_type') AS structural_report
FROM database_table_registry tbl
WHERE (tbl.schema_info->>'schema_owner') NOT LIKE 'pg\_%' ESCAPE '\'
  AND tbl.table_name LIKE 'biz\_%';

-- SQL 047: 在复杂的 CTE 和多层合并下抽离 JSON 数据中的敏感字段并进行校验
WITH filtered_logs_cte AS (
    SELECT log_id, security_context->>'user_token' AS token, security_context->>'auth_status' AS status
    FROM security_raw_logs
    WHERE security_context->>'auth_status' IS NOT NULL
)
SELECT log_id, 'MASKED_TOKEN: ' || SUBSTRING(token FROM 1 FOR 8) || '******' AS hidden_token_string
FROM filtered_logs_cte
WHERE status LIKE '%FAILED_ATTEMPT%' AND token LIKE 'TK-%';

-- SQL 048: 联合数组元素抽取与 JSONB 属性连接的高维度查询
SELECT 
    pkg.package_id,
    'PKG_MANIFEST: ' || pkg.package_name || ' | DEPENDENCY: ' || deps.element::text AS package_dependency_string
FROM software_packages pkg,
LATERAL jsonb_array_elements_text(pkg.manifest_json->'dependencies') AS deps(element)
WHERE pkg.package_name LIKE 'org.apache.%' AND deps.element LIKE 'hive-%';

-- SQL 049: 从大规模监控日志 JSON 提取各项硬件指标并拼接
SELECT 
    host_uuid,
    'METRIC_SNAPSHOT => CPU: ' || (metrics_payload->'cpu'->>'usage_pct') || '% | MEM: ' || 
    (metrics_payload->'memory'->>'used_gb') || 'GB' AS hardware_performance_snapshot
FROM cluster_monitoring_payloads
WHERE (metrics_payload->'cpu'->>'usage_pct')::numeric > 90.0
  AND (metrics_payload->'os'->>'kernel_version') LIKE '%-generic%';

-- SQL 050: 对多级树形业务线 JSON 数据进行聚合判定与组合字段加工
SELECT 
    business_unit_id,
    'BU: ' || bu_name || ' | AUDIT_STATUS: ' || 
    CASE 
        WHEN (compliance_json->'audit'->'summary'->>'has_violations')::boolean = true THEN 'VIOLATION_DETECTED_IN_' || (compliance_json->'audit'->'summary'->>'violation_count')
        ELSE 'COMPLIANT_AS_OF_' || (compliance_json->'audit'->'summary'->>'last_audit_date')
    END AS final_compliance_text
FROM business_units_compliance
WHERE compliance_json->'audit'->'summary'->>'auditor_name' LIKE '%Inspector%';
