-- Provided complex SQL collection.
-- Source:
-- - 001-025 from attachment 87388f7d-2c4c-499a-b085-d158b969af45/pasted-text.txt
-- - 026-050 from attachment c61899d0-fc27-4a25-974a-56bc89fb0b7f/pasted-text.txt
-- - 051-075 from the chat message on 2026-06-18.
-- Notes:
-- - SQL 052 appears truncated in the chat message and is preserved as received.
-- - SQL 053-058 were not present in the supplied text.

-- 001. 结合 CASE WHEN 的多字段条件拼接
SELECT emp_id, 'EMP_' || UPPER(first_name) || '_' || CASE WHEN dept_id IS NULL THEN 'UNASSIGNED' WHEN dept_id = 10 THEN 'HQ_' || LOWER(job_title) ELSE 'DEPT_' || CAST(dept_id AS TEXT) END || '_' || TO_CHAR(hire_date, 'YYYYMMDD') AS full_employee_tag FROM employees;

-- 002. 结合正则与特殊字符转义的复杂 LIKE
SELECT id, doc_title, tags FROM documents WHERE (doc_title LIKE 'ARCHIVE\_202[0-6]\_%' ESCAPE '\') AND id IN (SELECT doc_id FROM revisions WHERE change_summary NOT LIKE '%draft%') AND tags && ARRAY['finance'::text, 'internal'::text] AND metadata->>'status' LIKE '%APPROVED%';

-- 003. 基于窗口函数的动态 LIKE 过滤
SELECT tenant_id, user_code, session_id FROM (SELECT tenant_id, user_code, session_id, FIRST_VALUE(user_code) OVER (PARTITION BY tenant_id ORDER BY login_time) AS first_user FROM user_sessions) t WHERE t.user_code LIKE t.first_user || '_PREFIX_%';

-- 004. 聚合函数中的字符串拼接 STRING_AGG
SELECT department_id, 'DEPT_SUMMARY: ' || STRING_AGG(employee_code || ':' || position, ', ' ORDER BY hire_date DESC) AS rolled_up_staff FROM corporate_structures GROUP BY department_id HAVING STRING_AGG(position, '|') LIKE '%MANAGER%';

-- 005. 递归 CTE 中的路径字符串无限组装
WITH RECURSIVE org_path AS (SELECT id, parent_id, name, CAST(name AS TEXT) AS path FROM categories WHERE parent_id IS NULL UNION ALL SELECT c.id, c.parent_id, c.name, p.path || ' -> ' || c.name FROM categories c JOIN org_path p ON c.parent_id = p.id) SELECT * FROM org_path WHERE path LIKE 'Root -> Electronics -> %';

-- 006. COALESCE 空值降级的大面积字符串拼接
SELECT cust_id, COALESCE(address_line1, '') || ' ' || COALESCE(address_line2, 'N/A') || ', ' || COALESCE(city, 'Unknown City') || ' ' || COALESCE(postal_code, '000000') AS full_shipping_address FROM customers WHERE COALESCE(address_line1, '') || COALESCE(city, '') LIKE '%Street%';

-- 007. 全文检索与 ILIKE 大小写不敏感混合查询
SELECT item_id, description FROM inventory_items WHERE (description ILIKE '%heavy-duty%' OR SKU_code LIKE 'HD\_%') AND textsearch_vector @@ to_tsquery('english', 'warehouse & logistics') AND supplier_notes NOT ILIKE '%deprecated%';

-- 008. 关联子查询（Correlated Subquery）中的动态 LIKE
SELECT main.category_id, main.product_name FROM products main WHERE EXISTS (SELECT 1 FROM audit_logs sub WHERE sub.action_details LIKE '%' || main.product_code || '%Successfully Created%' AND sub.log_level = 'INFO');

-- 009. JSONB 字段提取、类型转换与字符串拼接
SELECT log_id, 'payload_err: ' || (raw_json->'error'->>'code')::text || ' | msg: ' || COALESCE(raw_json->>'message', 'none') AS formatted_err FROM system_events WHERE (raw_json->'user'->>'email') LIKE '%@company.com';

-- 010. 超级大嵌套：派生表 + 动态变量拼接 LIKE
SELECT res.report_date, 'REP_v1_' || res.manager_name || '_' || TO_CHAR(CURRENT_DATE, 'YYYY') AS report_identifier, res.summary_text FROM (SELECT d.created_at::date AS report_date, e.first_name || ' ' || e.last_name AS manager_name, 'Total Active: ' || COUNT(p.id) || ' | Active Codes: ' || STRING_AGG(p.product_code, ';') AS summary_text FROM departments d LEFT JOIN employees e ON d.manager_id = e.id LEFT JOIN products p ON p.dept_id = d.id WHERE p.status = 'ACTIVE' AND (p.product_code LIKE 'PROD\_%' ESCAPE '\') GROUP BY d.created_at, e.first_name, e.last_name) res WHERE res.summary_text LIKE '%Active Codes: ' || :input_variable || '%';

-- 011. 数组元素构造与包含条件下的 LIKE 复合筛选
SELECT id, array_to_string(hist_array, '|') AS flat_history FROM audit_trails WHERE array_to_string(hist_array, ',') LIKE '%ACTION_INIT%' AND sys_domain LIKE 'DOM_' || :cluster_id;

-- 012. 字符位置查找与多层嵌套拼接
SELECT uid, SUBSTRING(token_str FROM 1 FOR 5) || '***' || RIGHT(token_str, 4) AS masked_token FROM security_vault WHERE token_str LIKE '%' || :salt_val || '%' AND token_type LIKE 'JWT\_%' ESCAPE '\';

-- 013. 多维 CASE 结构深度生成复合关联标识
SELECT order_id, CASE WHEN shipping_region = 'US' THEN 'DOM_' || carrier_name WHEN shipping_region = 'EU' THEN 'INT_' || carrier_name || '_EUR' ELSE 'GLOBAL_' || COALESCE(carrier_name, 'UNKNOWN') END || '_' || tracking_number AS master_barcode FROM shipments WHERE tracking_number LIKE 'TRK%';

-- 014. 动态带入模式匹配与 NULL 敏感型测试
SELECT item_code FROM parts_catalog WHERE item_code LIKE '%' || NULLIF(:input_pattern, '') || '%' AND description NOT LIKE '%obsolete%';

-- 015. 视图嵌套模拟：多层拼接转换
SELECT client_name, 'ADDR:' || prov || '-' || city || '-' || street AS geo_hash FROM (SELECT name AS client_name, split_part(address, ',', 1) AS prov, split_part(address, ',', 2) AS city, split_part(address, ',', 3) AS street FROM client_base) sub WHERE prov || city LIKE '%North%';

-- 016. 带有时区格式化和时间戳转换的字段强组装
SELECT event_id, 'TZ_LOG_[' || TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') || ']_[NODE_' || cluster_node_id || ']' AS node_signature FROM system_heartbeats WHERE cluster_node_id::text LIKE '10.%';

-- 017. 子查询拉平后的多字段 Like 交叉检验
SELECT a.id, b.info_summary FROM accounts a JOIN (SELECT account_id, STRING_AGG(meta_val, '-') AS info_summary FROM account_metadata GROUP BY account_id) b ON a.id = b.account_id WHERE b.info_summary LIKE '%' || a.account_type || '%';

-- 018. 带有数学运算及进制转换的混淆字符串拼接
SELECT prod_id, 'HEX_' || to_hex(price::integer) || '_QTY_' || LPAD(quantity::text, 6, '0') AS warehouse_sku FROM stock_ledger WHERE 'HEX_' || to_hex(price::integer) LIKE '%AFA%';

-- 019. 正则表达式类似物 SIMILAR TO 与 LIKE 的混合解析测试
SELECT usr_id FROM client_registry WHERE usr_name SIMILAR TO '(Alex|Bob|Charlie)%' AND email_address NOT LIKE '%@throwaway.com' AND user_flags || '_VAL' LIKE '%ACTIVE_VAL%';

-- 020. 结合位运算和状态位拼接的审计语句
SELECT bit_id, 'MASK:' || (status_mask & 15)::text || ':STR:' || desc_text AS full_mask_str FROM binary_status_logs WHERE 'MASK:' || (status_mask & 15)::text LIKE 'MASK:7%';

-- 021. 触发器动态审计行数据模拟（NEW/OLD 方言模拟）
SELECT 'AUDIT_OLD_VAL:[' || OLD.val1 || '] -> NEW_VAL:[' || NEW.val2 || ']' AS audit_diff FROM audit_simulation_table WHERE OLD.val1 LIKE '%CORE%' AND NEW.val2 NOT LIKE '%DEPRECATED%';

-- 022. 域名解析与 URL 拼接动态校对
SELECT site_id, 'https://' || sub_domain || '.' || primary_domain || '/index.html?ref=' || partner_id AS target_url FROM web_domains WHERE 'https://' || sub_domain || '.' || primary_domain LIKE '%dev.api.company%';

-- 023. 自定义操作符与双竖线级联拼接压测
SELECT node_id, 'NODE_SYS_PATH_LONG_HASH_REPLAY:' || parent_path || '||' || child_path || '||' || leaf_path AS full_tree_node FROM file_clusters WHERE leaf_path LIKE '%.json';

-- 024. 复杂开窗过滤与前置文本匹配
SELECT item_name, 'RANK_' || row_num || '_' || item_name AS rank_tag FROM (SELECT item_name, ROW_NUMBER() OVER (PARTITION BY cat_id ORDER BY score DESC) as row_num FROM contest_entries) sub WHERE sub.item_name LIKE '%Winner%' AND 'RANK_' || row_num LIKE 'RANK_1_%';

-- 025. 账户凭证脱敏与混合加密标识串拼接
SELECT acc_num, 'ACC-99' || REPEAT('*', 8) || RIGHT(card_number, 4) || '-' || UPPER(bank_code) AS secure_ident FROM bank_cards WHERE bank_code LIKE 'B\_%' ESCAPE '\';

-- 026. 带有双重 CTE 的物料清单（BOM）路径组装
WITH sub_components AS (SELECT comp_id, parent_id, comp_name FROM parts WHERE parent_id = 101), assembled_tree AS (SELECT comp_id, 'ROOT/' || comp_name AS node_path FROM sub_components) SELECT * FROM assembled_tree WHERE node_path LIKE 'ROOT/CHASSIS%';

-- 027. 多表关联加多层 Coalesce 的宽表拼接
SELECT m.id, COALESCE(p.name, 'NO_PROD') || '->' || COALESCE(c.title, 'NO_CAT') || '->' || COALESCE(s.label, 'NO_SUB') AS classification FROM main_catalog m LEFT JOIN products p ON m.p_id = p.id LEFT JOIN categories c ON p.c_id = c.id LEFT JOIN sub_labels s ON c.s_id = s.id WHERE COALESCE(p.name, '') LIKE '%Ultra%';

-- 028. 行转列矩阵式复杂拼接过滤
SELECT year_bucket, 'Q1:' || q1_val || '|Q2:' || q2_val || '|Q3:' || q3_val || '|Q4:' || q4_val AS matrix_string FROM quarterly_financial_reports WHERE 'Q1:' || q1_val LIKE '%SURPLUS%';

-- 029. 复杂的多字段复合主键关联动态匹配
SELECT t1.id, 'PK_COMB:[ ' || t1.key_part1 || ' :: ' || t1.key_part2 || ' ]' FROM table_alpha t1 WHERE EXISTS (SELECT 1 FROM table_beta t2 WHERE 'PK_COMB:[ ' || t1.key_part1 || ' :: ' || t1.key_part2 || ' ]' LIKE '%' || t2.search_target || '%');

-- 030. 针对错误日志的高难度文本抓取与提取式拼接
SELECT log_time, 'LEVEL:' || severity || ' [CODE:' || error_code || '] MSG:' || substring(message from 'Exception: (.*)') AS error_report FROM server_logs WHERE message LIKE '%FatalException%' AND severity NOT LIKE 'WARN%';

-- 031. 动态范围生成与数字文本组合拼接
SELECT g.id, 'RANGE_FROM_' || g.low_bound || '_TO_' || g.high_bound AS range_desc FROM numeric_groups g WHERE 'RANGE_FROM_' || g.low_bound LIKE 'RANGE_FROM_100%';

-- 032. 联合查询（UNION ALL）各分支独立进行复合字段拼接
SELECT 'TYPE_A:' || a_name AS generic_title, code_val FROM branch_a WHERE code_val LIKE 'A%' UNION ALL SELECT 'TYPE_B:' || b_name AS generic_title, code_val FROM branch_b WHERE code_val LIKE 'B%';

-- 033. 分组汇总结果集反向拼接与模糊过滤
SELECT grp_id, 'COUNT:' || COUNT(*) || '|AVG:' || ROUND(AVG(amount), 2) AS summary_metrics FROM ledger_tx GROUP BY grp_id HAVING 'COUNT:' || COUNT(*) LIKE 'COUNT:1%';

-- 034. JSONB 数组拉平(jsonb_array_elements_text)后的拼接检索
SELECT j_id, 'ELEMENT_' || elem_val AS flat_elem FROM json_store, LATERAL jsonb_array_elements_text(config_data->'options') AS elem_val WHERE elem_val LIKE 'OPT\_%' ESCAPE '\';

-- 035. 自联结表的多代关系链名字拼接
SELECT p1.name || ' is parent of ' || p2.name || ' and grandparent of ' || p3.name AS family_line FROM family_tree p1 JOIN family_tree p2 ON p1.id = p2.parent_id JOIN family_tree p3 ON p2.id = p3.parent_id WHERE p1.name LIKE 'Grand%';

-- 036. 模拟地理坐标经纬度文本哈希化拼接
SELECT loc_id, 'LAT:' || latitude::text || ',LON:' || longitude::text AS geo_point FROM central_coordinates WHERE 'LAT:' || latitude::text LIKE 'LAT:40.%';

-- 037. 复杂的基于多层 IN 子查询的字段级联组合
SELECT id, 'VER_[' || major_version || '.' || minor_version || ']' AS semantic_version FROM system_releases WHERE 'VER_[' || major_version || '.' || minor_version || ']' IN (SELECT 'VER_[' || v_maj || '.' || v_min || ']' FROM beta_builds WHERE build_status LIKE 'STABLE%');

-- 038. 类似多路复用器的条件拼接查询
SELECT channel_id, 'CH_MUX_' || COALESCE(alias_a, alias_b, alias_c, 'DEFAULT_CH') AS multiplex_id FROM streaming_channels WHERE alias_a LIKE 'LIVE%' OR alias_b LIKE 'BACKUP%';

-- 039. 基于字符串间隔符号切分的二次重组
SELECT id, split_part(raw_csv, ';', 1) || ' - ' || split_part(raw_csv, ';', 3) AS extracted_pair FROM import_dump WHERE raw_csv LIKE '%;%;%';

-- 040. 特殊哈希算法结果的盐值逆向拼接比对
SELECT user_id, 'SALT_SHA256_' || md5(username || :secret_salt) AS mock_hash FROM identity_directory WHERE 'SALT_SHA256_' || md5(username || :secret_salt) LIKE '%e10adc%';

-- 041. 并发锁状态与进程标识拼接监控语句
SELECT pid, 'PID_[' || pid || ']:LOCK_MODE_[' || locktype || ':' || mode || ']' AS process_lock_status FROM pg_locks WHERE mode LIKE '%Exclusive%';

-- 042. 带有行级限流和偏移的高级 DQL 字符串断句
SELECT item_id, 'ITEM_REF_' || lpad(item_id::text, 10, 'X') FROM manufacturing_skus WHERE 'ITEM_REF_' || lpad(item_id::text, 10, 'X') LIKE 'ITEM_REF_XXXX%' LIMIT 50 OFFSET 10;

-- 043. 关联更新预查：带前缀的高级多字段校验
SELECT id, 'CHECK_SUM:' || src_ip || '->' || dest_ip AS network_vector FROM packet_logs WHERE 'CHECK_SUM:' || src_ip || '->' || dest_ip LIKE '%->192.168.%';

-- 044. 结合无符号整数与负数转换的边界级联拼接
SELECT idx, 'VAL_INT64_' || (big_counter::numeric + 9223372036854775807)::text AS extended_bounds FROM extreme_counters WHERE 'VAL_INT64_' || big_counter::text LIKE '%-999%';

-- 045. 重复字符填充与动态业务前缀混淆
SELECT cid, repeat('0', 4 - length(branch_id::text)) || branch_id::text || '-' || customer_uuid AS full_account_id FROM internal_accounts WHERE customer_uuid::text LIKE '8-%';

-- 046. 多重条件与逻辑取反（NOT LIKE）的网格级联
SELECT id, segment_code FROM customer_profiles WHERE segment_code LIKE 'SEG\_%' ESCAPE '\' AND segment_code NOT LIKE '%_VIP_%' AND segment_code || '_EXT' LIKE '%ACTIVE_EXT';

-- 047. 交叉连接（CROSS JOIN）下的双表全字段笛卡尔积拼接
SELECT t1.prefix_val || '_' || t2.suffix_val AS combined_permutation FROM vocabulary_prefix t1 CROSS JOIN vocabulary_suffix t2 WHERE t1.prefix_val LIKE 'PRE%' AND t2.suffix_val LIKE '%SUF';

-- 048. 复合窗口排序（DENSE_RANK）条件下的文本组合
SELECT student_id, 'RANK:' || DENSE_RANK() OVER (ORDER BY test_score DESC) || '|NAME:' || full_name FROM exam_results WHERE full_name LIKE 'Zhang%';

-- 049. 元数据字段导出：多系统关联编码组装
SELECT meta_id, 'SYS_' || source_system || '_EXT_' || external_id || '_REV_' || revision_number AS unified_meta_key FROM upstream_metadata_sync WHERE source_system LIKE 'ERP%';

-- 050. 空字符串与多空格合并过滤压测
SELECT id, TRIM('   ' || leading_tag || ' ' || trailing_tag || '   ') AS absolute_trimmed_tag FROM tag_dictionary WHERE leading_tag LIKE '%_START';

-- 051. 复合度量指标横向组装与阈值检测
SELECT metric_id, 'CPU:' || cpu_idle || '%|MEM:' || mem_used || 'MB' AS hardware_snapshot FROM cluster_telemetry WHERE 'CPU:' || cpu_idle || '%' LIKE 'CPU:10%';

-- 052. 账户余额范围动态文本生成式模糊匹配
-- NOTE: This SQL was truncated in the chat message and is preserved as received.
SELECT acc_id, 'BAL_LEVEL_' || CASE WHEN balance  '2025-01-01';

-- 053-058. Not present in the supplied text.

-- 059. 非标准 SQL 语法变体过滤兼容性测试
SELECT node_ref, 'V_REF:' || node_ref FROM infra_nodes WHERE node_ref LIKE '%\%%' ESCAPE '\' AND 'V_REF:' || node_ref NOT LIKE '%LOCALHOST%';

-- 060. 级联哈希冲突检测字符串输出
SELECT item_a_id, item_b_id, 'HASH_PAIR:' || md5(item_a_id::text) || '::' || md5(item_b_id::text) AS signature_pair FROM conflict_check_ledger WHERE 'HASH_PAIR:' || md5(item_a_id::text) LIKE '%098f6bcd%';

-- 061. 复杂的区间包含关系与字符串表达
SELECT reservation_id, 'BOOKING_PERIOD_[' || upper(duration)::text || ' - ' || lower(duration)::text || ']' FROM hotel_reservations WHERE hotel_code || '_ROOM' LIKE 'HILTON%';

-- 062. 基于逆向反转字符串 (REVERSE) 的回文与后向 LIKE 校验
SELECT word_id, original_word || '_REV_' || REVERSE(original_word) AS mirror_word FROM dictionary_labs WHERE REVERSE(original_word) LIKE 'tx_e%';

-- 063. 嵌套行类型（Row Type）解构拼接
SELECT r_id, 'COMP_ROW_STR:[' || (composite_column).field_a || ' # ' || (composite_column).field_b || ']' FROM custom_struct_table WHERE (composite_column).field_a LIKE 'SYS%';

-- 064. 权限访问控制列表（ACL）的权限集压缩拼接
SELECT resource_id, 'ACL_FLAGS:[' || STRING_AGG(DISTINCT privilege_level, '|' ORDER BY privilege_level) || ']' AS consolidated_acl FROM security_policies GROUP BY resource_id HAVING STRING_AGG(DISTINCT privilege_level, '|') LIKE '%WRITE%';

-- 065. 结合当前时间分区的临时表批次拼接标识
SELECT batch_id, 'BATCH_RUN_' || TO_CHAR(NOW(), 'YYYY_MM_DD_HH24_MI_SS') || '_ID_' || batch_id AS dynamic_batch_title FROM batch_jobs WHERE batch_status LIKE 'RUNNING%';

-- 066. 大量或门（OR）组合下的复合拼接推导
SELECT entry_id, 'LOG_COMBINED_' || category_code FROM application_registry WHERE category_code LIKE 'ERR_%' OR category_code LIKE 'CRIT_%' OR 'LOG_COMBINED_' || category_code LIKE '%_FATAL';

-- 067. 针对带有浮点数损失精度的数值强制转换为文本拼接
SELECT measurement_id, 'VAL_FLOAT_' || CAST(gamma_ray_index AS TEXT) AS continuous_sign FROM oil_well_logs WHERE CAST(gamma_ray_index AS TEXT) LIKE '0.004%';

-- 068. 物流多节点的运输路线全名称合并
SELECT ship_id, 'ORIGIN:' || origin_hub || ' -> MID:' || COALESCE(transfer_hub, 'NONE') || ' -> DEST:' || destination_hub AS full_routing_map FROM freight_manifests WHERE origin_hub LIKE 'CN%' AND destination_hub LIKE 'US%';

-- 069. 全字段的大连接（Full Outer Join）下的双向字段拼接
SELECT COALESCE(a.name, 'MISSING_A') || ' <==> ' || COALESCE(b.name, 'MISSING_B') AS link_status FROM side_a a FULL OUTER JOIN side_b b ON a.id = b.id WHERE a.name LIKE 'PRO%' OR b.name LIKE 'SUB%';

-- 070. 基于位置掩码（Bitmask）与字符串匹配的报文重组
SELECT packet_id, 'LEN:' || length_bytes || '|HEX:' || encode(packet_payload, 'hex') AS raw_hex_dump FROM network_packets WHERE encode(packet_payload, 'hex') LIKE 'deadbeef%';

-- 071. 混合多字节国际化字符集（如中文/日文）的高级拼接
SELECT prod_id, '商品编码：' || product_sku || ' ｜ 描述：' || chinese_description AS i18n_manifest FROM global_products WHERE chinese_description LIKE '%旗舰版%';

-- 072. 模拟内部配置注册表路径覆盖
SELECT reg_id, 'HKEY_LOCAL_MACHINE\SOFTWARE\\' || vendor_name || '\\' || product_name || '\Version' AS registry_path FROM software_inventory WHERE vendor_name LIKE 'Microsoft%';

-- 073. 带有特定模式的后缀剥离与二次重组拼接
SELECT email_id, 'USER_PART:' || split_part(email, '@', 1) || '|DOMAIN_PART:' || split_part(email, '@', 2) FROM mailing_list WHERE email LIKE '%@gmail.com';

-- 074. 分层树状物料编码递归前缀校验
SELECT item_id, 'LEVEL_0_CODE_' || root_code || '_CHILD_' || child_code AS matrix_code FROM manufacturing_hierarchy WHERE 'LEVEL_0_CODE_' || root_code LIKE '%_MAINFRAME_%';

-- 075. 用户会话指纹信息复合动态提取
SELECT session_hash, 'IP:' || client_ip || '|UA:' || user_agent_string AS fingerprint FROM user_http_traffic WHERE user_agent_string LIKE '%Mozilla/5.0%';
