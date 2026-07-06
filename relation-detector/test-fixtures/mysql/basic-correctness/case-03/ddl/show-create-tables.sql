-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-03.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_03.biz_bill_item_fact
CREATE TABLE `biz_bill_item_fact` (
  `factId` bigint NOT NULL AUTO_INCREMENT COMMENT '事实明细ID，语义层生成',
  `tenantId` bigint NOT NULL COMMENT '租户ID，来源: jsh_depot_head.tenant_id / jsh_depot_item.tenant_id',
  `sourceOrderId` bigint NOT NULL COMMENT '主单ID，来源: jsh_depot_head.id',
  `sourceOrderNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据号，来源: jsh_depot_head.number',
  `sourceOrderItemId` bigint NOT NULL COMMENT '明细ID，来源: jsh_depot_item.id',
  `purchaseOrderLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购入库关联采购订单号（来自入库单link_number）',
  `purchaseApplyLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购订单关联请购单号（来自采购订单link_apply）',
  `sourceType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据主类型，来源: jsh_depot_head.type（入库/出库/其它）',
  `sourceSubType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据子类型，来源: jsh_depot_head.sub_type。与sourceType联动：请购单/采购订单/销售订单/调拨通常为其它；采购/销售退货/零售退货通常为入库；采购退货/销售/零售通常为出库',
  `businessDate` datetime DEFAULT NULL COMMENT '业务时间，来源: jsh_depot_head.oper_time。按场景分别对应 requisitionOrderDate / purchaseOrderDate / purchaseInOrderDate / purchaseReturnInOrderDate / orderDate / salesOutOrderDate / salesReturnInOrderDate / transferOrderDate',
  `creator` bigint DEFAULT NULL COMMENT '制单人ID，来源: jsh_depot_head.creator，对应 jsh_user.id；storeId 可由 creator -> jsh_orga_user_rel -> jsh_organization 推导',
  `supplierId` bigint DEFAULT NULL COMMENT '供应商ID，来源: jsh_depot_head.organ_id（supplier.type=供应商，或采购链路）',
  `customerId` bigint DEFAULT NULL COMMENT '客户ID，来源: jsh_depot_head.organ_id（supplier.type=客户，或销售链路）',
  `memberId` bigint DEFAULT NULL COMMENT '会员ID，来源: jsh_depot_head.organ_id（supplier.type=会员，或零售链路）',
  `storeId` bigint DEFAULT NULL COMMENT '门店ID，来源: jsh_organization.id，经 creator -> jsh_orga_user_rel 推导',
  `productId` bigint DEFAULT NULL COMMENT '商品ID，来源: jsh_depot_item.material_id',
  `warehouseId` bigint DEFAULT NULL COMMENT '仓库ID，来源: jsh_depot_item.depot_id',
  `outWarehouseId` bigint DEFAULT NULL COMMENT '调出仓库ID，来源: jsh_depot_item.depot_id（调拨场景）',
  `inWarehouseId` bigint DEFAULT NULL COMMENT '调入仓库ID，来源: jsh_depot_item.another_depot_id（调拨场景）',
  `quantity` decimal(24,6) DEFAULT NULL COMMENT '数量，来源: jsh_depot_item.oper_number',
  `amount` decimal(24,6) DEFAULT NULL COMMENT '金额，建议口径: COALESCE(jsh_depot_item.tax_last_money, jsh_depot_item.all_price, 0)',
  `inventoryDirection` tinyint DEFAULT NULL COMMENT '库存方向（规则字段，非源表原始列）：由sourceType+sourceSubType计算。入库=1，出库=-1，其它=0',
  `salesDirection` tinyint DEFAULT NULL COMMENT '销售方向（规则字段，非源表原始列）：销售出库=1，销售退货入库=-1，其它=0',
  `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '语义层创建时间',
  `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '语义层更新时间',
  PRIMARY KEY (`factId`) USING BTREE,
  UNIQUE KEY `uk_bill_item_fact_source_item` (`tenantId`,`sourceOrderItemId`) USING BTREE,
  KEY `idx_bill_item_fact_time` (`tenantId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_type_time` (`tenantId`,`sourceType`,`sourceSubType`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_store_time` (`tenantId`,`storeId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_product_time` (`tenantId`,`productId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_warehouse_time` (`tenantId`,`warehouseId`,`businessDate`) USING BTREE,
  KEY `idx_bif_purchaseOrderLinkNo` (`tenantId`,`sourceSubType`,`purchaseOrderLinkNo`) USING BTREE,
  KEY `idx_bif_purchaseApplyLinkNo` (`tenantId`,`sourceSubType`,`purchaseApplyLinkNo`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6337883 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='单据明细事实表（高频字段极简版）';

-- relation-detector-fixture-table: case_03.biz_bill_item_fact1
CREATE TABLE `biz_bill_item_fact1` (
  `factId` bigint NOT NULL AUTO_INCREMENT COMMENT '事实明细ID，语义层生成',
  `tenantId` bigint NOT NULL COMMENT '租户ID，来源: jsh_depot_head.tenant_id / jsh_depot_item.tenant_id',
  `sourceOrderId` bigint NOT NULL COMMENT '主单ID，来源: jsh_depot_head.id',
  `sourceOrderNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据号，来源: jsh_depot_head.number',
  `sourceOrderItemId` bigint NOT NULL COMMENT '明细ID，来源: jsh_depot_item.id',
  `purchaseOrderLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购入库关联采购订单号（来自入库单link_number）',
  `purchaseApplyLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购订单关联请购单号（来自采购订单link_apply）',
  `sourceType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据主类型，来源: jsh_depot_head.type（入库/出库/其它）',
  `sourceSubType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据子类型，来源: jsh_depot_head.sub_type。与sourceType联动：请购单/采购订单/销售订单/调拨通常为其它；采购/销售退货/零售退货通常为入库；采购退货/销售/零售通常为出库',
  `businessDate` datetime DEFAULT NULL COMMENT '业务时间，来源: jsh_depot_head.oper_time。按场景分别对应 requisitionOrderDate / purchaseOrderDate / purchaseInOrderDate / purchaseReturnInOrderDate / orderDate / salesOutOrderDate / salesReturnInOrderDate / transferOrderDate',
  `creator` bigint DEFAULT NULL COMMENT '制单人ID，来源: jsh_depot_head.creator，对应 jsh_user.id；storeId 可由 creator -> jsh_orga_user_rel -> jsh_organization 推导',
  `supplierId` bigint DEFAULT NULL COMMENT '供应商ID，来源: jsh_depot_head.organ_id（supplier.type=供应商，或采购链路）',
  `customerId` bigint DEFAULT NULL COMMENT '客户ID，来源: jsh_depot_head.organ_id（supplier.type=客户，或销售链路）',
  `memberId` bigint DEFAULT NULL COMMENT '会员ID，来源: jsh_depot_head.organ_id（supplier.type=会员，或零售链路）',
  `storeId` bigint DEFAULT NULL COMMENT '门店ID，来源: jsh_organization.id，经 creator -> jsh_orga_user_rel 推导',
  `productId` bigint DEFAULT NULL COMMENT '商品ID，来源: jsh_depot_item.material_id',
  `warehouseId` bigint DEFAULT NULL COMMENT '仓库ID，来源: jsh_depot_item.depot_id',
  `outWarehouseId` bigint DEFAULT NULL COMMENT '调出仓库ID，来源: jsh_depot_item.depot_id（调拨场景）',
  `inWarehouseId` bigint DEFAULT NULL COMMENT '调入仓库ID，来源: jsh_depot_item.another_depot_id（调拨场景）',
  `quantity` decimal(24,6) DEFAULT NULL COMMENT '数量，来源: jsh_depot_item.oper_number',
  `amount` decimal(24,6) DEFAULT NULL COMMENT '金额，建议口径: COALESCE(jsh_depot_item.tax_last_money, jsh_depot_item.all_price, 0)',
  `inventoryDirection` tinyint DEFAULT NULL COMMENT '库存方向（规则字段，非源表原始列）：由sourceType+sourceSubType计算。入库=1，出库=-1，其它=0',
  `salesDirection` tinyint DEFAULT NULL COMMENT '销售方向（规则字段，非源表原始列）：销售出库=1，销售退货入库=-1，其它=0',
  `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '语义层创建时间',
  `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '语义层更新时间',
  PRIMARY KEY (`factId`) USING BTREE,
  UNIQUE KEY `uk_bill_item_fact_source_item` (`tenantId`,`sourceOrderItemId`) USING BTREE,
  KEY `idx_bill_item_fact_time` (`tenantId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_type_time` (`tenantId`,`sourceType`,`sourceSubType`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_store_time` (`tenantId`,`storeId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_product_time` (`tenantId`,`productId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_warehouse_time` (`tenantId`,`warehouseId`,`businessDate`) USING BTREE,
  KEY `idx_bif_purchaseOrderLinkNo` (`tenantId`,`sourceSubType`,`purchaseOrderLinkNo`) USING BTREE,
  KEY `idx_bif_purchaseApplyLinkNo` (`tenantId`,`sourceSubType`,`purchaseApplyLinkNo`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4391220 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='单据明细事实表（高频字段极简版）';

-- relation-detector-fixture-table: case_03.biz_bill_item_fact_new
CREATE TABLE `biz_bill_item_fact_new` (
  `factId` bigint NOT NULL AUTO_INCREMENT COMMENT '事实明细ID，语义层生成',
  `tenantId` bigint NOT NULL COMMENT '租户ID，来源: jsh_depot_head.tenant_id / jsh_depot_item.tenant_id',
  `sourceOrderId` bigint NOT NULL COMMENT '主单ID，来源: jsh_depot_head.id',
  `sourceOrderNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据号，来源: jsh_depot_head.number',
  `sourceOrderItemId` bigint NOT NULL COMMENT '明细ID，来源: jsh_depot_item.id',
  `purchaseOrderLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购入库关联采购订单号（来自入库单link_number）',
  `purchaseApplyLinkNo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '采购订单关联请购单号（来自采购订单link_apply）',
  `sourceType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据主类型，来源: jsh_depot_head.type（入库/出库/其它）',
  `sourceSubType` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单据子类型，来源: jsh_depot_head.sub_type。与sourceType联动：请购单/采购订单/销售订单/调拨通常为其它；采购/销售退货/零售退货通常为入库；采购退货/销售/零售通常为出库',
  `businessDate` datetime DEFAULT NULL COMMENT '业务时间，来源: jsh_depot_head.oper_time。按场景分别对应 requisitionOrderDate / purchaseOrderDate / purchaseInOrderDate / purchaseReturnInOrderDate / orderDate / salesOutOrderDate / salesReturnInOrderDate / transferOrderDate',
  `creator` bigint DEFAULT NULL COMMENT '制单人ID，来源: jsh_depot_head.creator，对应 jsh_user.id；storeId 可由 creator -> jsh_orga_user_rel -> jsh_organization 推导',
  `supplierId` bigint DEFAULT NULL COMMENT '供应商ID，来源: jsh_depot_head.organ_id（supplier.type=供应商，或采购链路）',
  `customerId` bigint DEFAULT NULL COMMENT '客户ID，来源: jsh_depot_head.organ_id（supplier.type=客户，或销售链路）',
  `memberId` bigint DEFAULT NULL COMMENT '会员ID，来源: jsh_depot_head.organ_id（supplier.type=会员，或零售链路）',
  `storeId` bigint DEFAULT NULL COMMENT '门店ID，来源: jsh_organization.id，经 creator -> jsh_orga_user_rel 推导',
  `productId` bigint DEFAULT NULL COMMENT '商品ID，来源: jsh_depot_item.material_id',
  `warehouseId` bigint DEFAULT NULL COMMENT '仓库ID，来源: jsh_depot_item.depot_id',
  `outWarehouseId` bigint DEFAULT NULL COMMENT '调出仓库ID，来源: jsh_depot_item.depot_id（调拨场景）',
  `inWarehouseId` bigint DEFAULT NULL COMMENT '调入仓库ID，来源: jsh_depot_item.another_depot_id（调拨场景）',
  `quantity` decimal(24,6) DEFAULT NULL COMMENT '数量，来源: jsh_depot_item.oper_number',
  `amount` decimal(24,6) DEFAULT NULL COMMENT '金额，建议口径: COALESCE(jsh_depot_item.tax_last_money, jsh_depot_item.all_price, 0)',
  `inventoryDirection` tinyint DEFAULT NULL COMMENT '库存方向（规则字段，非源表原始列）：由sourceType+sourceSubType计算。入库=1，出库=-1，其它=0',
  `salesDirection` tinyint DEFAULT NULL COMMENT '销售方向（规则字段，非源表原始列）：销售出库=1，销售退货入库=-1，其它=0',
  `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '语义层创建时间',
  `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '语义层更新时间',
  PRIMARY KEY (`factId`) USING BTREE,
  UNIQUE KEY `uk_bill_item_fact_source_item` (`tenantId`,`sourceOrderItemId`) USING BTREE,
  UNIQUE KEY `uk_tenant_item` (`tenantId`,`sourceOrderItemId`) USING BTREE,
  KEY `idx_bill_item_fact_time` (`tenantId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_type_time` (`tenantId`,`sourceType`,`sourceSubType`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_store_time` (`tenantId`,`storeId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_product_time` (`tenantId`,`productId`,`businessDate`) USING BTREE,
  KEY `idx_bill_item_fact_warehouse_time` (`tenantId`,`warehouseId`,`businessDate`) USING BTREE,
  KEY `idx_bif_purchaseOrderLinkNo` (`tenantId`,`sourceSubType`,`purchaseOrderLinkNo`) USING BTREE,
  KEY `idx_bif_purchaseApplyLinkNo` (`tenantId`,`sourceSubType`,`purchaseApplyLinkNo`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9189461 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='单据明细事实表（高频字段极简版）';

-- relation-detector-fixture-table: case_03.biz_sync_progress
CREATE TABLE `biz_sync_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `jobId` bigint NOT NULL,
  `tenantId` bigint DEFAULT NULL,
  `batchFromId` bigint DEFAULT NULL,
  `batchToId` bigint DEFAULT NULL,
  `affectedRows` int DEFAULT NULL,
  `doneRows` bigint DEFAULT NULL,
  `totalRows` bigint DEFAULT NULL,
  `pct` decimal(8,2) DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_job_time` (`jobId`,`createdAt`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4527 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.chat_message
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL COMMENT '消息ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色: user/assistant',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '消息内容',
  `result_data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '查询结果JSON',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'done' COMMENT '状态: thinking/querying/done/error',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_session_id` (`session_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='消息表';

-- relation-detector-fixture-table: case_03.chat_session
CREATE TABLE `chat_session` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_updated_at` (`updated_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='会话表';

-- relation-detector-fixture-table: case_03.fe_agent_message
CREATE TABLE `fe_agent_message` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
  `sessionId` int NOT NULL COMMENT '关联会话 ID',
  `role` enum('user','assistant','system') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `metadata` json DEFAULT NULL COMMENT '消息元数据',
  `createdAt` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `IDX_fe_agent_message_sessionId` (`sessionId`) USING BTREE,
  CONSTRAINT `FK_fe_agent_message_session` FOREIGN KEY (`sessionId`) REFERENCES `fe_agent_session` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 消息表';

-- relation-detector-fixture-table: case_03.fe_agent_session
CREATE TABLE `fe_agent_session` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
  `sessionId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话唯一标识符',
  `userId` int DEFAULT NULL COMMENT '用户 ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `status` enum('active','archived','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '会话状态',
  `metadata` json DEFAULT NULL COMMENT '会话元数据',
  `createdAt` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updatedAt` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `UQ_fe_agent_session_sessionId` (`sessionId`) USING BTREE,
  KEY `IDX_fe_agent_session_userId` (`userId`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=166 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='Agent 会话表';

-- relation-detector-fixture-table: case_03.fe_typeorm_migrations
CREATE TABLE `fe_typeorm_migrations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `timestamp` bigint NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.graph_checkpoint
CREATE TABLE `graph_checkpoint` (
  `checkpoint_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `thread_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `node_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `next_node_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `state_data` json NOT NULL,
  `saved_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`checkpoint_id`) USING BTREE,
  KEY `GRAPH_FK_THREAD` (`thread_id`) USING BTREE,
  CONSTRAINT `GRAPH_FK_THREAD` FOREIGN KEY (`thread_id`) REFERENCES `graph_thread` (`thread_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.graph_thread
CREATE TABLE `graph_thread` (
  `thread_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `thread_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `is_released` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`thread_id`) USING BTREE,
  UNIQUE KEY `IDX_GRAPH_THREAD_NAME_RELEASED` (`thread_name`,`is_released`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.jsh_account
CREATE TABLE `jsh_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `serial_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',
  `initial_amount` decimal(24,6) DEFAULT NULL COMMENT '期初金额',
  `current_amount` decimal(24,6) DEFAULT NULL COMMENT '当前余额',
  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `is_default` bit(1) DEFAULT NULL COMMENT '是否默认',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='账户信息';

-- relation-detector-fixture-table: case_03.jsh_account_head
CREATE TABLE `jsh_account_head` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型(支出/收入/收款/付款/转账)',
  `organ_id` bigint DEFAULT NULL COMMENT '单位Id(收款/付款单位)',
  `hands_person_id` bigint DEFAULT NULL COMMENT '经手人id',
  `creator` bigint DEFAULT NULL COMMENT '操作员',
  `change_amount` decimal(24,6) DEFAULT NULL COMMENT '变动金额(优惠/收款/付款/实付)',
  `discount_money` decimal(24,6) DEFAULT NULL COMMENT '优惠金额',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '合计金额',
  `account_id` bigint DEFAULT NULL COMMENT '账户(收款/付款)',
  `bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据编号',
  `bill_time` datetime DEFAULT NULL COMMENT '单据日期',
  `remark` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `file_name` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '附件名称',
  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、9审核中',
  `source` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '单据来源，0-pc，1-手机',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK9F4C0D8DB610FC06` (`organ_id`) USING BTREE,
  KEY `FK9F4C0D8DAAE50527` (`account_id`) USING BTREE,
  KEY `FK9F4C0D8DC4170B37` (`hands_person_id`) USING BTREE,
  KEY `bill_no` (`bill_no`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=132 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='财务主表';

-- relation-detector-fixture-table: case_03.jsh_account_item
CREATE TABLE `jsh_account_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `header_id` bigint NOT NULL COMMENT '表头Id',
  `account_id` bigint DEFAULT NULL COMMENT '账户Id',
  `in_out_item_id` bigint DEFAULT NULL COMMENT '收支项目Id',
  `bill_id` bigint DEFAULT NULL COMMENT '单据id',
  `need_debt` decimal(24,6) DEFAULT NULL COMMENT '应收欠款',
  `finish_debt` decimal(24,6) DEFAULT NULL COMMENT '已收欠款',
  `each_amount` decimal(24,6) DEFAULT NULL COMMENT '单项金额',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK9F4CBAC0AAE50527` (`account_id`) USING BTREE,
  KEY `FK9F4CBAC0C5FE6007` (`header_id`) USING BTREE,
  KEY `FK9F4CBAC0D203EDC5` (`in_out_item_id`) USING BTREE,
  KEY `bill_id` (`bill_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=158 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='财务子表';

-- relation-detector-fixture-table: case_03.jsh_depot
CREATE TABLE `jsh_depot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓库名称',
  `address` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓库地址',
  `warehousing` decimal(24,6) DEFAULT NULL COMMENT '仓储费',
  `truckage` decimal(24,6) DEFAULT NULL COMMENT '搬运费',
  `type` int DEFAULT NULL COMMENT '类型',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '描述',
  `principal` bigint DEFAULT NULL COMMENT '负责人',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  `is_default` bit(1) DEFAULT NULL COMMENT '是否默认',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=132 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='仓库表';

-- relation-detector-fixture-table: case_03.jsh_depot_head
CREATE TABLE `jsh_depot_head` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型(出库/入库)',
  `sub_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '出入库分类',
  `default_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '初始票据号',
  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '票据号',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `oper_time` datetime DEFAULT NULL COMMENT '出入库时间',
  `organ_id` bigint DEFAULT NULL COMMENT '供应商id',
  `creator` bigint DEFAULT NULL COMMENT '操作员',
  `account_id` bigint DEFAULT NULL COMMENT '账户id',
  `change_amount` decimal(24,6) DEFAULT NULL COMMENT '变动金额(收款/付款)',
  `back_amount` decimal(24,6) DEFAULT NULL COMMENT '找零金额',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '合计金额',
  `pay_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '付款类型(现金、记账等)',
  `bill_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据类型',
  `remark` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `file_name` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '附件名称',
  `sales_man` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '销售员（可以多个）',
  `account_id_list` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户ID列表',
  `account_money_list` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户金额列表',
  `discount` decimal(24,6) DEFAULT NULL COMMENT '优惠率',
  `discount_money` decimal(24,6) DEFAULT NULL COMMENT '优惠金额',
  `discount_last_money` decimal(24,6) DEFAULT NULL COMMENT '优惠后金额',
  `other_money` decimal(24,6) DEFAULT NULL COMMENT '销售或采购费用合计',
  `deposit` decimal(24,6) DEFAULT NULL COMMENT '订金',
  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、2完成采购|销售、3部分采购|销售、9审核中',
  `purchase_status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '采购状态，0未采购、2完成采购、3部分采购',
  `source` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '单据来源，0-pc，1-手机',
  `link_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联订单号',
  `link_apply` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联请购单',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK2A80F214B610FC06` (`organ_id`) USING BTREE,
  KEY `FK2A80F214AAE50527` (`account_id`) USING BTREE,
  KEY `number` (`number`) USING BTREE,
  KEY `link_number` (`link_number`) USING BTREE,
  KEY `creator` (`creator`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE,
  KEY `idx_tenant_subtype_status_id` (`tenant_id`,`sub_type`,`status`,`delete_flag`,`id` DESC) USING BTREE,
  KEY `idx_tenant_type_subtype_id` (`tenant_id`,`type`,`sub_type`,`id` DESC,`number` DESC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4173019 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='单据主表';

-- relation-detector-fixture-table: case_03.jsh_depot_item
CREATE TABLE `jsh_depot_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `header_id` bigint NOT NULL COMMENT '表头Id',
  `material_id` bigint NOT NULL COMMENT '商品Id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '商品扩展id',
  `material_unit` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品单位',
  `sku` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性',
  `oper_number` decimal(24,6) DEFAULT NULL COMMENT '数量',
  `basic_number` decimal(24,6) DEFAULT NULL COMMENT '基础数量，如kg、瓶',
  `unit_price` decimal(24,6) DEFAULT NULL COMMENT '单价',
  `purchase_unit_price` decimal(24,6) DEFAULT NULL COMMENT '采购单价',
  `tax_unit_price` decimal(24,6) DEFAULT NULL COMMENT '含税单价',
  `all_price` decimal(24,6) DEFAULT NULL COMMENT '金额',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `another_depot_id` bigint DEFAULT NULL COMMENT '调拨时，对方仓库Id',
  `tax_rate` decimal(24,6) DEFAULT NULL COMMENT '税率',
  `tax_money` decimal(24,6) DEFAULT NULL COMMENT '税额',
  `tax_last_money` decimal(24,6) DEFAULT NULL COMMENT '价税合计',
  `material_type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品类型',
  `sn_list` varchar(2000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '序列号列表',
  `batch_number` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '批号',
  `expiration_date` datetime DEFAULT NULL COMMENT '有效日期',
  `link_id` bigint DEFAULT NULL COMMENT '关联明细id',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK2A819F475D61CCF7` (`material_id`) USING BTREE,
  KEY `FK2A819F474BB6190E` (`header_id`) USING BTREE,
  KEY `FK2A819F479485B3F5` (`depot_id`) USING BTREE,
  KEY `FK2A819F47729F5392` (`another_depot_id`) USING BTREE,
  KEY `material_extend_id` (`material_extend_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11724414 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='单据子表';

-- relation-detector-fixture-table: case_03.jsh_function
CREATE TABLE `jsh_function` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `parent_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '上级编号',
  `url` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '链接',
  `component` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '组件',
  `state` bit(1) DEFAULT NULL COMMENT '收缩',
  `sort` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `push_btn` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '功能按钮',
  `icon` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '图标',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `url` (`url`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=262 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='功能模块表';

-- relation-detector-fixture-table: case_03.jsh_in_out_item
CREATE TABLE `jsh_in_out_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='收支项目';

-- relation-detector-fixture-table: case_03.jsh_location_gps
CREATE TABLE `jsh_location_gps` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `link_id` bigint NOT NULL COMMENT '关联业务ID',
  `link_type` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '类型: SUPPLIER, STORE, HEADQUARTER, REGION, CITY, DISTRICT, DEPOT',
  `val_supplier_id` bigint GENERATED ALWAYS AS (if((`link_type` = _utf8mb3'SUPPLIER'),`link_id`,NULL)) VIRTUAL,
  `val_org_id` bigint GENERATED ALWAYS AS (if((`link_type` in (_utf8mb3'STORE',_utf8mb3'HEADQUARTER',_utf8mb3'REGION',_utf8mb3'CITY',_utf8mb3'DISTRICT')),`link_id`,NULL)) VIRTUAL,
  `val_depot_id` bigint GENERATED ALWAYS AS (if((`link_type` = _utf8mb3'DEPOT'),`link_id`,NULL)) VIRTUAL,
  `link_code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编码',
  `link_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `province` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '省份',
  `city` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '城市',
  `district` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '行政区',
  `address` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '详细地址',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_supplier_only` (`val_supplier_id`) USING BTREE,
  UNIQUE KEY `uk_org_only` (`val_org_id`) USING BTREE,
  UNIQUE KEY `uk_depot_only` (`val_depot_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=306 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.jsh_log
CREATE TABLE `jsh_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '用户id',
  `operation` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '操作模块名称',
  `client_ip` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '客户端IP',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `status` tinyint DEFAULT NULL COMMENT '操作状态 0==成功，1==失败',
  `content` varchar(5000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '详情',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FKF2696AA13E226853` (`user_id`) USING BTREE,
  KEY `create_time` (`create_time`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='操作日志';

-- relation-detector-fixture-table: case_03.jsh_material
CREATE TABLE `jsh_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint DEFAULT NULL COMMENT '产品类型id',
  `name` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `mfrs` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '制造商',
  `model` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '型号',
  `standard` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '规格',
  `brand` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '品牌',
  `mnemonic` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '助记码',
  `color` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '颜色',
  `unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单位-单个',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `img_name` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '图片名称',
  `unit_id` bigint DEFAULT NULL COMMENT '单位Id',
  `expiry_num` int DEFAULT NULL COMMENT '保质期天数',
  `weight` decimal(24,6) DEFAULT NULL COMMENT '基础重量(kg)',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用 0-禁用  1-启用',
  `other_field1` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义1',
  `other_field2` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义2',
  `other_field3` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义3',
  `enable_serial_number` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否开启序列号，0否，1是',
  `enable_batch_number` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否开启批号，0否，1是',
  `position` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓位货架',
  `attribute` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性信息',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK675951272AB6672C` (`category_id`) USING BTREE,
  KEY `UnitId` (`unit_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4120 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品表';

-- relation-detector-fixture-table: case_03.jsh_material_attribute
CREATE TABLE `jsh_material_attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attribute_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '属性名',
  `attribute_value` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '属性值',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品属性表';

-- relation-detector-fixture-table: case_03.jsh_material_category
CREATE TABLE `jsh_material_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `category_level` smallint DEFAULT NULL COMMENT '等级',
  `parent_id` bigint DEFAULT NULL COMMENT '上级id',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '显示顺序',
  `serial_no` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',
  `remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `FK3EE7F725237A77D8` (`parent_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品类型表';

-- relation-detector-fixture-table: case_03.jsh_material_current_stock
CREATE TABLE `jsh_material_current_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint DEFAULT NULL COMMENT '产品id',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `current_number` decimal(24,6) DEFAULT NULL COMMENT '当前库存数量',
  `current_unit_price` decimal(24,6) DEFAULT NULL COMMENT '当前单价',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `material_id` (`material_id`) USING BTREE,
  KEY `depot_id` (`depot_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=131852 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品当前库存';

-- relation-detector-fixture-table: case_03.jsh_material_extend
CREATE TABLE `jsh_material_extend` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint DEFAULT NULL COMMENT '商品id',
  `bar_code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品条码',
  `commodity_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品单位',
  `sku` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性',
  `purchase_decimal` decimal(24,6) DEFAULT NULL COMMENT '采购价格',
  `commodity_decimal` decimal(24,6) DEFAULT NULL COMMENT '零售价格',
  `wholesale_decimal` decimal(24,6) DEFAULT NULL COMMENT '销售价格',
  `low_decimal` decimal(24,6) DEFAULT NULL COMMENT '最低售价',
  `default_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '1' COMMENT '是否为默认单位，1是，0否',
  `create_time` datetime DEFAULT NULL COMMENT '创建日期',
  `create_serial` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '创建人编码',
  `update_serial` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人编码',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间戳',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `material_id` (`material_id`) USING BTREE,
  KEY `bar_code` (`bar_code`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3564 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品价格扩展';

-- relation-detector-fixture-table: case_03.jsh_material_initial_stock
CREATE TABLE `jsh_material_initial_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint DEFAULT NULL COMMENT '产品id',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `number` decimal(24,6) DEFAULT NULL COMMENT '初始库存数量',
  `low_safe_stock` decimal(24,6) DEFAULT NULL COMMENT '最低库存数量',
  `high_safe_stock` decimal(24,6) DEFAULT NULL COMMENT '最高库存数量',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `material_id` (`material_id`) USING BTREE,
  KEY `depot_id` (`depot_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=131818 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品初始库存';

-- relation-detector-fixture-table: case_03.jsh_material_property
CREATE TABLE `jsh_material_property` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `native_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '原始名称',
  `enabled` bit(1) DEFAULT NULL COMMENT '是否启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `another_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '别名',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品扩展字段表';

-- relation-detector-fixture-table: case_03.jsh_msg
CREATE TABLE `jsh_msg` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `msg_title` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息标题',
  `msg_content` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息内容',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息类型',
  `user_id` bigint DEFAULT NULL COMMENT '接收人id',
  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，1未读 2已读',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='消息表';

-- relation-detector-fixture-table: case_03.jsh_orga_user_rel
CREATE TABLE `jsh_orga_user_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `orga_id` bigint DEFAULT NULL COMMENT '机构id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_blng_orga_dspl_seq` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '用户在所属机构中显示顺序',
  `delete_flag` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `creator` bigint DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `updater` bigint DEFAULT NULL COMMENT '更新人',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `orga_id` (`orga_id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `creator` (`creator`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=134 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='机构用户关系表';

-- relation-detector-fixture-table: case_03.jsh_organization
CREATE TABLE `jsh_organization` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_no` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构编号',
  `org_abr` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构简称',
  `parent_id` bigint DEFAULT NULL COMMENT '父机构id',
  `sort` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构显示顺序',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=222 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='机构表';

-- relation-detector-fixture-table: case_03.jsh_person
CREATE TABLE `jsh_person` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '姓名',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='经手人表';

-- relation-detector-fixture-table: case_03.jsh_platform_config
CREATE TABLE `jsh_platform_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform_key` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关键词',
  `platform_key_info` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关键词名称',
  `platform_value` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '值',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='平台参数';

-- relation-detector-fixture-table: case_03.jsh_role
CREATE TABLE `jsh_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `price_limit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '价格屏蔽 1-屏蔽采购价 2-屏蔽零售价 3-屏蔽销售价',
  `value` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '值',
  `description` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '描述',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='角色表';

-- relation-detector-fixture-table: case_03.jsh_sequence
CREATE TABLE `jsh_sequence` (
  `seq_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '序列名称',
  `min_value` bigint NOT NULL COMMENT '最小值',
  `max_value` bigint NOT NULL COMMENT '最大值',
  `current_val` bigint NOT NULL COMMENT '当前值',
  `increment_val` int NOT NULL DEFAULT '1' COMMENT '增长步数',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`seq_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='单据编号表';

-- relation-detector-fixture-table: case_03.jsh_serial_number
CREATE TABLE `jsh_serial_number` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint DEFAULT NULL COMMENT '产品表id',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `serial_number` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '序列号',
  `is_sell` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否卖出，0未卖出，1卖出',
  `in_price` decimal(24,6) DEFAULT NULL COMMENT '入库单价',
  `remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `creator` bigint DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `updater` bigint DEFAULT NULL COMMENT '更新人',
  `in_bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '入库单号',
  `out_bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '出库单号',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `material_id` (`material_id`) USING BTREE,
  KEY `depot_id` (`depot_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='序列号表';

-- relation-detector-fixture-table: case_03.jsh_supplier
CREATE TABLE `jsh_supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '供应商名称',
  `contacts` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '联系人',
  `phone_num` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '电子邮箱',
  `description` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `isystem` tinyint DEFAULT NULL COMMENT '是否系统自带 0==系统 1==非系统',
  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `advance_in` decimal(24,6) DEFAULT '0.000000' COMMENT '预收款',
  `begin_need_get` decimal(24,6) DEFAULT NULL COMMENT '期初应收',
  `begin_need_pay` decimal(24,6) DEFAULT NULL COMMENT '期初应付',
  `all_need_get` decimal(24,6) DEFAULT NULL COMMENT '累计应收',
  `all_need_pay` decimal(24,6) DEFAULT NULL COMMENT '累计应付',
  `fax` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '传真',
  `telephone` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '手机',
  `address` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '地址',
  `tax_num` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '纳税人识别号',
  `bank_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '开户行',
  `account_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '账号',
  `tax_rate` decimal(24,6) DEFAULT NULL COMMENT '税率',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `creator` bigint DEFAULT NULL COMMENT '操作员',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `type` (`type`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='供应商/客户信息表';

-- relation-detector-fixture-table: case_03.jsh_sys_dict_data
CREATE TABLE `jsh_sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=778 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典数据表';

-- relation-detector-fixture-table: case_03.jsh_sys_dict_type
CREATE TABLE `jsh_sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE KEY `dict_type` (`dict_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=207 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典类型表';

-- relation-detector-fixture-table: case_03.jsh_system_config
CREATE TABLE `jsh_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司名称',
  `company_contacts` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司联系人',
  `company_address` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司地址',
  `company_tel` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司电话',
  `company_fax` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司传真',
  `company_post_code` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司邮编',
  `sale_agreement` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '销售协议',
  `depot_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '仓库启用标记，0未启用，1启用',
  `customer_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '客户启用标记，0未启用，1启用',
  `minus_stock_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '负库存启用标记，0未启用，1启用',
  `purchase_by_sale_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '以销定购启用标记，0未启用，1启用',
  `multi_level_approval_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '多级审核启用标记，0未启用，1启用',
  `multi_bill_type` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '流程类型，可多选',
  `force_approval_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '强审核启用标记，0未启用，1启用',
  `update_unit_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '1' COMMENT '更新单价启用标记，0未启用，1启用',
  `over_link_bill_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '超出关联单据启用标记，0未启用，1启用',
  `in_out_manage_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '出入库管理启用标记，0未启用，1启用',
  `multi_account_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '多账户启用标记，0未启用，1启用',
  `move_avg_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '移动平均价启用标记，0未启用，1启用',
  `audit_print_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '先审核后打印启用标记，0未启用，1启用',
  `zero_change_amount_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '零收付款启用标记，0未启用，1启用',
  `customer_static_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '客户静态单价启用标记，0未启用，1启用',
  `material_price_tax_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '商品价格含税启用标记，0未启用，1启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='系统参数';

-- relation-detector-fixture-table: case_03.jsh_temp_category_affinity
CREATE TABLE `jsh_temp_category_affinity` (
  `id` int NOT NULL AUTO_INCREMENT,
  `source_cat_id` int DEFAULT NULL COMMENT '触发品类(A)',
  `target_cat_id` int DEFAULT NULL COMMENT '关联品类(B)',
  `link_prob` decimal(4,3) DEFAULT NULL COMMENT '触发概率',
  `min_ratio` decimal(4,2) DEFAULT NULL COMMENT '数量最小比例',
  `max_ratio` decimal(4,2) DEFAULT NULL COMMENT '数量最大比例',
  `rule_desc` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '规则描述',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.jsh_temp_category_pdf
CREATE TABLE `jsh_temp_category_pdf` (
  `id` bigint NOT NULL COMMENT '品类ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '品类名称',
  `weight` int DEFAULT '10' COMMENT '采样权重',
  `cdf_end` decimal(10,4) DEFAULT NULL COMMENT '累积分布终点',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.jsh_temp_day_pdf
CREATE TABLE `jsh_temp_day_pdf` (
  `day_date` date NOT NULL COMMENT '日期 YYYY-MM-DD',
  `weight` int DEFAULT '100' COMMENT '基础权重(默认100)',
  `is_holiday` tinyint DEFAULT '0' COMMENT '是否法定节假日',
  `remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '节日名称',
  PRIMARY KEY (`day_date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='模拟器-全动态年度日历权重表';

-- relation-detector-fixture-table: case_03.jsh_temp_hour_pdf
CREATE TABLE `jsh_temp_hour_pdf` (
  `hour_val` int NOT NULL,
  `weight` int DEFAULT '10',
  `remark` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`hour_val`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.jsh_temp_mock_batch_results
CREATE TABLE `jsh_temp_mock_batch_results` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '流水号唯一主键',
  `tenant_id` bigint NOT NULL COMMENT '租户隔离ID',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行状态: SUCCESS / ERROR',
  `order_id` bigint DEFAULT NULL COMMENT '生成的真实零售出库单ID',
  `bill_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成的真实单据编号(LSCK...)',
  `mock_time` datetime DEFAULT NULL COMMENT '单据落地的绝对历史模拟时钟',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '触发开单的操作员姓名',
  `item_count` int DEFAULT '0' COMMENT '单据内包含的货品行丰富度',
  `total_amount` decimal(24,6) DEFAULT '0.000000' COMMENT '单据最终价税合计总金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '核心开单引擎回传的断言日志描述',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_month` (`mock_time`) USING BTREE COMMENT '用于最终大盘按月、按年进行财务报表聚合计算的性能优化索引'
) ENGINE=InnoDB AUTO_INCREMENT=189 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模拟器-全自动计划消耗回执物理流水表(千万级容量底池)';

-- relation-detector-fixture-table: case_03.jsh_temp_mock_plan
CREATE TABLE `jsh_temp_mock_plan` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '计划行唯一流水号',
  `user_id` bigint NOT NULL COMMENT '模拟抽取到的实际开单操作员 ID (对应 jsh_user 表 id 关联字段)',
  `mock_timestamp_str` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模拟开单绝对时间戳串 (格式: YYYY-MM-DD HH:ii:ss)',
  `bill_no_str` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '一阶段预分配并完全固化落地好的 LSCK 单号 (彻底避免大盘重置回卷重号)',
  `execute_status` tinyint DEFAULT '0' COMMENT '执行消耗状态控制：0-待执行，1-已成功开单并落地，2-因熔断失败',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mock_time` (`mock_timestamp_str`) USING BTREE COMMENT '用于第二阶段流水批处理时按时间轴线性读取的索引优化'
) ENGINE=InnoDB AUTO_INCREMENT=1000039081 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='模拟器-全年度开单计划流水表(全局固化单号防重版)';

-- relation-detector-fixture-table: case_03.jsh_temp_org_pdf
CREATE TABLE `jsh_temp_org_pdf` (
  `org_id` bigint NOT NULL COMMENT '对应 jsh_organization 的 id',
  `weight` int DEFAULT '10' COMMENT '门店权重',
  `cdf_end` decimal(10,4) DEFAULT NULL COMMENT '累积概率分布终点 (0-1)',
  `remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '门店简称备注',
  PRIMARY KEY (`org_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='模拟器-机构概率分布配置表';

-- relation-detector-fixture-table: case_03.jsh_tenant
CREATE TABLE `jsh_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '用户id',
  `login_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登录名',
  `user_num_limit` int DEFAULT NULL COMMENT '用户数量限制',
  `type` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '租户类型，0免费租户，1付费租户',
  `enabled` bit(1) DEFAULT b'1' COMMENT '启用 0-禁用  1-启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `create_time` (`create_time`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='租户';

-- relation-detector-fixture-table: case_03.jsh_unit
CREATE TABLE `jsh_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称，支持多单位',
  `basic_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '基础单位',
  `other_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位',
  `other_unit_two` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位2',
  `other_unit_three` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位3',
  `ratio` decimal(24,3) DEFAULT NULL COMMENT '比例',
  `ratio_two` decimal(24,3) DEFAULT NULL COMMENT '比例2',
  `ratio_three` decimal(24,3) DEFAULT NULL COMMENT '比例3',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='多单位表';

-- relation-detector-fixture-table: case_03.jsh_user
CREATE TABLE `jsh_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户姓名--例如张三',
  `login_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '登录用户名',
  `password` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登陆密码',
  `leader_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否经理，0否，1是',
  `position` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '职位',
  `department` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '所属部门',
  `email` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '电子邮箱',
  `phonenum` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '手机号码',
  `ismanager` tinyint NOT NULL DEFAULT '1' COMMENT '是否为管理者 0==管理者 1==员工',
  `isystem` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统自带数据 ',
  `status` tinyint DEFAULT '0' COMMENT '状态，0正常，2封禁',
  `description` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '用户描述信息',
  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `weixin_open_id` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '微信绑定',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=266 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='用户表';

-- relation-detector-fixture-table: case_03.jsh_user_business
CREATE TABLE `jsh_user_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类别',
  `key_id` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '主id',
  `value` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci COMMENT '值',
  `btn_str` varchar(20000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '按钮权限',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `type` (`type`) USING BTREE,
  KEY `key_id` (`key_id`) USING BTREE,
  KEY `tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=332 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='用户/角色/模块关系表';

-- relation-detector-fixture-table: case_03.log_bill_sync_detail
CREATE TABLE `log_bill_sync_detail` (
  `logId` bigint NOT NULL AUTO_INCREMENT,
  `syncBatchId` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `tenantId` bigint NOT NULL,
  `sourceOrderId` bigint NOT NULL,
  `sourceOrderItemId` bigint NOT NULL,
  `executeTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `syncStatus` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `errorMsg` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`logId`) USING BTREE,
  KEY `idx_sync_batch` (`syncBatchId`) USING BTREE,
  KEY `idx_source_item` (`tenantId`,`sourceOrderItemId`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2686654 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='单据明细同步日志中间表';

-- relation-detector-fixture-table: case_03.ontology
CREATE TABLE `ontology` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ontology_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `business_definition` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `ontology_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'entity',
  `physical_table` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `business_domain` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `enabled` int NOT NULL DEFAULT '1',
  `business_definition_bak` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `IDX_aa733b95416a2be7d0dce878b8` (`ontology_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_metric
CREATE TABLE `ontology_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `metric_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `show_style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'table',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `input_params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `output_fields_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `default_order_by` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `default_order_dir` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'desc',
  `default_limit` int NOT NULL DEFAULT '5',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `input_dimensions` json DEFAULT NULL,
  `output_dimensions` json DEFAULT NULL,
  `enabled` int NOT NULL DEFAULT '1',
  `metric_sql` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'metric sql template',
  `description_bak` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `metric_sql1` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'optimized metric sql v1',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `IDX_15e15406175086eaaf5769117f` (`metric_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_metric_bak
CREATE TABLE `ontology_metric_bak` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `metric_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `show_style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'table',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `description1` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'AI选指标描述',
  `input_params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `output_fields_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `default_order_by` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `default_order_dir` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'desc',
  `default_limit` int DEFAULT '5',
  `sort_order` int DEFAULT '0',
  `enabled` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_metric_code` (`metric_code`) USING BTREE,
  KEY `idx_ontology_code` (`ontology_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_metric_bak_ceil_20260525
CREATE TABLE `ontology_metric_bak_ceil_20260525` (
  `id` bigint NOT NULL DEFAULT '0',
  `metric_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `metric_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `show_style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'table',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `input_params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `output_fields_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `default_order_by` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `default_order_dir` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'desc',
  `default_limit` int NOT NULL DEFAULT '5',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `input_dimensions` json DEFAULT NULL,
  `output_dimensions` json DEFAULT NULL,
  `enabled` int NOT NULL DEFAULT '1',
  `metric_sql` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'metric sql template'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_property
CREATE TABLE `ontology_property` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `property_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `property_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `is_primary_key` int NOT NULL DEFAULT '0',
  `enabled` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_relation
CREATE TABLE `ontology_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `relation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `source_ontology` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_ontology` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `cardinality` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `mapping_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `source_primary_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `unique_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `enabled` int NOT NULL DEFAULT '1',
  `description_bak` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `IDX_e490fb346e1a9921337c87a381` (`unique_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.ontology_rule
CREATE TABLE `ontology_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本体code',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
  `rule_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规则唯一编码',
  `rule_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规则类型：query查询性/action动作性/mix混合',
  `scene_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '业务场景：replenish补货/audit审核/return退货',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规则整体描述',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `enabled` tinyint DEFAULT '1' COMMENT '启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='规则描述';

-- relation-detector-fixture-table: case_03.ontology_rule_1
CREATE TABLE `ontology_rule_1` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ontology_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本体code',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '规则描述',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `enabled` tinyint DEFAULT '1' COMMENT '启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='规则描述';

-- relation-detector-fixture-table: case_03.ontology_rule_item
CREATE TABLE `ontology_rule_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则编码，如 restocking_timely_first, restocking_cost_first 等',
  `item_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则项编码',
  `item_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则项名称',
  `item_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'condition条件/formula公式/action动作/constraint约束',
  `item_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '规则项内容',
  `executable_logic` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'LLM可执行逻辑表达式',
  `priority` int DEFAULT '0' COMMENT '规则优先级，数值越小优先级越高（用于冲突解决）',
  `sort_order` int DEFAULT '0' COMMENT '类别内排序',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_item_code` (`item_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='经营决策规则条目表';

-- relation-detector-fixture-table: case_03.ontology_rule_item_1
CREATE TABLE `ontology_rule_item_1` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '瑙勫垯缂栫爜锛屽? restocking_timely_first, restocking_cost_first 绛',
  `rule_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '瑙勫垯鍚嶇О锛屽? 鏃舵晥浼樺厛鍘熷垯銆佹垚鏈?紭鍏堝師鍒',
  `rule_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '瑙勫垯鍏蜂綋鍐呭?鎻忚堪',
  `priority` int DEFAULT '0' COMMENT '瑙勫垯浼樺厛绾э紝鏁板?瓒婂皬浼樺厛绾ц秺楂橈紙鐢ㄤ簬鍐茬獊瑙ｅ喅锛',
  `sort_order` int DEFAULT '0' COMMENT '绫诲埆鍐呮帓搴',
  `enabled` tinyint DEFAULT '1' COMMENT '鏄?惁鍚?敤',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='规则描述';

-- relation-detector-fixture-table: case_03.slx_daily_counter
CREATE TABLE `slx_daily_counter` (
  `id` int NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `run_date` date NOT NULL,
  `retail_count` int DEFAULT '0',
  `req_count` int DEFAULT '0',
  `po_count` int DEFAULT '0',
  `inbound_count` int DEFAULT '0',
  `return_count` int DEFAULT '0',
  PRIMARY KEY (`id`) USING HASH,
  UNIQUE KEY `uk_org_date` (`org_id`,`run_date`) USING HASH
) ENGINE=MEMORY AUTO_INCREMENT=3692 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=FIXED;

-- relation-detector-fixture-table: case_03.slx_holiday_effect
CREATE TABLE `slx_holiday_effect` (
  `id` int NOT NULL AUTO_INCREMENT,
  `holiday_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节假日名称',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `store_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '门店类型(NULL=所有)',
  `effect_factor` decimal(4,2) DEFAULT NULL COMMENT '效应系数',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '说明',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_date_range` (`start_date`,`end_date`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='节假日效应';

-- relation-detector-fixture-table: case_03.slx_hour_weight
CREATE TABLE `slx_hour_weight` (
  `hour_val` int NOT NULL COMMENT '小时(0~23)',
  `weekday_weight` decimal(6,4) DEFAULT NULL COMMENT '工作日权重',
  `weekend_weight` decimal(6,4) DEFAULT NULL COMMENT '周末权重',
  `description` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '说明',
  PRIMARY KEY (`hour_val`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='时段权重';

-- relation-detector-fixture-table: case_03.slx_product_tier
CREATE TABLE `slx_product_tier` (
  `id` int NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `tier` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'A/B/C/D/E/F',
  `base_daily_sales` decimal(10,4) NOT NULL COMMENT '基准日均销量',
  `target_stock_days` int NOT NULL COMMENT '目标库存天数',
  `reorder_point` decimal(10,2) DEFAULT '0.00' COMMENT '补货点',
  `reorder_qty` decimal(10,2) DEFAULT '0.00' COMMENT '补货量',
  `season_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'NORMAL' COMMENT '季节分类',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_org_mat` (`org_id`,`material_id`) USING BTREE,
  KEY `idx_org_tier` (`org_id`,`tier`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8503 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='商品分层配置';

-- relation-detector-fixture-table: case_03.slx_run_log
CREATE TABLE `slx_run_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `run_date` date NOT NULL,
  `orders_created` int DEFAULT '0',
  `items_created` int DEFAULT '0',
  `requisitions_created` int DEFAULT '0',
  `po_created` int DEFAULT '0',
  `inbound_created` int DEFAULT '0',
  `returns_created` int DEFAULT '0',
  `total_amount` decimal(24,6) DEFAULT '0.000000',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_org_date` (`org_id`,`run_date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='每日执行统计';

-- relation-detector-fixture-table: case_03.slx_store_config
CREATE TABLE `slx_store_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL COMMENT 'jsh_organization.id',
  `org_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '门店名称',
  `depot_id` bigint DEFAULT NULL COMMENT '关联仓库ID',
  `store_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'S/A/B/C 门店等级',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '城市（用于调拨）',
  `open_date` date NOT NULL COMMENT '开业日期',
  `close_date` date DEFAULT NULL COMMENT '关店日期(NULL=至今)',
  `daily_base_orders` int NOT NULL COMMENT '成熟期日均单量基准',
  `sku_count` int NOT NULL COMMENT '活跃SKU数',
  `high_ratio` decimal(4,2) DEFAULT '0.20' COMMENT '高动销占比',
  `mid_ratio` decimal(4,2) DEFAULT '0.30' COMMENT '中动销占比',
  `low_ratio` decimal(4,2) DEFAULT '0.30' COMMENT '低动销占比',
  `slow_ratio` decimal(4,2) DEFAULT '0.10' COMMENT '滞销占比',
  `dead_ratio` decimal(4,2) DEFAULT '0.10' COMMENT '未动销占比',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/ERROR',
  `last_run_date` date DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='门店造数据配置';

-- relation-detector-fixture-table: case_03.slx_store_type_config
CREATE TABLE `slx_store_type_config` (
  `store_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'S/A/B/C',
  `type_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型名称',
  `weekday_factor` decimal(4,2) DEFAULT NULL COMMENT '工作日系数',
  `weekend_factor` decimal(4,2) DEFAULT NULL COMMENT '周末系数',
  `holiday_factor` decimal(4,2) DEFAULT NULL COMMENT '节假日系数',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '典型场景',
  PRIMARY KEY (`store_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='门店类型特征';

-- relation-detector-fixture-table: case_03.slx_tier_config
CREATE TABLE `slx_tier_config` (
  `tier` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分层: A~F',
  `tier_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `sku_ratio` decimal(4,2) DEFAULT NULL COMMENT 'SKU占比',
  `sales_ratio` decimal(4,2) DEFAULT NULL COMMENT '销量贡献占比',
  `daily_sales_min` decimal(10,4) DEFAULT NULL COMMENT '日均销量下限',
  `daily_sales_max` decimal(10,4) DEFAULT NULL COMMENT '日均销量上限',
  `stock_days_min` int DEFAULT NULL COMMENT '库存天数下限',
  `stock_days_max` int DEFAULT NULL COMMENT '库存天数上限',
  `reorder_lead_days` int DEFAULT NULL COMMENT '补货提前期(天)',
  `safety_factor` decimal(4,2) DEFAULT NULL COMMENT '安全系数',
  `return_rate` decimal(4,2) DEFAULT NULL COMMENT '退货概率',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '典型商品',
  PRIMARY KEY (`tier`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='商品畅销程度基准';

-- relation-detector-fixture-table: case_03.slx_transfer_config
CREATE TABLE `slx_transfer_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `source_org_id` bigint DEFAULT NULL COMMENT '调出门店',
  `target_org_id` bigint DEFAULT NULL COMMENT '调入门店',
  `min_stock_days` int DEFAULT '30' COMMENT '触发调拨的最小库存天数(调出门店)',
  `max_stock_days` int DEFAULT '7' COMMENT '触发调拨的最大库存天数(调入门店)',
  `transfer_ratio` decimal(4,2) DEFAULT '0.50' COMMENT '调拨比例(富余量的百分比)',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source` (`source_org_id`) USING BTREE,
  KEY `idx_target` (`target_org_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='调拨配置';

-- relation-detector-fixture-table: case_03.slx_transfer_log
CREATE TABLE `slx_transfer_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `transfer_date` date DEFAULT NULL COMMENT '调拨日期',
  `source_org_id` bigint DEFAULT NULL COMMENT '调出门店',
  `target_org_id` bigint DEFAULT NULL COMMENT '调入门店',
  `material_id` bigint DEFAULT NULL COMMENT '商品ID',
  `quantity` decimal(24,6) DEFAULT NULL COMMENT '调拨数量',
  `source_stock_before` decimal(24,6) DEFAULT NULL COMMENT '调出门店调前库存',
  `target_stock_before` decimal(24,6) DEFAULT NULL COMMENT '调入门店调前库存',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_date` (`transfer_date`) USING BTREE,
  KEY `idx_source` (`source_org_id`) USING BTREE,
  KEY `idx_target` (`target_org_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='调拨日志';

-- relation-detector-fixture-table: case_03.spring_ai_store
CREATE TABLE `spring_ai_store` (
  `id` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `namespace` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `key_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `value_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- relation-detector-fixture-table: case_03.sys_batch_exec_detail_log
CREATE TABLE `sys_batch_exec_detail_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键自增ID',
  `batch_session_time` datetime NOT NULL COMMENT '批次启动时间(对应跑批主过程的 v_batch_start_time)',
  `inbound_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生成的采购入库单号(对应过程第5步生成的 v_new_number)',
  `source_order_id` bigint DEFAULT NULL COMMENT '被转换的源采购订单ID(对应过程第6步抓取到的 v_t1_id)',
  `creator_id` bigint DEFAULT NULL COMMENT '单据原始制单人ID(对应第6步抓取、第9步写入主表的 v_creator_id)',
  `warehouse_id` bigint DEFAULT NULL COMMENT '落地分配的物理仓库ID(对应第8步随机匹配出的 v_random_depot_id)',
  `time_user` decimal(10,4) DEFAULT NULL COMMENT '第4步耗时(秒)：身份及物理多租户空间校验隔离锚定',
  `time_number` decimal(10,4) DEFAULT NULL COMMENT '第5步耗时(秒)：自动生成新单据编号(排他自增序列计算)',
  `time_order` decimal(10,4) DEFAULT NULL COMMENT '第6-7步耗时(秒)：选择可转换源单(含FORCE INDEX加速)及弹性补位线耗时',
  `time_depot` decimal(10,4) DEFAULT NULL COMMENT '第8步耗时(秒)：操盘手物理仓库操作收货权限安全匹配',
  `time_item` decimal(10,4) DEFAULT NULL COMMENT '第11步耗时(秒)：集合化单据明细迁移(100%原装多表INNER JOIN克隆)',
  `time_stock` decimal(10,4) DEFAULT NULL COMMENT '第12步耗时(秒)：物理库存实时上架(精准反写mcs表，对应 v_time_cost_stock)',
  `time_head` decimal(10,4) DEFAULT NULL COMMENT '第13-14步耗时(秒)：子表金额汇总反写回表头及上游订单结案',
  `result_status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '执行最终状态：SUCCESS 成功 / FAIL 失败',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '异常中断或熔断时的致命错误详情(捕获自 MESSAGE_TEXT)',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_batch_session` (`batch_session_time`) USING BTREE COMMENT '批次启动时间索引，用于最终一键聚合管理看板数据'
) ENGINE=InnoDB AUTO_INCREMENT=3047 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='系统跑批事务性能高精度跟踪分析日志表(100%映射单单转换过程各步骤细分耗时)';

-- relation-detector-fixture-table: case_03.user
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `isActive` tinyint NOT NULL DEFAULT '1',
  `createdAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `IDX_e12875dfb3b1d92d7d7c5377e2` (`email`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

