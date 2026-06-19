-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;

    -- [2. 初始化内存级静默审计回执收集池]
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_batch_results (
        id BIGINT,
        order_number VARCHAR(50),
        status_msg VARCHAR(255)
    ) ENGINE=MEMORY;
    
    TRUNCATE TABLE temp_batch_results;

    -- [3. 【关键开合】：激活底层过程静默传输协议，强行关闭单步屏幕打印]
    SET @is_batch_mode = 1;

    -- [4. 大盘高能推进主循环]
    WHILE i < p_count DO
        -- 连续调用转化引擎，生成的单据状态自动跃迁为已审批，且回执流水静默灌入内存表
        CALL proc_generate_purchase_order_from_requisition(p_login_name);
        SET i = i + 1;
    END WHILE;

    -- [5. 【会话复原】：关闭静默跑批模式，将链接状态平滑还给连接池]
    SET @is_batch_mode = 0;

    -- [6. 终极聚合审计报表呈现]
    -- 摆脱琐碎步骤展现，在流程最末端，通过聚合函数一次性呈现本次批处理的完整业务战报
    SELECT 
        status_msg AS 执行结果,
        COUNT(*) AS 生成单据总行数
    FROM temp_batch_results 
    GROUP BY status_msg;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_generate_purchase_inbound
BEGIN
    -- [1. 声明大循环计数器控制指标及分批分流变量]
    DECLARE i INT DEFAULT 0;
    DECLARE v_batch_start_time DATETIME;
    
    -- 看板聚合统计变量
    DECLARE v_stat_total INT DEFAULT 0;
    DECLARE v_stat_success INT DEFAULT 0;
    DECLARE v_stat_failure INT DEFAULT 0;
    DECLARE v_stat_avg_order_time DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_stat_avg_stock_time DECIMAL(10,4) DEFAULT 0.0000;
    
    SET v_batch_start_time = NOW();
    
    -- [2. 自动清空物理历史日志表]
    TRUNCATE TABLE sys_batch_exec_detail_log;
    
    -- [3. 创建内存级极速缓冲临时表（仅作为千条级的轻量临时中转站）]
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_batch_log_buffer (
        batch_session_time DATETIME,
        inbound_number VARCHAR(50),
        source_order_id BIGINT,
        creator_id BIGINT,
        warehouse_id BIGINT,
        time_user DECIMAL(10,4),
        time_number DECIMAL(10,4),
        time_order DECIMAL(10,4),
        time_depot DECIMAL(10,4),
        time_item DECIMAL(10,4),
        time_stock DECIMAL(10,4),
        time_head DECIMAL(10,4),
        result_status VARCHAR(10),
        error_message VARCHAR(500)
    ) ENGINE=MEMORY;
    
    TRUNCATE TABLE temp_batch_log_buffer;
    
    -- [4. 激活会话大盘控制标志]
    SET @batch_mode = 1;
    SET @current_batch_session = v_batch_start_time;
    
    -- [5. 大批次驱动主循环体]
    WHILE i < p_batch_count DO
        CALL proc_generate_purchase_inbound_from_order(p_login_name);
        SET i = i + 1;
        
        -- ====== 高亮核心：每满 1000 次，自动向物理表集中刷新一次并清空内存，防止内存溢出 ======
        IF i % 1000 = 0 THEN
            INSERT INTO sys_batch_exec_detail_log (
                batch_session_time, inbound_number, source_order_id, creator_id,
                warehouse_id, time_user, time_number, time_order, time_depot,
                time_item, time_stock, time_head, result_status, error_message
            )
            SELECT 
                batch_session_time, inbound_number, source_order_id, creator_id,
                warehouse_id, time_user, time_number, time_order, time_depot,
                time_item, time_stock, time_head, result_status, error_message
            FROM temp_batch_log_buffer;
            
            -- 瞬间释放内存，内存表永远只吃 1000 条的轻量体积
            TRUNCATE TABLE temp_batch_log_buffer;
        END IF;
    END WHILE;
    
    -- [6. 循环完全收尾时，如果存在未满 1000 条的残余尾数，执行最后一次零星刷盘]
    IF i % 1000 <> 0 THEN
        INSERT INTO sys_batch_exec_detail_log (
            batch_session_time, inbound_number, source_order_id, creator_id,
            warehouse_id, time_user, time_number, time_order, time_depot,
            time_item, time_stock, time_head, result_status, error_message
        )
        SELECT 
            batch_session_time, inbound_number, source_order_id, creator_id,
            warehouse_id, time_user, time_number, time_order, time_depot,
            time_item, time_stock, time_head, result_status, error_message
        FROM temp_batch_log_buffer;
    END IF;
    
    -- [7. 会话平滑恢复，解除大盘控制标志]
    SET @batch_mode = 0;
    SET @current_batch_session = NULL;
    
    -- [8. 彻底销毁释放掉内存临时表缓冲碎片]
    DROP TEMPORARY TABLE IF EXISTS temp_batch_log_buffer;
    
    -- [9. 聚合计算当前批次的总处理指标与大盘平均基准耗时]
    SELECT 
        COUNT(*), 
        IFNULL(SUM(IF(result_status = 'SUCCESS', 1, 0)), 0),
        IFNULL(SUM(IF(result_status = 'FAIL', 1, 0)), 0),
        IFNULL(AVG(time_order), 0.0000),
        IFNULL(AVG(time_stock), 0.0000)
    INTO 
        v_stat_total, v_stat_success, v_stat_failure, 
        v_stat_avg_order_time, v_stat_avg_stock_time
    FROM sys_batch_exec_detail_log
    WHERE batch_session_time = v_batch_start_time;
    
    -- [10. 输出单行精简的管理看板汇总数据，绝不产生一万行长输出刷屏]
    SELECT 
        v_batch_start_time AS '批次启动时间',
        v_stat_total AS '请求处理循环总次数',
        v_stat_success AS '成功生成并入库单数',
        v_stat_failure AS '异常中断或熔断单数',
        CONCAT(ROUND((v_stat_success / v_stat_total) * 100, 2), '%') 
            AS '大盘批次成功率',
        v_stat_avg_order_time AS '平均抓单补位耗时(秒)',
        v_stat_avg_stock_time AS '平均库存上架耗时(秒)';
        
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_insert_purchase_requisition
BEGIN
    -- ------------------------------------------------------------------------------------
    -- 1. 变量声明
    -- ------------------------------------------------------------------------------------
    DECLARE i INT DEFAULT 0;
    DECLARE v_tenant_id BIGINT;

    -- ------------------------------------------------------------------------------------
    -- 2. 步骤 1：租户安全预检与现场熔断
    -- ------------------------------------------------------------------------------------
    -- 在进入长周期的 WHILE 大循环前，先验证登录名的物理存在性，从源头杜绝高频空转
    SELECT tenant_id INTO v_tenant_id 
    FROM jsh_user 
    WHERE login_name = p_login_name 
      AND delete_flag = '0' 
    LIMIT 1;
    
    IF v_tenant_id IS NULL THEN
        -- 未查到有效租户，拒绝高频调度，直接抛出前端错误
        SELECT 500 AS code, '无效或已被软删除的登录名，无法确定多租户安全范围' AS message;
    ELSE
        -- --------------------------------------------------------------------------------
        -- 3. 步骤 2：清空常驻物理日志表 (准备接收子过程源源不断倒入的数据)
        -- --------------------------------------------------------------------------------
        -- 每次启动一万单的大型压测数据初始化前，先清空物理日志大表，初始化计数器并释放空间
        TRUNCATE TABLE jsh_mock_batch_log;
    
    	-- --------------------------------------------------------------------------------
        -- 4. 步骤 3：开启高并发静默开关，进入高频调度大循环
        -- --------------------------------------------------------------------------------
        -- 强行抑制子过程内部所有的单条 SELECT 响应，让成功与回滚错误全部沉淀于物理表中
        SET @is_batch_mode = 1;
        
        WHILE i < p_total_count DO
            -- 级联调用底层由影子池读写分离支撑的仿真核心存储过程
            CALL proc_insert_purchase_requisition(v_tenant_id, p_start_str, p_stop_str);
            
            -- 步进器自增，严格执行串行推进
            SET i = i + 1;
        END WHILE;
        
        -- 一万单完全生成离圈，恢复正常的单体响应开关状态
        SET @is_batch_mode = 0;

        -- --------------------------------------------------------------------------------
        -- 5. 步骤 4：汇总并生成最终的、基于物理实体的各品类执行成功占比分析大报表
        -- --------------------------------------------------------------------------------
        SELECT 
            CASE WHEN code = 200 THEN '成功' ELSE '失败' END AS 状态, 
            message AS 消息反馈, 
            COUNT(*) AS 数量,
            -- 对 NULL 单号执行容错保底占位处理，防范回滚造成的聚合开闭区间塌陷
            IFNULL(MIN(number), '-------') AS 起始单号,
            IFNULL(MAX(number), '-------') AS 结束单号,
            -- 分母精确瞄准真实的物理日志全表行数，成功解决返回值为空的问题，百分比彻底对齐
            CONCAT(ROUND((COUNT(*) / (SELECT COUNT(*) FROM jsh_mock_batch_log)) * 100, 2), '%') AS 执行占比
        FROM jsh_mock_batch_log 
        GROUP BY code, message;

        -- 【架构师提示】：此处彻底取消任何 DROP TABLE 动作。
        -- 物理日志表常驻系统，长周期数据投放结束后，您可以在外部随时通过单独的查询
        -- 语句（如：SELECT * FROM jsh_mock_batch_log）翻阅、审计每一张单据的执行明细轨迹。
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_mock_retail_orders
BEGIN
    -- [1. 局部控制变量定义区]
    DECLARE v_login_id BIGINT;
    DECLARE v_tenant_id BIGINT;
    DECLARE v_plan_id BIGINT;
    DECLARE v_user_id BIGINT;
    DECLARE v_timestamp_s VARCHAR(20); 
    DECLARE v_bill_no_s VARCHAR(50); -- 顺向承接从物理计划表中读取出来的固化唯一单号
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_current_loop_error INT DEFAULT 0;
    DECLARE v_batch_counter INT DEFAULT 0;

    -- [2. 多租户安全边界锁取与空间防卫校验]
    SELECT id, tenant_id INTO v_login_id, v_tenant_id 
    FROM jsh_user WHERE login_name = p_login_name AND delete_flag = '0' LIMIT 1;

    IF v_tenant_id IS NULL THEN
        SELECT 'ERROR' AS status, 'User not found' AS message;
    ELSE
        -- 初始化物理结果总表环境，清空旧战报
        TRUNCATE TABLE `jsh_temp_mock_batch_results`;

        -- 初始化极轻量级内存高速回执暂存区
        DROP TEMPORARY TABLE IF EXISTS tmp_mock_batch_results;
        CREATE TEMPORARY TABLE tmp_mock_batch_results (
            id INT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(10), order_id BIGINT, bill_no VARCHAR(50), mock_time DATETIME, operator VARCHAR(50), item_count INT, total_amount DECIMAL(24,6), remark VARCHAR(500)
        ) ENGINE=MEMORY;

        -- 激活静默会话
        SET @is_batch_mode = 1;

        -- [3. 微观时间轴正向递增消耗驱动游标] 
        BEGIN
            -- 【单号固化拉取】：直接拉取已固化的单号列 bill_no_str，不受 TRUNCATE 影响，天衣无缝
        	DECLARE cur_plans CURSOR FOR 
			    SELECT 
			        p.id, 
			        -- 核心修正：利用子查询，从您指定的 19 家特定战略门店用户池中任意盲抽一个合法员工
			        (SELECT pool.user_id 
			         FROM jsh_mock_user_store_pool pool 
			         WHERE pool.tenant_id = v_tenant_id 
			         ORDER BY RAND() LIMIT 1) AS user_id, 
			        p.mock_timestamp_str, 
			        p.bill_no_str
			    FROM `jsh_temp_mock_plan` p
			    WHERE p.execute_status = 0
			    ORDER BY p.mock_timestamp_str ASC, p.id ASC;
            DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

            OPEN cur_plans;
            exec_loop: LOOP
                FETCH cur_plans INTO v_plan_id, v_user_id, v_timestamp_s, v_bill_no_s;
                IF v_done THEN LEAVE exec_loop; END IF;

                SET v_current_loop_error = 0;

                BEGIN
                    -- 二层隔离防御
                    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION 
                    BEGIN
                        SET v_current_loop_error = 1;
                        UPDATE `jsh_temp_mock_plan` SET execute_status = 2 WHERE id = v_plan_id;
                    END;

                    -- 顺序触发微观开单动作，传入绝对唯一的 v_bill_no_s
                    CALL proc_create_order_mock_retail(
                        v_login_id, 
                        v_user_id, 
                        v_timestamp_s,
                        v_bill_no_s
                    );

                    -- 若无报错，将缓冲区当前行计划迁徙标记为成功
                    IF v_current_loop_error = 0 THEN
                        UPDATE `jsh_temp_mock_plan` SET execute_status = 1 WHERE id = v_plan_id;
                    END IF;
                END;

                -- Chunk 分批上账累计
                SET v_batch_counter = v_batch_counter + 1;

                -- 当累积积压达到 1000 笔回执时，强倾倒至磁盘物理结果表中并释放内存
                IF v_batch_counter >= 1000 THEN
                    INSERT INTO `jsh_temp_mock_batch_results` (tenant_id, status, order_id, bill_no, mock_time, operator, item_count, total_amount, remark)
                    SELECT v_tenant_id, status, order_id, bill_no, mock_time, operator, item_count, total_amount, remark 
                    FROM tmp_mock_batch_results;

                    TRUNCATE TABLE tmp_mock_batch_results;
                    SET v_batch_counter = 0;
                END IF;

            END LOOP;
            CLOSE cur_plans;
        END;

        -- 尾波残余清扫机制
        IF v_batch_counter > 0 THEN
            INSERT INTO `jsh_temp_mock_batch_results` (tenant_id, status, order_id, bill_no, mock_time, operator, item_count, total_amount, remark)
            SELECT v_tenant_id, status, order_id, bill_no, mock_time, operator, item_count, total_amount, remark 
            FROM tmp_mock_batch_results;
        END IF;

        -- 关闭并复原静默会话控制阀门
        SET @is_batch_mode = 0;

        -- 大盘全投影月度财务业绩战报聚合输出
        SELECT 
            DATE_FORMAT(mock_time, '%Y-%m') AS 销售月份,
            COUNT(*) AS 实际成功开单数量,
            SUM(total_amount) AS 累计月销售业绩
        FROM `jsh_temp_mock_batch_results`
        WHERE status = 'SUCCESS'
        GROUP BY 1 
        ORDER BY 1 ASC;

        -- 彻底销毁中间临时缓冲区
        DROP TEMPORARY TABLE IF EXISTS tmp_mock_batch_results;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_create_order_mock_retail
BEGIN
    -- [1. 声明业务常量与核心变量]
    DECLARE STATUS_APPROVED CHAR(1) DEFAULT '1';
    DECLARE v_tenant_id BIGINT;
    DECLARE v_head_id BIGINT;
    DECLARE v_depot_id BIGINT;
    DECLARE v_user_name VARCHAR(50);
    DECLARE v_depot_s VARCHAR(1024);
    DECLARE v_depot_l VARCHAR(1024);
    
    DECLARE v_max_m INT DEFAULT 0;
    DECLARE v_auto_n INT DEFAULT 0;
    DECLARE v_elite_n INT DEFAULT 0;
    DECLARE v_item_n INT DEFAULT 0;             
    DECLARE v_total_p DECIMAL(24,6) DEFAULT 0; 
    DECLARE v_mock_dt DATETIME;
    DECLARE v_err VARCHAR(500) DEFAULT 'SUCCESS';

    -- 【架构升级移除】：彻底删除原 v_min_mat_id, v_max_mat_id, v_rand_mat_offset 等主键偏移变量
    
    -- 【全新引入】：用于 Part 2 中迭代品类阵容的游标变量
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_curr_cat_id BIGINT;
    DECLARE v_curr_t_ratio DECIMAL(4,2);

    -- [2. 游标声明：绑定最终确定的品类 bomb 阵列（按方案 B 游标循环逐个品类攻坚）]
    DECLARE cur_cat CURSOR FOR 
        SELECT cat_id, t_ratio FROM tmp_final_categories;

    -- [3. 异常捕获句柄：发生SQL错误时显式回滚事务，清理内存残余，防止死锁]
    DECLARE EXIT HANDLER FOR SQLEXCEPTION 
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_err = MESSAGE_TEXT;
        ROLLBACK;
        
        -- 显式清理本次事务涉及的所有临时表，释放内存锁
        DROP TEMPORARY TABLE IF EXISTS tmp_available_categories;
        DROP TEMPORARY TABLE IF EXISTS tmp_pdf_selection;
        DROP TEMPORARY TABLE IF EXISTS tmp_affinity_buffer;
        DROP TEMPORARY TABLE IF EXISTS tmp_final_categories;
        
        -- 根据全局上下文控制输出模式
        IF IFNULL(@is_batch_mode, 0) = 1 THEN
            INSERT INTO tmp_mock_batch_results (status, remark) 
            VALUES ('ERROR', v_err);
        ELSE
            SELECT 'ERROR' AS status, v_err AS remark;
        END IF;
    END;

    -- [4. 声明游标未找到数据的处理器]
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- [5. 开启核心原子事务区块]
    START TRANSACTION;

    -- [6. 严格的时间字符串解析逻辑：杜绝非法时钟导致的数据血缘污染]
    IF p_timestamp IS NOT NULL AND LENGTH(p_timestamp) >= 19 THEN
        SET v_mock_dt = STR_TO_DATE(p_timestamp, '%Y-%m-%d %H:%i:%s');
    END IF;

    -- 若解析失败或输入为 NULL，直接抛出系统级自定义异常，断然拒绝 NOW 兜底
    IF v_mock_dt IS NULL THEN 
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid Timestamp Format'; 
    END IF;

    -- [7. 身份校验：动态穿透锁定租户 ID 并获取当前开单柜员姓名]
    SELECT tenant_id INTO v_tenant_id 
    FROM jsh_user WHERE id = p_login_id LIMIT 1;
    
    SELECT username INTO v_user_name 
    FROM jsh_user WHERE id = p_user_id LIMIT 1;

    -- [8. 仓库匹配算法（保留原样：100% 保留 FIND_IN_SET 随机抽选）]
    SELECT `value` INTO v_depot_s 
    FROM jsh_user_business 
    WHERE tenant_id = v_tenant_id 
      AND key_id = p_user_id 
      AND `type` = 'UserDepot' LIMIT 1;

    IF v_depot_s IS NOT NULL AND v_depot_s <> '' THEN
        SET v_depot_l = REPLACE(REPLACE(REPLACE(v_depot_s, '][', ','), '[', ''), ']', '');
        
        SELECT id INTO v_depot_id FROM jsh_depot 
        WHERE tenant_id = v_tenant_id 
          AND enabled = 1 
          AND FIND_IN_SET(id, v_depot_l) 
        ORDER BY RAND() LIMIT 1;
    END IF;

    IF v_depot_id IS NULL THEN 
        SELECT id INTO v_depot_id FROM jsh_depot 
        WHERE tenant_id = v_tenant_id AND enabled = 1 LIMIT 1; 
    END IF;

    -- [9. 库存动态盘点：实时抓取该仓库下有实际库存的“活品类”]
    DROP TEMPORARY TABLE IF EXISTS tmp_available_categories;
    CREATE TEMPORARY TABLE tmp_available_categories (
        cat_id BIGINT PRIMARY KEY
    ) ENGINE=InnoDB;
    
    -- 通过联合商品表（含category_id索引）抓取活跃品类，防止空抽
    INSERT INTO tmp_available_categories (cat_id) 
    SELECT DISTINCT m.category_id 
    FROM jsh_material_current_stock mcs 
    JOIN jsh_material m ON mcs.material_id = m.id 
    WHERE mcs.depot_id = v_depot_id 
      AND mcs.current_number > 0;

    -- 熔断控制：若整个仓库已经被全部抽空，直接抛出异常
    SELECT COUNT(*) INTO v_max_m FROM tmp_available_categories;
    IF v_max_m = 0 THEN 
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Depot Empty'; 
    END IF;

    -- [10. 规模丰富度计算：采用 Box-Muller 正态分布模拟客单品类数量(1-25种)]
    SET v_auto_n = GREATEST(1, LEAST(25, 
        ABS(ROUND(((v_max_m + 1) / 6.0) + (SQRT(-2.0 * LN(IF(RAND()=0, 0.0001, RAND()))) * COS(2.0 * PI() * RAND())) * 6.0))
    ));

    -- [11. 核心架构修正：在只有几十行的配置表进行概率抽签，100% 捍卫饮料等高频品类的爆率]
    DROP TEMPORARY TABLE IF EXISTS tmp_pdf_selection;
    CREATE TEMPORARY TABLE tmp_pdf_selection (
        cat_id BIGINT, 
        target_ratio DECIMAL(4,2)
    ) ENGINE=InnoDB;
    
    SET @lp = 0;
    WHILE @lp < v_auto_n DO
        -- 上层摇号：根据 jsh_temp_category_pdf 预设的 cdf_end 刻度绝对均匀盲抽
        -- 【优势】：仅在数十条数据的内存表进行，饮料（权重8000）将稳定斩获 40% 的中签率！
        INSERT INTO tmp_pdf_selection (cat_id, target_ratio) 
        SELECT id, 1.0 
        FROM jsh_temp_category_pdf 
        WHERE cdf_end >= RAND() 
          AND id IN (SELECT cat_id FROM tmp_available_categories) 
        ORDER BY cdf_end ASC LIMIT 1;
        
        SET @lp = @lp + 1;
    END WHILE;

    -- [12. 关联连带模型 (Affinity Engine)：模拟零售核心的“啤酒与尿布”强连带]
    DROP TEMPORARY TABLE IF EXISTS tmp_affinity_buffer;
    CREATE TEMPORARY TABLE tmp_affinity_buffer (
        cat_id BIGINT, 
        t_ratio DECIMAL(4,2)
    ) ENGINE=InnoDB;
    
    INSERT INTO tmp_affinity_buffer (cat_id, t_ratio) 
    SELECT 
        aff.target_cat_id, 
        (aff.min_ratio + RAND() * (aff.max_ratio - aff.min_ratio)) 
    FROM tmp_pdf_selection s 
    JOIN jsh_temp_category_affinity aff ON s.cat_id = aff.source_cat_id 
    WHERE RAND() <= aff.link_prob 
      AND aff.target_cat_id IN (SELECT cat_id FROM tmp_available_categories);
    
    -- 将连带结果合并回主池并去重，取最大数量系数，生成最终的品类阵列
    INSERT INTO tmp_pdf_selection (cat_id, target_ratio) 
    SELECT cat_id, t_ratio FROM tmp_affinity_buffer;
    
    DROP TEMPORARY TABLE IF EXISTS tmp_final_categories;
    CREATE TEMPORARY TABLE tmp_final_categories (
        cat_id BIGINT PRIMARY KEY, 
        t_ratio DECIMAL(4,2)
    ) ENGINE=InnoDB;
    
    INSERT INTO tmp_final_categories (cat_id, t_ratio) 
    SELECT cat_id, MAX(target_ratio) 
    FROM tmp_pdf_selection 
    GROUP BY cat_id;

    -- [Part 1 结束，紧接 Part 2：物理单据生成与游标逐品类无偏采样]
    -- [13. 物理单据头生成：100% 纯物理追加写入，完全消灭排他锁等待]
    -- 直接将传入分配好的物化单号直接灌入单据头，杜绝高并发序列检索死锁
    INSERT INTO jsh_depot_head (
        type, sub_type, default_number, number, 
        create_time, oper_time, creator, status, 
        tenant_id, delete_flag
    ) VALUES (
        '出库', '零售', p_bill_no, p_bill_no, 
        v_mock_dt, v_mock_dt, p_user_id, '0', 
        v_tenant_id, '0'
    );
    SET v_head_id = LAST_INSERT_ID();


    -- [14. 品类级独立解耦采样：通过游标将大表盲抽降维为品类内微观精准命中]
    OPEN cur_cat;
    
    cat_loop: LOOP
        FETCH cur_cat INTO v_curr_cat_id, v_curr_t_ratio;
        IF v_done THEN 
            LEAVE cat_loop; 
        END IF;
        
        -- 随机决定当前品类下最终购买 2-5 种单品
        SET v_elite_n = FLOOR(RAND() * 4 + 2);
        
        -- 精准物理追加当前中签品类的单品明细
        -- 【彻底摒弃主键偏移】：利用 category_id 索引（单品类最多几百种）做极轻量纯随机排序
        -- 【优势】：饮料单品机会绝对均等，彻底解决断层霸屏和物理屏蔽缺陷，兼顾顶级索引关联性能
        INSERT INTO jsh_depot_item (
            header_id, material_id, material_extend_id, material_unit, 
            oper_number, basic_number, unit_price, all_price, 
            depot_id, tenant_id
        )
        SELECT 
            v_head_id, m_id, me_id, m_unit, 
            f_val, f_val, u_price, (u_price * f_val), 
            v_depot_id, v_tenant_id
        FROM (
            SELECT 
                mcs.material_id AS m_id, 
                jme.id AS me_id, 
                jme.commodity_unit AS m_unit, 
                jme.commodity_decimal AS u_price,
                LEAST(mcs.current_number, 
                    CASE 
                        WHEN jme.commodity_decimal > 500 AND RAND() < 0.85 THEN 0 -- 昂贵品高概率熔断
                        WHEN jme.commodity_decimal > 100 THEN 1 -- 百元以上限购 1 件
                        ELSE GREATEST(1, ROUND((FLOOR(RAND() * 2 + 1)) * v_curr_t_ratio)) 
                    END 
                ) AS f_val
            FROM jsh_material_current_stock mcs 
            JOIN jsh_material m ON mcs.material_id = m.id 
            LEFT JOIN jsh_material_extend jme ON jme.material_id = mcs.material_id
            WHERE mcs.depot_id = v_depot_id 
              AND mcs.current_number > 0 
              AND m.category_id = v_curr_cat_id -- 依靠 category_id 索引将候选池收缩至极限
            ORDER BY RAND() -- 此时候选池数据极小，进行内存纯随机不会引发性能灾难
            LIMIT v_elite_n
        ) t WHERE f_val > 0;

    END LOOP cat_loop;
    
    CLOSE cur_cat;


    -- [15. 业务汇总审计与实时库存反写：数据全闭环的核心枢纽]
    SELECT COUNT(*) INTO v_item_n 
    FROM jsh_depot_item 
    WHERE header_id = v_head_id;
    
    -- 若由于上面的价格熔断机制导致空单，直接抛出异常让系统自动整体回滚，不产生脏单头
    IF v_item_n = 0 THEN 
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Empty Order (Price Melt)'; 
    END IF;
    
    -- 【实时库存扣减】：批量物理扣减仓库实时库存，保证当前库存反映真实业务消耗
    UPDATE jsh_material_current_stock mcs
    INNER JOIN jsh_depot_item jdi 
       ON mcs.material_id = jdi.material_id 
      AND mcs.depot_id = jdi.depot_id
    SET mcs.current_number = mcs.current_number - jdi.oper_number
    WHERE jdi.header_id = v_head_id;

    -- 汇总明细总金额，反写回单据表头，并将单据状态变更为已审核状态 '1'
    SELECT SUM(all_price) INTO v_total_p 
    FROM jsh_depot_item 
    WHERE header_id = v_head_id;
    
    UPDATE jsh_depot_head 
    SET change_amount = v_total_p, 
        total_price = v_total_p, 
        status = STATUS_APPROVED 
    WHERE id = v_head_id;
    
    -- [16. 显式提交事务，彻底释放锁，平滑销毁内存级中间临时表]
    COMMIT;
    
    DROP TEMPORARY TABLE IF EXISTS tmp_available_categories;
    DROP TEMPORARY TABLE IF EXISTS tmp_pdf_selection;
    DROP TEMPORARY TABLE IF EXISTS tmp_affinity_buffer;
    DROP TEMPORARY TABLE IF EXISTS tmp_final_categories;

    -- [17. 结果规范化对齐：保持客户端与批处理引擎统一字典投影]
    IF IFNULL(@is_batch_mode, 0) = 1 THEN
        INSERT INTO tmp_mock_batch_results (
            status, order_id, bill_no, mock_time, 
            operator, item_count, total_amount, remark
        ) VALUES (
            'SUCCESS', v_head_id, p_bill_no, v_mock_dt, 
            v_user_name, v_item_n, v_total_p, 'OK'
        );
    ELSE
        SELECT 
            'SUCCESS' AS status, 
            v_head_id AS order_id, 
            p_bill_no AS bill_no, 
            v_mock_dt AS mock_time, 
            v_user_name AS operator, 
            v_item_n AS item_count, 
            v_total_p AS total_amount,
            'OK' AS remark;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_generate_purchase_inbound_from_order
BEGIN
    -- =========================================================================
    -- [1. 基础变量声明与状态字典 - 100%完全保留和恢复您原本的变量流转机制]
    -- =========================================================================
    DECLARE v_creator_id BIGINT;
    DECLARE v_tenant_id BIGINT;
    DECLARE v_random_account_id BIGINT; 
    DECLARE v_random_organ_id BIGINT;   
    DECLARE v_random_depot_id BIGINT;
    DECLARE v_new_header_id BIGINT;
    DECLARE v_batch_number_str VARCHAR(18);
    DECLARE v_calculated_time DATETIME; 
    DECLARE v_final_msg VARCHAR(500); 
    
    -- 统一路由控制码（初始默认为成功）
    DECLARE v_ret_code INT DEFAULT 200;
    
    -- 状态机常量字典
    DECLARE c_status_unapproved VARCHAR(1) DEFAULT '0'; 
    DECLARE c_status_approved VARCHAR(1) DEFAULT '1';   
    DECLARE c_status_completed VARCHAR(1) DEFAULT '2';  
    
    -- 编号生成相关变量
    DECLARE v_last_number VARCHAR(50);
    DECLARE v_new_number VARCHAR(50);
    DECLARE v_next_val BIGINT;
    
    -- 仓库权限解析变量
    DECLARE v_depot_string TEXT;
    DECLARE v_depot_list TEXT;
    
    -- 源单据数据暂存变量
    DECLARE v_t1_id BIGINT;
    DECLARE v_t1_oper_time DATETIME;
    DECLARE v_t1_remark VARCHAR(1000);
    DECLARE v_t1_sales_man VARCHAR(50);
    DECLARE v_t1_discount DECIMAL(24,6);
    DECLARE v_t1_discount_money DECIMAL(24,6);
    DECLARE v_t1_other_money DECIMAL(24,6);
    DECLARE v_t1_default_number VARCHAR(50);

    -- 高精度微秒级时间拆分计时器变量
    DECLARE v_t_start TIMESTAMP(6);
    DECLARE v_t_checkpoint TIMESTAMP(6);
    DECLARE v_time_user      DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_number    DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_order     DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_depot     DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_cost_item DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_cost_stock DECIMAL(10,4) DEFAULT 0.0000;
    DECLARE v_time_cost_head  DECIMAL(10,4) DEFAULT 0.0000;

    -- =========================================================================
    -- [2. 全局异常处理句柄 - 发生致命异常时直接回滚原子块并进行持久化审计记录]
    -- =========================================================================
    DECLARE EXIT HANDLER FOR SQLEXCEPTION 
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_final_msg = MESSAGE_TEXT;
        ROLLBACK;
        
        IF IFNULL(@batch_mode, 0) = 1 THEN
            INSERT INTO sys_batch_exec_detail_log (
                batch_session_time, inbound_number, source_order_id, 
                creator_id, warehouse_id, time_user, time_number, 
                time_order, time_depot, time_item, time_stock, 
                time_head, result_status, error_message
            ) VALUES (
                IFNULL(@current_batch_session, NOW()), v_new_number, 
                v_t1_id, v_creator_id, v_random_depot_id, v_time_user, 
                v_time_number, v_time_order, v_time_depot, v_time_cost_item, 
                v_time_cost_stock, v_time_cost_head, 'FAIL', 
                SUBSTRING(v_final_msg, 1, 500)
            );
        ELSE
            SELECT CONCAT('执行致命错误异常拦截: ', v_final_msg) AS result;
        END IF;
    END;

    -- =========================================================================
    -- [3. 引入核心业务命名块入口]
    -- =========================================================================
    proc_core: BEGIN

        SET v_t_start = NOW(6);

        -- [4. 身份及物理多租户空间校验隔离锚定]
        SELECT id, tenant_id INTO v_creator_id, v_tenant_id 
        FROM jsh_user 
        WHERE login_name = p_login_name 
          AND (delete_flag = '0' OR delete_flag IS NULL) 
        LIMIT 1;

        IF v_tenant_id IS NULL THEN
            SET v_ret_code = 500;
            SET v_final_msg = '错误：找不到对应的租户信息或操作账户已被封禁';
            ROLLBACK; 
            LEAVE proc_core; 
        END IF;

        SET v_time_user = 
            TIMESTAMPDIFF(MICROSECOND, v_t_start, NOW(6)) / 1000000.0000;

        -- [5. 自动生成新单据编号：利用排他自增序列生成最新的采购入库单编码（CGRK）]
        SET v_t_checkpoint = NOW(6);

        SELECT `number` INTO v_last_number 
        FROM jsh_depot_head 
        WHERE `type` = '入库' 
          AND sub_type = '采购' 
          AND tenant_id = v_tenant_id 
        ORDER BY id DESC, `number` DESC, default_number DESC 
        LIMIT 1;
        
        IF v_last_number IS NOT NULL THEN
            SET v_next_val = 
                CAST(SUBSTRING(v_last_number, 5) AS UNSIGNED) + 1;
            SET v_new_number = CONCAT('CGRK', LPAD(v_next_val, 11, '0'));
        ELSE
            SET v_new_number = 'CGRK00000000001';
        END IF;

        SET v_time_number = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        -- [6. 选择可转换源单 - 引入 FORCE INDEX 强制指定索引，实现性能暴涨]
        SET v_t_checkpoint = NOW(6);

        SELECT 
            id, oper_time, remark, sales_man, discount, 
            discount_money, other_money, default_number, 
            creator, organ_id, account_id 
        INTO 
            v_t1_id, v_t1_oper_time, v_t1_remark, v_t1_sales_man, v_t1_discount, 
            v_t1_discount_money, v_t1_other_money, v_t1_default_number, 
            v_creator_id, v_random_organ_id, v_random_account_id 
        FROM jsh_depot_head FORCE INDEX (idx_tenant_subtype_status_id)
        WHERE `status` = c_status_approved 
          AND sub_type = '采购订单' 
          AND tenant_id = v_tenant_id 
          AND (delete_flag = '0' OR delete_flag IS NULL) 
        ORDER BY id DESC 
        LIMIT 1;

        -- 【自动订单弹性补位线】
        IF v_t1_id IS NULL THEN
            UPDATE jsh_depot_head 
            SET `status` = c_status_approved 
            WHERE `status` = c_status_unapproved 
              AND sub_type = '采购订单' 
              AND tenant_id = v_tenant_id 
              AND (delete_flag = '0' OR delete_flag IS NULL) 
            ORDER BY id DESC 
            LIMIT 10;
            
            SELECT 
                id, oper_time, remark, sales_man, discount, 
                discount_money, other_money, default_number, 
                creator, organ_id, account_id 
            INTO 
                v_t1_id, v_t1_oper_time, v_t1_remark, v_t1_sales_man, v_t1_discount, 
                v_t1_discount_money, v_t1_other_money, v_t1_default_number, 
                v_creator_id, v_random_organ_id, v_random_account_id 
            FROM jsh_depot_head FORCE INDEX (idx_tenant_subtype_status_id)
            WHERE `status` = c_status_approved 
              AND sub_type = '采购订单' 
              AND tenant_id = v_tenant_id 
              AND (delete_flag = '0' OR delete_flag IS NULL) 
            ORDER BY id DESC 
            LIMIT 1;
        END IF;

        SET v_time_order = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        -- [7. 业务风控校验断言隔离区]
        IF v_t1_id IS NULL THEN
            SET v_ret_code = 404; 
            SET v_final_msg = CONCAT('错误：未找到可转换采购订单 (租户ID:', IFNULL(v_tenant_id, '空'), ')'); 
            ROLLBACK; LEAVE proc_core; 
        ELSEIF v_random_account_id IS NULL THEN
            SET v_ret_code = 500; 
            SET v_final_msg = CONCAT('错误：源单 [ID:', v_t1_id, '] 缺失结算账户 (account_id)'); 
            ROLLBACK; LEAVE proc_core; 
        ELSEIF v_random_organ_id IS NULL THEN
            SET v_ret_code = 500; 
            SET v_final_msg = CONCAT('错误：源单 [ID:', v_t1_id, '] 缺失供应商 (organ_id)'); 
            ROLLBACK; LEAVE proc_core; 
        END IF;

        -- [8. 操盘手物理仓库权限安全匹配]
        SET v_t_checkpoint = NOW(6);

        SELECT `value` INTO v_depot_string 
        FROM jsh_user_business 
        WHERE tenant_id = v_tenant_id 
          AND key_id = v_creator_id 
          AND `type` = 'UserDepot' 
        LIMIT 1;
        
        IF v_depot_string IS NOT NULL AND v_depot_string <> '' THEN
            SET v_depot_list = 
                REPLACE(REPLACE(REPLACE(v_depot_string, '][', ','), '[', ''), ']', '');
            SELECT id INTO v_random_depot_id 
            FROM jsh_depot 
            WHERE tenant_id = v_tenant_id 
              AND enabled = 1 
              AND (delete_flag = '0' OR delete_flag IS NULL) 
              AND FIND_IN_SET(id, v_depot_list) 
            ORDER BY RAND() 
            LIMIT 1;
        END IF;

        IF v_random_depot_id IS NULL THEN
            SET v_ret_code = 500; 
            SET v_final_msg = 
                CONCAT('错误：操作员 [ID:', v_creator_id, '] 无任何有效的物理仓库操作收货权限'); 
            ROLLBACK; LEAVE proc_core; 
        END IF;

        SET v_time_depot = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        -- [9. 时钟演进计算与入库表头落地]
        SET v_calculated_time = 
            DATE_ADD(v_t1_oper_time, INTERVAL (FLOOR(3 + (RAND() * 8))) DAY);
        IF v_calculated_time > NOW() THEN 
            SET v_calculated_time = NOW(); 
        END IF;

        START TRANSACTION;

        INSERT INTO jsh_depot_head (
            `type`, sub_type, default_number, `number`, create_time, 
            oper_time, organ_id, creator, account_id, pay_type, 
            status, remark, sales_man, discount, discount_money, 
            other_money, link_number, tenant_id, delete_flag
        ) VALUES (
            '入库', '采购', v_new_number, v_new_number, v_calculated_time, 
            v_calculated_time, v_random_organ_id, v_creator_id, 
            v_random_account_id, '现付', c_status_approved, v_t1_remark, 
            v_t1_sales_man, v_t1_discount, v_t1_discount_money, 
            v_t1_other_money, v_t1_default_number, v_tenant_id, '0'
        );

        SET v_new_header_id = LAST_INSERT_ID();

        -- [10. 追溯流水批次号拼装]
        SET v_batch_number_str = 
            CONCAT(DATE_FORMAT(v_calculated_time, '%Y%m%d%H%i%s'), 
                   LPAD(FLOOR(RAND() * 10000), 4, '0'));
        
        -- [11. 集合化单据明细迁移：100%原汁原味还原您的多表 INNER JOIN 业务原貌]
        SET v_t_checkpoint = NOW(6);

        INSERT INTO jsh_depot_item (
            header_id, material_id, material_extend_id, material_unit, sku, 
            oper_number, basic_number, unit_price, purchase_unit_price, 
            tax_unit_price, all_price, remark, depot_id, another_depot_id, 
            tax_rate, tax_money, tax_last_money, material_type, sn_list, 
            batch_number, expiration_date, link_id, tenant_id, delete_flag
        )
        SELECT 
            v_new_header_id, jdi.material_id, jdi.material_extend_id, jdi.material_unit, jdi.sku, 
            jdi.oper_number, jdi.basic_number, jme.purchase_decimal, jdi.purchase_unit_price, 
            jdi.tax_unit_price, (jdi.oper_number * jme.purchase_decimal), jdi.remark, v_random_depot_id, 
            jdi.another_depot_id, jdi.tax_rate, jdi.tax_money, (jdi.oper_number * jme.purchase_decimal), 
            jdi.material_type, jdi.sn_list, v_batch_number_str, 
            DATE_ADD(v_calculated_time, INTERVAL IFNULL(m.expiry_num, 0) DAY), 
            jdi.id, jdi.tenant_id, jdi.delete_flag
        FROM jsh_depot_item jdi 
        INNER JOIN jsh_material_extend jme ON jdi.material_id = jme.material_id 
        INNER JOIN jsh_material m ON jdi.material_id = m.id 
        WHERE jdi.header_id = v_t1_id 
          AND jdi.tenant_id = v_tenant_id 
          AND (jdi.delete_flag = '0' OR jdi.delete_flag IS NULL);

        SET v_time_cost_item = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        -- [12. 【库存高能累加核心线】：100%原汁原味还原您的无索引库存上架逻辑]
        SET v_t_checkpoint = NOW(6);

        UPDATE jsh_material_current_stock mcs
        INNER JOIN jsh_depot_item jdi ON mcs.material_id = jdi.material_id AND mcs.depot_id = jdi.depot_id
        SET mcs.current_number = mcs.current_number + jdi.oper_number
        WHERE jdi.header_id = v_new_header_id; 

        SET v_time_cost_stock = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        -- [13. 金额汇总更新 与 14.上游订单结案：100%原汁原味保留您的原始负数取反逻辑公式]
        SET v_t_checkpoint = NOW(6);

        UPDATE jsh_depot_head h 
        INNER JOIN (
            SELECT 
                header_id, 
                SUM(all_price) AS sum_all, 
                SUM(tax_last_money) AS sum_tax_last 
            FROM jsh_depot_item 
            WHERE header_id = v_new_header_id 
              AND tenant_id = v_tenant_id 
            GROUP BY header_id
        ) i ON h.id = i.header_id 
        SET h.change_amount = -(i.sum_all), 
            h.total_price = -(i.sum_all), 
            h.discount_last_money = i.sum_tax_last 
        WHERE h.id = v_new_header_id;

        UPDATE jsh_depot_head 
        SET `status` = c_status_completed 
        WHERE id = v_t1_id;

        SET v_time_cost_head = 
            TIMESTAMPDIFF(MICROSECOND, v_t_checkpoint, NOW(6)) / 1000000.0000;

        COMMIT;
        SET v_final_msg = CONCAT('成功：入库单 ', v_new_number, ' 处理完成。');
    END proc_core;

    -- =========================================================================
    -- [15. 【全盘唯一】单点输出网关路由控制中心 - 已重定向至纯内存临时表]
    -- =========================================================================
    IF IFNULL(@batch_mode, 0) = 1 THEN
        -- 高亮：在这里将写入目标替换为纯内存临时表，大循环期间享受纯内存性能
        INSERT INTO temp_batch_log_buffer (
            batch_session_time, inbound_number, source_order_id, creator_id, 
            warehouse_id, time_user, time_number, time_order, time_depot, 
            time_item, time_stock, time_head, result_status, error_message
        ) VALUES (
            IFNULL(@current_batch_session, NOW()), v_new_number, v_t1_id, 
            v_creator_id, v_random_depot_id, v_time_user, v_time_number, 
            v_time_order, v_time_depot, v_time_cost_item, v_time_cost_stock, 
            v_time_cost_head, IF(v_ret_code = 200, 'SUCCESS', 'FAIL'), 
            IF(v_ret_code = 200, v_final_msg, 
                                 CONCAT('业务逻辑阻断: ', v_final_msg))
        );
    ELSE
        SELECT IF(v_ret_code = 200, v_final_msg, 
                             CONCAT('错误: ', v_final_msg)) AS result;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_generate_purchase_order_from_requisition
BEGIN
    -- [1. 变量定义区]
    DECLARE v_creator_id BIGINT;
    DECLARE v_tenant_id BIGINT;
    DECLARE v_random_account_id BIGINT;
    DECLARE v_random_organ_id BIGINT;
    DECLARE v_new_header_id BIGINT;
    DECLARE v_last_number VARCHAR(50);
    DECLARE v_new_number VARCHAR(50);
    DECLARE v_target_create_time DATETIME; 
    
    -- 统一状态字典寄存器（用于扁平化输出改造，初始默认为成功状态）
    DECLARE v_ret_code INT DEFAULT 200;
    DECLARE v_ret_msg VARCHAR(255) DEFAULT '成功';
    
    -- 单据状态机控制常量
    DECLARE v_status_unapproved VARCHAR(1) DEFAULT '0'; -- 未审批
    DECLARE v_status_approved VARCHAR(1) DEFAULT '1';   -- 已审批
    DECLARE v_status_closed VARCHAR(1) DEFAULT '2';     -- 已结案/已转单

    -- 请购单数据高速暂存变量
    DECLARE v_t1_id BIGINT;
    DECLARE v_t1_create_time DATETIME;
    DECLARE v_t1_remark VARCHAR(1000);
    DECLARE v_t1_sales_man VARCHAR(50);
    DECLARE v_t1_discount DECIMAL(24,6);
    DECLARE v_t1_discount_money DECIMAL(24,6);
    DECLARE v_t1_other_money DECIMAL(24,6);
    DECLARE v_t1_default_number VARCHAR(50);

    -- [2. 异常捕获句柄：发生致命异常时即时整体回滚事务，并重写错误状态寄存器]
    -- 核心修复：通过向外层主代码块报告异常状态，使控制流可以平滑滑向末端网关
    DECLARE EXIT HANDLER FOR SQLEXCEPTION 
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_ret_msg = MESSAGE_TEXT;
        SET v_ret_code = 500;
        ROLLBACK;
    END;

    -- [3. 引入命名业务核心块，用于随时实施非结构化跳转退出]
    proc_core: BEGIN

        -- [4. 激活原子事务]
        START TRANSACTION;

        -- [5. 多租户隔离校验：根据账户名安全穿透并锁取租户物理空间范围]
        SELECT tenant_id INTO v_tenant_id FROM jsh_user WHERE login_name = p_login_name AND delete_flag = '0' LIMIT 1;
        
        IF v_tenant_id IS NULL THEN
            SET v_ret_code = 500;
            SET v_ret_msg = '错误：找不到对应的租户信息';
            ROLLBACK;
            LEAVE proc_core; -- 语法修复：用 LEAVE 代替 GOTO，安全跳出块
        END IF;

        -- [6. 核心筛选源单逻辑：从源头筛选当前租户下处于“已审批”状态的可转化请购单]
        SELECT id INTO v_t1_id
        FROM jsh_depot_head FORCE INDEX (idx_tenant_subtype_status_id)
        WHERE `status` = v_status_approved
        	AND sub_type = '请购单'
        	AND tenant_id = v_tenant_id
        	AND (delete_flag = '0' OR delete_flag IS NULL)
        ORDER BY id DESC
        LIMIT 1;

        -- 【自动弹性批准机制】：若当前大盘中无积压的已审批请购单，则自动将 10 条未审批的请购单强行推进为已审批，激活任务
        IF v_t1_id IS NULL THEN
            UPDATE jsh_depot_head SET `status` = v_status_approved 
            WHERE `status` = v_status_unapproved AND sub_type = '请购单' AND tenant_id = v_tenant_id
              AND (delete_flag = '0' OR delete_flag IS NULL) LIMIT 10;
            
            -- 批准后重新尝试捕捉可转化的请购单源头
            SELECT id INTO v_t1_id FROM jsh_depot_head
            WHERE `status` = v_status_approved AND sub_type = '请购单' AND tenant_id = v_tenant_id
              AND (delete_flag = '0' OR delete_flag IS NULL) LIMIT 1;
        END IF;

        -- 【空值防崩断言】：若经过上述弹性拉动后依然无可转化单据，抛出软错误，不造成系统崩塌
        IF v_t1_id IS NULL THEN
            SET v_ret_code = 404;
            SET v_ret_msg = '错误：无可用的请购单';
            ROLLBACK;
            LEAVE proc_core; -- 语法修复：跳至末端输出网关
        END IF;

        -- [7. 源单元数据无损抽取：完整提取原始请购单的核心主表关键指标]
        SELECT id, create_time, remark, sales_man, discount, discount_money, 
               other_money, default_number, creator
        INTO v_t1_id, v_t1_create_time, v_t1_remark, v_t1_sales_man, v_t1_discount, 
             v_t1_discount_money, v_t1_other_money, v_t1_default_number, v_creator_id
        FROM jsh_depot_head WHERE id = v_t1_id;

        -- [8. 必要数据链匹配：随机投掷分配符合审计规范的标准结算账户与合规供应商]
        SELECT id INTO v_random_account_id FROM jsh_account 
        WHERE tenant_id = v_tenant_id AND `name` IN ('现金账户','微信收款','支付宝收款','银联POS','总部-中信银行')
          AND (delete_flag = '0' OR delete_flag IS NULL) ORDER BY RAND() LIMIT 1;

        SELECT id INTO v_random_organ_id FROM jsh_supplier 
        WHERE tenant_id = v_tenant_id AND account_number IS NOT NULL
          AND (delete_flag = '0' OR delete_flag IS NULL) ORDER BY RAND() LIMIT 1;

        -- [9. 完整性熔断检查：严防死锁以及外键不匹配引起的数据库物理层崩溃]
        IF v_random_account_id IS NULL THEN
            SET v_ret_code = 500; SET v_ret_msg = '错误：找不到可用的结算账户'; ROLLBACK; LEAVE proc_core;
        ELSEIF v_random_organ_id IS NULL THEN
            SET v_ret_code = 500; SET v_ret_msg = '错误：找不到可用的供应商'; ROLLBACK; LEAVE proc_core;
        ELSEIF v_creator_id IS NULL THEN
            SET v_ret_code = 500; SET v_ret_msg = '错误：源单据创建人信息缺失'; ROLLBACK; LEAVE proc_core;
        END IF;

        -- [10. 排他流水号迭代生成：寻找历史最新采购订单编码，并基于其自增 1 生成新序列号]
        SELECT `number` INTO v_last_number FROM jsh_depot_head 
        WHERE `type` = '其它' AND sub_type = '采购订单' 
        ORDER BY id DESC, `number` DESC LIMIT 1;

        IF v_last_number IS NOT NULL THEN
            SET v_new_number = CONCAT('CGDD', LPAD(CAST(SUBSTRING(v_last_number, 5) AS UNSIGNED) + 1, 11, '0'));
        ELSE
            SET v_new_number = 'CGDD00000000001';
        END IF;

        -- [11. 历史时钟平滑演进：在请购单创建时间的基础上，投掷 7 天内的随机延时秒数，且绝不超越当天当前时间]
        SET v_target_create_time = LEAST(
            DATE_ADD(v_t1_create_time, INTERVAL (RAND() * 7 * 24 * 60 * 60) SECOND), 
            NOW()
        );

        -- [12. 插入采购订单物理表头：状态直接变更为已审批状态 '1'，数据流一键完结]
        INSERT INTO jsh_depot_head (
            `type`, sub_type, default_number, `number`, create_time, oper_time, 
            organ_id, creator, account_id, pay_type, status, remark, 
            sales_man, discount, discount_money, other_money, link_apply, 
            tenant_id, delete_flag
        ) VALUES (
            '其它', '采购订单', v_new_number, v_new_number, v_target_create_time, v_target_create_time,
            v_random_organ_id, v_creator_id, v_random_account_id, '现付', 
            v_status_approved, v_t1_remark, v_t1_sales_man, v_t1_discount, 
            v_t1_discount_money, v_t1_other_money, v_t1_default_number, v_tenant_id, '0'
        );

        SET v_new_header_id = LAST_INSERT_ID();

        -- [13. 集合化单据明细克隆：内联产品价格扩展表，将请购单的所有商品明细行一次性穿透克隆至采购订单明细]
        INSERT INTO jsh_depot_item (
            header_id, material_id, material_extend_id, material_unit, sku, 
            oper_number, basic_number, unit_price, purchase_unit_price, 
            tax_unit_price, all_price, remark, depot_id, tax_rate, 
            tax_money, tax_last_money, material_type, link_id, tenant_id, delete_flag
        )
        SELECT 
            v_new_header_id, jdi.material_id, jdi.material_extend_id, jdi.material_unit, jdi.sku, 
            jdi.oper_number, jdi.basic_number, jme.purchase_decimal, jdi.purchase_unit_price, 
            jdi.tax_unit_price, (jdi.oper_number * jme.purchase_decimal), jdi.remark, jdi.depot_id, 
            jdi.tax_rate, jdi.tax_money, (jdi.oper_number * jme.purchase_decimal), jdi.material_type, 
            jdi.id, jdi.tenant_id, jdi.delete_flag
        FROM jsh_depot_item jdi
        INNER JOIN jsh_material_extend jme ON jdi.material_id = jme.material_id
        WHERE jdi.header_id = v_t1_id AND (jdi.delete_flag = '0' OR jdi.delete_flag IS NULL);

        -- [14. 金额汇总并回写：统计刚才克隆过来的子表价税合计总金额，并反向更新到订单表头中]
        UPDATE jsh_depot_head h
                INNER JOIN (
            SELECT header_id, SUM(all_price) as sum_all, SUM(tax_last_money) as sum_tax_last 
            FROM jsh_depot_item WHERE header_id = v_new_header_id GROUP BY header_id
        ) i ON h.id = i.header_id
        SET h.total_price = -(i.sum_all), h.discount_last_money = i.sum_tax_last
        WHERE h.id = v_new_header_id;

        -- [15. 源单据生命周期状态迁移：将上游的请购单变更为状态 '2' (已结案/已转单)]
        UPDATE jsh_depot_head SET `status` = v_status_closed WHERE id = v_t1_id;

        -- 正常完结，正式将数据写入磁盘，释放行锁
        COMMIT;

    END proc_core;

    -- [16. 【全盘唯一】单点输出网关路由控制中心]
    -- 无论是正常提交还是通过 LEAST 跳出，全部汇聚于此进行单点输出控制，代码扁平干净
    IF IFNULL(@is_batch_mode, 0) = 1 THEN
        -- 批处理静默链路
        INSERT INTO temp_batch_results (id, order_number, status_msg) 
        VALUES (IF(v_ret_code=200, v_new_header_id, NULL), IF(v_ret_code=200, v_new_number, NULL), v_ret_msg);
    ELSE
        -- 独立终端展示链路
        SELECT 
            IF(v_ret_code=200, v_new_header_id, NULL) AS new_id, 
            IF(v_ret_code=200, v_new_number, NULL) AS new_order_number, 
            v_ret_msg AS result_status;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_init_yearly_weights
BEGIN
    -- [1. 声明日期边界变量]
    DECLARE v_curr_date DATE;
    DECLARE v_end_date DATE;
    
    SET v_curr_date = CONCAT(p_year, '-01-01');
    SET v_end_date  = CONCAT(p_year, '-12-31');

    -- 清空历史日历
    TRUNCATE TABLE `jsh_temp_day_pdf`;

    -- [2. 基础阳历与周末框架循环注入]
    WHILE v_curr_date <= v_end_date DO
        INSERT INTO `jsh_temp_day_pdf` (
            day_date, 
            weight, 
            is_holiday, 
            remark
        )
        SELECT 
            v_curr_date,
            CASE 
                -- 电商三大固定大促
                WHEN MONTH(v_curr_date) = 11 AND DAY(v_curr_date) = 11 THEN 850 -- 双11
                WHEN MONTH(v_curr_date) = 6  AND DAY(v_curr_date) = 18 THEN 650 -- 618
                WHEN MONTH(v_curr_date) = 12 AND DAY(v_curr_date) = 12 THEN 450 -- 双12
                
                -- 公历固定法定节假日
                WHEN MONTH(v_curr_date) = 10 AND DAY(v_curr_date) BETWEEN 1 AND 7 THEN 400 -- 国庆
                WHEN MONTH(v_curr_date) = 1  AND DAY(v_curr_date) = 1 THEN 250 -- 元旦
                WHEN MONTH(v_curr_date) = 5  AND DAY(v_curr_date) BETWEEN 1 AND 3 THEN 300 -- 五一
                
                -- 常规情人节零售高峰
                WHEN MONTH(v_curr_date) = 2  AND DAY(v_curr_date) = 14 THEN 220 
                
                -- 基础周末加成
                WHEN DAYOFWEEK(v_curr_date) IN (1, 7) THEN 160 
                
                -- 平时工作日
                ELSE 100 
            END,
            CASE 
                WHEN MONTH(v_curr_date) = 10 AND DAY(v_curr_date) BETWEEN 1 AND 7 THEN 1
                WHEN MONTH(v_curr_date) = 1  AND DAY(v_curr_date) = 1 THEN 1
                WHEN MONTH(v_curr_date) = 5  AND DAY(v_curr_date) BETWEEN 1 AND 3 THEN 1
                ELSE 0 
            END,
            '基础日历自动划分';
            
        SET v_curr_date = DATE_ADD(v_curr_date, INTERVAL 1 DAY);
    END WHILE;

    -- [3. 动态修正层：精准匹配中国农历移动节假日]
    
    -- === 3.1 历史 2024 年农历特殊节点修正 ===
    IF p_year = 2024 THEN
        -- 2024春节（2月10日春节，除夕为2月9日）
        UPDATE `jsh_temp_day_pdf` SET weight = 350, remark = '24年春节前办年货' WHERE day_date BETWEEN '2024-02-01' AND '2024-02-08';
        UPDATE `jsh_temp_day_pdf` SET weight = 450, is_holiday = 1, remark = '24年春节高峰' WHERE day_date BETWEEN '2024-02-09' AND '2024-02-12';
        UPDATE `jsh_temp_day_pdf` SET weight = 250, remark = '24年春节尾声' WHERE day_date BETWEEN '2024-02-13' AND '2024-02-17';
        -- 2024端午与中秋
        UPDATE `jsh_temp_day_pdf` SET weight = 220, is_holiday = 1, remark = '24年端午假期' WHERE day_date BETWEEN '2024-06-08' AND '2024-06-10';
        UPDATE `jsh_temp_day_pdf` SET weight = 280, is_holiday = 1, remark = '24年中秋假期' WHERE day_date BETWEEN '2024-09-15' AND '2024-09-17';

    -- === 3.2 历史 2025 年农历特殊节点修正 ===
    ELSEIF p_year = 2025 THEN
        -- 2025春节（1月29日春节，除夕为1月28日）
        UPDATE `jsh_temp_day_pdf` SET weight = 350, remark = '25年春节前办年货' WHERE day_date BETWEEN '2025-01-20' AND '2025-01-28';
        UPDATE `jsh_temp_day_pdf` SET weight = 450, is_holiday = 1, remark = '25年春节高峰' WHERE day_date BETWEEN '2025-01-29' AND '2025-02-01';
        UPDATE `jsh_temp_day_pdf` SET weight = 250, remark = '25年春节尾声' WHERE day_date BETWEEN '2025-02-02' AND '2025-02-05';
        -- 2025端午与中秋
        UPDATE `jsh_temp_day_pdf` SET weight = 220, is_holiday = 1, remark = '25年端午假期' WHERE day_date BETWEEN '2025-05-31' AND '2025-06-02';
        UPDATE `jsh_temp_day_pdf` SET weight = 280, is_holiday = 1, remark = '25年中秋假期' WHERE day_date BETWEEN '2025-10-06' AND '2025-10-08';

    -- === 3.3 当前 2026 年农历特殊节点修正 ===
    ELSEIF p_year = 2026 THEN
        -- 2026春节（2月17日春节，除夕为2月16日）
        UPDATE `jsh_temp_day_pdf` SET weight = 350, remark = '26年春节前办年货' WHERE day_date BETWEEN '2026-02-08' AND '2026-02-15';
        UPDATE `jsh_temp_day_pdf` SET weight = 450, is_holiday = 1, remark = '26年春节高峰' WHERE day_date BETWEEN '2026-02-16' AND '2026-02-19';
        -- 2026端午与中秋
        UPDATE `jsh_temp_day_pdf` SET weight = 220, is_holiday = 1, remark = '26年端午假期' WHERE day_date BETWEEN '2026-06-18' AND '2026-06-20';
        UPDATE `jsh_temp_day_pdf` SET weight = 280, is_holiday = 1, remark = '26年中秋假期' WHERE day_date BETWEEN '2026-09-24' AND '2026-09-26';

    -- === 3.4 兜底机制 ===
    ELSE
        -- 未预置的具体年份，默认将 2 月前 7 天做平滑替代处理
        UPDATE `jsh_temp_day_pdf` SET weight = 300, is_holiday = 1, remark = '未知年份动态兜底假期' WHERE MONTH(day_date) = 2 AND DAY(day_date) BETWEEN 1 AND 7;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_insert_purchase_requisition
BEGIN
    -- ------------------------------------------------------------------------------------
    -- 1. 变量声明
    -- ------------------------------------------------------------------------------------
    DECLARE v_status_approved TINYINT DEFAULT 1;
    DECLARE v_ret_code INT DEFAULT 200;
    DECLARE v_ret_msg VARCHAR(255) DEFAULT '成功';
    DECLARE v_new_id BIGINT DEFAULT NULL;
    DECLARE v_gen_number VARCHAR(50) DEFAULT NULL;
    DECLARE v_creator_id BIGINT;
    DECLARE v_limit_count INT;
    DECLARE v_last_number VARCHAR(50);
    DECLARE v_start_dt DATETIME;
    DECLARE v_stop_dt DATETIME;
    DECLARE v_random_oper_time DATETIME; 

    -- 控制 A 阶段多品类抽取的变量
    DECLARE v_main_cat_count INT;
    DECLARE v_loop_i INT DEFAULT 0;
    DECLARE v_tmp_cat_id BIGINT;
    
    -- 本地变量：承载被选中战略门店 ID
    DECLARE v_selected_org_id BIGINT;

    -- ------------------------------------------------------------------------------------
    -- 2. 异常捕捉处理器
    -- ------------------------------------------------------------------------------------
    DECLARE EXIT HANDLER FOR SQLEXCEPTION 
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_ret_msg = MESSAGE_TEXT;
        SET v_ret_code = 500;
        ROLLBACK;
        
        DROP TEMPORARY TABLE IF EXISTS tmp_selected_main_cats;
        DROP TEMPORARY TABLE IF EXISTS tmp_affinity_contributions;
        DROP TEMPORARY TABLE IF EXISTS tmp_affinity_merged_pool;
        DROP TEMPORARY TABLE IF EXISTS tmp_final_item_pool;
        DROP TEMPORARY TABLE IF EXISTS tmp_final_item_pool_shadow; -- 【清理影子】

        IF IFNULL(@is_batch_mode, 0) = 1 THEN
            INSERT INTO jsh_mock_batch_log (code, message, new_id, number) 
            VALUES (v_ret_code, v_ret_msg, v_new_id, v_gen_number);
        ELSE
            SELECT v_ret_code AS code, v_ret_msg AS message, v_new_id AS new_id, v_gen_number AS number;
        END IF;
    END;

    -- ------------------------------------------------------------------------------------
    -- 3. 环境参数解析与通用数据完整性校验 (战略门店物理池秒级盲抽)
    -- ------------------------------------------------------------------------------------
    SET v_start_dt = STR_TO_DATE(CONCAT(LEFT(p_start_str, 10), ' ', RIGHT(p_start_str, 2), ':00:00'), '%Y-%m-%d %H:%i:%s');
    SET v_stop_dt = STR_TO_DATE(CONCAT(LEFT(p_stop_str, 10), ' ', RIGHT(p_stop_str, 2), ':00:00'), '%Y-%m-%d %H:%i:%s');

    -- 从实体池中，原子化捕获匹配的用户ID与门店ID，耗时接近 0
    SELECT user_id, org_id INTO v_creator_id, v_selected_org_id
    FROM jsh_mock_user_store_pool
    WHERE tenant_id = p_tenant_id
    ORDER BY RAND()
    LIMIT 1;
    
    IF v_creator_id IS NULL THEN
        SET v_ret_code = 500; 
        SET v_ret_msg = '战略门店池未初始化或未找到有效用户';
    ELSE
        -- --------------------------------------------------------------------------------
        -- 初始化本次请求所需的会话级内存计算临时表
        -- --------------------------------------------------------------------------------
        CREATE TEMPORARY TABLE tmp_selected_main_cats (
            category_id BIGINT PRIMARY KEY,
            base_weight INT,
            hit_count INT DEFAULT 1
        ) ENGINE=MEMORY;

        CREATE TEMPORARY TABLE tmp_affinity_contributions (
            source_cat_id BIGINT,
            target_cat_id BIGINT,
            fail_prob DECIMAL(4,3),
            computed_sub_qty DECIMAL(10,2)
        ) ENGINE=MEMORY;

        CREATE TEMPORARY TABLE tmp_affinity_merged_pool (
            target_cat_id BIGINT PRIMARY KEY,
            combined_fail_prob DECIMAL(10,6) DEFAULT 1.000000,
            allocated_qty INT
        ) ENGINE=MEMORY;

        -- 最终持久化主池
        CREATE TEMPORARY TABLE tmp_final_item_pool (
            material_id BIGINT,
            material_extend_id BIGINT,
            commodity_unit VARCHAR(50),
            final_qty INT
        ) ENGINE=MEMORY;

        -- 【影子模式核心改动 1.1】：初始化一个无索引、极简结构的只读快照影子池
        CREATE TEMPORARY TABLE tmp_final_item_pool_shadow (
            material_id BIGINT PRIMARY KEY
        ) ENGINE=MEMORY;

        -- --------------------------------------------------------------------------------
        -- A阶段 - 精修 3σ 高斯模型确定品类摇号机会次数
        -- --------------------------------------------------------------------------------
        SET v_main_cat_count = FLOOR(4.0 + 0.65 * (SQRT(-2 * LOG(RAND())) * COS(2 * PI() * RAND())));
        IF v_main_cat_count <= 0 THEN 
            SET v_main_cat_count = 1; 
        END IF;

        -- 循环轮询抽取主品类，重复物料热度自增累加
        WHILE v_loop_i < v_main_cat_count DO
            SELECT id INTO v_tmp_cat_id FROM jsh_temp_category_pdf 
            WHERE cdf_end >= RAND() ORDER BY cdf_end ASC LIMIT 1;
            
            IF v_tmp_cat_id IS NOT NULL THEN
                INSERT INTO tmp_selected_main_cats (category_id, base_weight, hit_count)
                SELECT id, weight, 1 FROM jsh_temp_category_pdf WHERE id = v_tmp_cat_id
                ON DUPLICATE KEY UPDATE hit_count = hit_count + 1;
            END IF;
            
            SET v_loop_i = v_loop_i + 1;
        END WHILE;

        IF (SELECT COUNT(*) FROM tmp_selected_main_cats) = 0 THEN
            INSERT INTO tmp_selected_main_cats (category_id, base_weight, hit_count) VALUES (49, 800, 1);
        END IF;

        -- 开启核心事务
        START TRANSACTION;
        
        -- --------------------------------------------------------------------------------
        -- 【核心注释 4】：步骤 4 - 单据编号与生成规模确定
        -- --------------------------------------------------------------------------------
        -- 4.1 步进流水号精算：
        --     从主表中锁定当前租户最新的一张请购单。若存在，提取其数字后缀并刚性执行 +1 
        --     步进；若为首张单据，则使用 'QGD00000000001' 作为初始种子。
        SELECT `number` INTO v_last_number 
        FROM jsh_depot_head 
        WHERE `type` = '其它' 
          AND sub_type = '请购单' 
          AND tenant_id = p_tenant_id 
        ORDER BY id DESC 
        LIMIT 1;
        
        SET v_gen_number = IF(
            v_last_number IS NOT NULL, 
            CONCAT('QGD', LPAD(CAST(SUBSTRING(v_last_number, 4) AS UNSIGNED) + 1, 11, '0')), 
            'QGD00000000001'
        );
        
        -- 4.2 双时间戳绝对对齐：
        --     在传入的业务起始时间 (v_start_dt) 与结束时间 (v_stop_dt) 之间，利用秒级
        --     时间戳加随机数，精算出一个均匀分布的业务发生时间。后续持久化时，将强制
        --     把 create_time 和 oper_time 设为该相同值，防止系统产生时间断层。
        SET v_random_oper_time = FROM_UNIXTIME(
            UNIX_TIMESTAMP(v_start_dt) + RAND() * (
                UNIX_TIMESTAMP(v_stop_dt) - UNIX_TIMESTAMP(v_start_dt)
            )
        );

        -- --------------------------------------------------------------------------------
        -- 规模与数量 - 依据品类固有权重以及【命中热度】级联放大主商品采购量
        -- --------------------------------------------------------------------------------
        SET @current_cat = 0;
        SET @row_num = 0;

        INSERT INTO tmp_final_item_pool (material_id, material_extend_id, commodity_unit, final_qty)
        SELECT 
            m_res.id, 
            jme.id, 
            jme.commodity_unit,
            -- 单价数量 = 固有基础量 * 命中次数 * 限幅波动因子 [0.9, 1.1]
            FLOOR(ROUND(m_res.base_weight * 0.05) * m_res.hit_count * (0.9 + RAND() * 0.2)) + 1
        FROM (
            SELECT 
                m.id, 
                m.category_id, 
                cats.base_weight,
                cats.hit_count, 
                @row_num := IF(@current_cat = m.category_id, @row_num + 1, 1) AS rn,
                @current_cat := m.category_id AS dummy
            FROM jsh_material m
            INNER JOIN tmp_selected_main_cats cats ON m.category_id = cats.category_id
            WHERE m.tenant_id = p_tenant_id AND m.delete_flag = '0'
            ORDER BY m.category_id, RAND()
        ) AS m_res
        INNER JOIN jsh_material_extend jme ON m_res.id = jme.material_id
        WHERE m_res.rn <= (FLOOR(RAND() * 3) + 1); -- 每类随机请购 1~3 款单品

        -- 【影子模式核心改动 1.2】：在第一批插入完成后，瞬间捕获主池已占用的商品快照到影子表中。
        -- 此时主商品一般仅包含几条数据，在纯内存复制的耗时无限趋近于 0。
        INSERT INTO tmp_final_item_pool_shadow (material_id)
        SELECT material_id FROM tmp_final_item_pool;

        -- --------------------------------------------------------------------------------
        -- 连带联动 - 跨品类亲和力概率叠加与数量等比合并计算 (已自动继承放大后的源头数量)
        -- --------------------------------------------------------------------------------
        -- 1. 收集所有已生成的主商品对下游关联品类产生的独立失败率贡献与联动数量
        INSERT INTO tmp_affinity_contributions (source_cat_id, target_cat_id, fail_prob, computed_sub_qty)
        SELECT 
            m.category_id,
            aff.target_cat_id,
            (1.000 - aff.link_prob) AS fail_prob,
            f.final_qty * (aff.min_ratio + RAND() * (aff.max_ratio - aff.min_ratio))
        FROM tmp_final_item_pool f
        INNER JOIN jsh_material m ON f.material_id = m.id
        INNER JOIN jsh_temp_category_affinity aff ON m.category_id = aff.source_cat_id;

        -- 2. 利用 ON DUPLICATE KEY UPDATE 算法合并多品类重叠贡献（失败率做乘积，数量做累加）
        INSERT INTO tmp_affinity_merged_pool (target_cat_id, combined_fail_prob, allocated_qty)
        SELECT 
            target_cat_id, 
            fail_prob, 
            FLOOR(computed_sub_qty)
        FROM tmp_affinity_contributions
        ON DUPLICATE KEY UPDATE 
            combined_fail_prob = combined_fail_prob * VALUES(combined_fail_prob),
            allocated_qty = allocated_qty + VALUES(allocated_qty);

        -- 3. 概率叠加判定，通过判定则将连带商品写入最终池，使用【只读影子池】确保完全读写分离
        SET @sub_current_cat = 0;
        SET @sub_row_num = 0;

        INSERT INTO tmp_final_item_pool (material_id, material_extend_id, commodity_unit, final_qty)
        SELECT 
            sub_m.id, 
            jme.id, 
            jme.commodity_unit,
            IFNULL(sub_m.allocated_qty, 5)
        FROM (
            SELECT 
                m.id, 
                m.category_id, 
                p.allocated_qty,
                @sub_row_num := IF(@sub_current_cat = m.category_id, @sub_row_num + 1, 1) AS rn,
                @sub_current_cat := m.category_id AS dummy
            FROM jsh_material m
            INNER JOIN tmp_affinity_merged_pool p ON m.category_id = p.target_cat_id
            WHERE RAND() <= (1.000000 - p.combined_fail_prob)
              AND m.tenant_id = p_tenant_id 
              AND m.delete_flag = '0'
            ORDER BY m.category_id, RAND()
        ) AS sub_m
        INNER JOIN jsh_material_extend jme ON sub_m.id = jme.material_id
        -- 【影子模式核心改动 1.3】：完全原汁原味死守您初始最直观、易懂的 NOT IN 子查询逻辑！
        -- 由于此处外层 INSERT 的写入对象是主池，而子查询读取的对象是物理上完全隔离的影子快照池，
        -- 读写彻底各行其道，完美绕过 MySQL 临时表重开锁死限制，性能与兼容性拉满。
        WHERE sub_m.rn <= 2 
          AND sub_m.id NOT IN (SELECT material_id FROM tmp_final_item_pool_shadow);

        -- --------------------------------------------------------------------------------
        -- 5. 步骤 5：数据持久化与事务提交
        -- --------------------------------------------------------------------------------
        -- 插入主表
        INSERT INTO jsh_depot_head (
            `type`, sub_type, default_number, `number`, 
            create_time, oper_time, creator, pay_type, `status`, tenant_id
        ) VALUES (
            '其它', '请购单', v_gen_number, v_gen_number, 
            v_random_oper_time, v_random_oper_time, v_creator_id, '现付', 0, p_tenant_id
        );
        
        SET v_new_id = LAST_INSERT_ID();

        -- 从最终池一次性批量清洗、倾倒数据至明细表
        INSERT INTO jsh_depot_item (header_id, material_id, material_extend_id, material_unit, oper_number, basic_number, tenant_id)
        SELECT v_new_id, material_id, material_extend_id, commodity_unit, final_qty, final_qty, p_tenant_id
        FROM tmp_final_item_pool;

        -- 更新审批状态并提交事务
        UPDATE jsh_depot_head SET `status` = v_status_approved WHERE id = v_new_id;
        COMMIT;
    END IF;

    -- 清理会话生命周期的所有临时内存表
    DROP TEMPORARY TABLE IF EXISTS tmp_selected_main_cats;
    DROP TEMPORARY TABLE IF EXISTS tmp_affinity_contributions;
    DROP TEMPORARY TABLE IF EXISTS tmp_affinity_merged_pool;
    DROP TEMPORARY TABLE IF EXISTS tmp_final_item_pool_shadow; -- 【清理影子】
    DROP TEMPORARY TABLE IF EXISTS tmp_final_item_pool;

    -- 6. 步骤 6：批量模式下的静默输出渠道
    -- 步骤 6：批量模式下的静默输出
IF IFNULL(@is_batch_mode, 0) = 1 THEN
    INSERT INTO jsh_mock_batch_log (code, message, new_id, number) 
    VALUES (v_ret_code, v_ret_msg, v_new_id, v_gen_number);
    ELSE
        SELECT v_ret_code AS code, v_ret_msg AS message, v_new_id AS new_id, v_gen_number AS number;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_refresh_org_pdf
BEGIN
    DECLARE v_total_w DECIMAL(24,6);
    SET @running_sum = 0;

    -- 1. 清空并计算基础权重
    TRUNCATE TABLE `jsh_temp_org_pdf`;

    INSERT INTO `jsh_temp_org_pdf` (org_id, weight, remark)
    SELECT 
        jo.id,
        ROUND(
            -- [维度A] 地域基础分
            (CASE 
                WHEN jo.org_no LIKE '%-HD-%' THEN 100 -- 华东
                WHEN jo.org_no LIKE '%-HN-%' THEN 95  -- 华南
                WHEN jo.org_no LIKE '%-HB-%' THEN 85  -- 华北
                WHEN jo.org_no LIKE '%-XN-%' THEN 80  -- 西南
                WHEN jo.org_no LIKE '%-HZ-%' THEN 75  -- 华中
                WHEN jo.org_no LIKE '%-XB-%' THEN 70  -- 西北
                WHEN jo.org_no LIKE '%-DB-%' THEN 60  -- 东北
                ELSE 50 
            END)
            * 
            -- [维度B] 密度加成：提取 org_no 前12位判定同区店数
            (1 + (SELECT COUNT(*) - 1 
                  FROM jsh_organization sub 
                  WHERE LEFT(sub.org_no, 12) = LEFT(jo.org_no, 12) 
                    AND sub.tenant_id = p_tenant_id
                    AND sub.id NOT IN (SELECT DISTINCT parent_id FROM jsh_organization WHERE parent_id IS NOT NULL)
            ) * 0.15) 
            *
            -- [维度C] 关键词溢价
            (CASE 
                WHEN jo.org_abr LIKE '%旗舰店%' THEN 2.5
                WHEN jo.org_abr LIKE '%机场%' THEN 2.2
                WHEN jo.org_abr LIKE '%站%' OR jo.org_abr LIKE '%枢纽%' THEN 2.0
                WHEN jo.org_abr LIKE '%中心%' OR jo.org_abr LIKE '%总部%' THEN 1.6
                WHEN jo.org_abr LIKE '%社区%' OR jo.org_abr LIKE '%生活%' THEN 0.8
                ELSE 1.0 
            END)
        ) AS final_weight,
        jo.org_abr
    FROM jsh_organization jo
    WHERE jo.tenant_id = p_tenant_id 
      AND jo.parent_id IS NOT NULL
      AND jo.id NOT IN (SELECT DISTINCT parent_id FROM jsh_organization WHERE parent_id IS NOT NULL);

    -- 2. 计算并更新 CDF 刻度
    SELECT SUM(weight) INTO v_total_w FROM `jsh_temp_org_pdf`;

    UPDATE `jsh_temp_org_pdf` t
    INNER JOIN (
        SELECT 
            org_id, 
            (@running_sum := @running_sum + weight) / v_total_w AS new_cdf
        FROM `jsh_temp_org_pdf`
        ORDER BY org_id ASC
    ) calculated ON t.org_id = calculated.org_id
    SET t.cdf_end = calculated.new_cdf;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_simulate_yearly_sales
BEGIN
    -- [1. 变量定义区]
    DECLARE v_month INT DEFAULT 1;               
    DECLARE v_curr_day DATE;                     
    DECLARE v_month_orders INT DEFAULT 0;        
    DECLARE v_day_orders INT DEFAULT 0;          
    DECLARE v_day_w INT DEFAULT 0;               
    DECLARE v_total_y_w INT DEFAULT 0;           
    DECLARE v_total_m_w INT DEFAULT 0;           
    DECLARE v_tenant_id BIGINT;                  
    DECLARE v_login_id BIGINT;                   
    DECLARE v_done INT DEFAULT FALSE;            
    DECLARE v_before_count INT DEFAULT 0;         
    DECLARE v_after_count INT DEFAULT 0;          

    -- [2. 安全校验与多租户空间环境重置]
    SELECT id, tenant_id INTO v_login_id, v_tenant_id 
    FROM jsh_user WHERE login_name = p_login_name AND delete_flag = '0' LIMIT 1;

    IF v_tenant_id IS NULL THEN 
        SELECT 'ERROR' AS 规划状态, '租户锁定失败，操作账号不存在' AS 错误提示;
    ELSE
        -- 清空上一次模拟残留的物理开单计划，腾出缓冲区
        -- 原先会导致自增回卷的错误代码
        -- TRUNCATE TABLE `jsh_temp_mock_plan`;
    	-- 修改为安全清空但保留最新自增编号断面的稳定代码
    	DELETE FROM `jsh_temp_mock_plan`;

        -- 动态刷新门店空间分布配置（重新精算地域基础分与同区密度加成系数）
        CALL proc_refresh_org_pdf(v_tenant_id);
        
        -- 初始化整年基础权重骨架日历表（内部已自带 TRUNCATE 机制，此处不再重复执行以防元数据锁死）
        CALL proc_init_yearly_weights(p_year);

        -- 【天级安全拦截线】：仅统计从当年 01-01 开始截止到【今天】(CURRENT_DATE) 的总绝对权重底池
        -- 分摊的数学基数完全建立在已过去的时间跨度内，大于今天的未来日期权重直接被作为 0 处理
        SELECT SUM(weight) INTO v_total_y_w 
        FROM `jsh_temp_day_pdf` 
        WHERE YEAR(day_date) = p_year AND day_date <= CURRENT_DATE(); 

        -- 建立高性能内存临时战报表，用于收集记录 12 个月的宏观分摊执行指标
        DROP TEMPORARY TABLE IF EXISTS tmp_yearly_dispatch_report;
        CREATE TEMPORARY TABLE tmp_yearly_dispatch_report (
            plan_month VARCHAR(20) PRIMARY KEY,
            expected_orders INT,
            actual_injected_orders INT,
            status_desc VARCHAR(50)
        ) ENGINE=MEMORY;

        -- [3. 核心分摊演进：跨越 12 个月的时间轴矩阵横向切分]
        WHILE v_month <= 12 DO
            
            -- 【时钟边界收拢】：月权重累加统计时，同样限制最大只能统计到【今天】为止的绝对权重
            SELECT SUM(weight) INTO v_total_m_w 
            FROM `jsh_temp_day_pdf` 
            WHERE YEAR(day_date) = p_year AND MONTH(day_date) = v_month AND day_date <= CURRENT_DATE();

            -- 宏观第一层按月精算：按照当前月权重占过去总权重的比例，计算出本月应承担的销售 KPI 目标
            IF v_total_y_w > 0 AND v_total_m_w IS NOT NULL THEN
                SET v_month_orders = ROUND((v_total_m_w / v_total_y_w) * p_total_annual_orders);
            ELSE
                SET v_month_orders = 0;
            END IF;

            -- 记录本次计划注入前，缓冲区的总行数，用于监控差额
            SELECT COUNT(*) INTO v_before_count FROM `jsh_temp_mock_plan`;

            -- 当月分摊指标单量确定大于 0 后，开启天级别微观拆解游标循环
            IF v_month_orders > 0 THEN
                BEGIN
                    -- 游标过滤逻辑：严格限定天级时间线最大只能前进到【今天】，未来天数被强制熔断
                    DECLARE cur_days CURSOR FOR 
                        SELECT day_date, weight 
                        FROM `jsh_temp_day_pdf` 
                        WHERE YEAR(day_date) = p_year AND MONTH(day_date) = v_month AND day_date <= CURRENT_DATE(); 
                    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

                    SET v_done = FALSE;
                    OPEN cur_days;
                    read_loop: LOOP
                        FETCH cur_days INTO v_curr_day, v_day_w;
                        IF v_done THEN LEAVE read_loop; END IF;

                        -- 微观第二层按天精算：计算当天应分配的绝对开单确定单量
                        SET v_day_orders = ROUND((v_day_w / v_total_m_w) * v_month_orders);
                        
                        -- 判定当天指标大于 0 时，正式下发任务调度指令
                        IF v_day_orders > 0 THEN
                            -- 【极其重要】：在下发给批量插入分发器前，必须强制将会话累加变量显式抹去重置
                            -- 彻底消灭因多天连续执行、会话变量不归零导致的离散 CDF 概率池断裂
                            SET @running_h_sum := 0;
                            
                            -- 调用经过集合化批量提速、无变量死锁的小时秒级分发器
                            CALL proc_worker_daily_distribution(
                                v_login_id, 
                                DATE_FORMAT(v_curr_day, '%Y-%m-%d'), 
                                v_day_orders
                            );
                        END IF;
                    END LOOP;
                    CLOSE cur_days;
                END;
            END IF;

            -- 统计注入后的缓冲区总行数
            SELECT COUNT(*) INTO v_after_count FROM `jsh_temp_mock_plan`;

            -- 封存当月的审计战报，对未来的越界月份提供清晰的“隔离熔断”文字说明
            INSERT INTO tmp_yearly_dispatch_report (plan_month, expected_orders, actual_injected_orders, status_desc)
            VALUES (
                CONCAT(p_year, '-', LPAD(v_month, 2, '0')),
                v_month_orders,
                GREATEST(0, (v_after_count - v_before_count)), -- 确保数学计数的绝对严谨性
                CASE WHEN v_month_orders = 0 THEN '越过当天日期边界，全盘时钟熔断隔离' ELSE '指令注入成功' END
            );

            SET v_month = v_month + 1;
        END WHILE;

        -- [4. 一次性大盘集中审计战报汇总投影输出]
        SELECT 
            plan_month AS 规划月份,
            expected_orders AS 预计分摊单量,
            actual_injected_orders AS 已注入计划表行数,
            status_desc AS 提示说明
        FROM tmp_yearly_dispatch_report 
        ORDER BY plan_month ASC;

        -- 回收内存资源，销毁月度大盘统计临时表
        DROP TEMPORARY TABLE IF EXISTS tmp_yearly_dispatch_report;
    END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_worker_daily_distribution
BEGIN
    -- [步骤1：声明多租户及流水控制起始变量]
    DECLARE v_tenant_id BIGINT;
    DECLARE v_start_lsck_val BIGINT DEFAULT 0;

    SET @running_h_sum := 0;

    -- 锁定并圈定当前管理员名下的多租户隔离空间范围
    SELECT tenant_id INTO v_tenant_id FROM jsh_user WHERE id = p_login_id LIMIT 1;

    -- 步骤 1.1：通过单次大事务排他锁加锁，拿到当前租户真实单据表最真实的最新最大自增断面数字
    SELECT IFNULL(MAX(CAST(SUBSTRING(default_number, 5) AS UNSIGNED)), 0) 
    INTO v_start_lsck_val FROM jsh_depot_head WHERE tenant_id = v_tenant_id FOR UPDATE;

    -- 步骤 1.2：【物理防回卷拦截】动态重置计划表的 AUTO_INCREMENT 物理起点，确保多次执行总台时其自增不断向高位推进
    SET @alter_sql = CONCAT('ALTER TABLE `jsh_temp_mock_plan` AUTO_INCREMENT = ', (v_start_lsck_val + 1));
    PREPARE stmt_alter FROM @alter_sql;
    EXECUTE stmt_alter;
    DEALLOCATE PREPARE stmt_alter;

    -- [步骤2：高速构建单列极轻量数字辅助表]
    DROP TEMPORARY TABLE IF EXISTS tmp_sequence;
    CREATE TEMPORARY TABLE tmp_sequence (seq_id INT PRIMARY KEY) ENGINE=InnoDB;
    
    SET @seq := 0;
    WHILE @seq < p_total_orders DO
        INSERT INTO tmp_sequence VALUES (@seq);
        SET @seq := @seq + 1;
    END WHILE;

    -- [步骤3：【原子步骤一】纯净集合化分发注入（彻底剥离 CROSS JOIN 与 HAVING 错配）]
    -- 在本条 SQL 中，单号列 `bill_no_str` 暂时留空（灌入空串），全力让物理主键 `id` 爆发出非空的自增唯一性
    INSERT INTO `jsh_temp_mock_plan` (user_id, mock_timestamp_str, bill_no_str, execute_status)
    SELECT 
        -- 核心防御：将原 HAVING 中的过滤逻辑直接作为标量子查询的 WHERE 内部前提，强制过滤无员工的脏店
        rand_tbl.user_id,
        rand_tbl.mock_time,
        '' AS bill_no_str, -- 暂时寄存空串，等待下一步主键回写
        0 AS execute_status
    FROM (
        SELECT 
            (SELECT emp.user_id 
             FROM jsh_orga_user_rel emp 
             WHERE emp.orga_id = o.org_id AND emp.delete_flag = '0'
             ORDER BY RAND() LIMIT 1) AS user_id,
            
            CONCAT(
                p_target_date, ' ', 
                LPAD((SELECT h.hour_val 
                      FROM (
                          SELECT hour_val, (@running_h_sum := @running_h_sum + weight) AS h_cdf 
                          FROM jsh_temp_hour_pdf 
                          ORDER BY hour_val ASC
                      ) h 
                      WHERE h.h_cdf >= RAND() * (SELECT SUM(weight) FROM jsh_temp_hour_pdf) 
                      ORDER BY h.h_cdf ASC LIMIT 1), 2, '0'), ':', 
                LPAD(FLOOR(RAND() * 60), 2, '0'), ':', 
                LPAD(FLOOR(RAND() * 60), 2, '0')
            ) AS mock_time
        FROM tmp_sequence seq
        -- 步骤 3.A：基于 Org PDF 的 cdf_end 分布直接进行单表配额切分
        CROSS JOIN (
            SELECT o.org_id 
            FROM jsh_temp_org_pdf o 
            WHERE o.cdf_end >= RAND() 
            ORDER BY o.cdf_end ASC LIMIT 1
        ) o
    ) rand_tbl
    -- 在子表外部执行纯净过滤，彻底拔除 HAVING 算子，防止优化器产生行指针回卷缓存
    WHERE rand_tbl.user_id IS NOT NULL;


    -- [步骤4：【原子步骤二】主键物理反写（无任何变量与外部表参与，实现 100% 物理防重）]
    -- 核心提速与防重线：在数据落盘后，利用已经锁定的物理聚簇主键 id 实时回写单号，在底层机制上彻底根除任何串单现象
    UPDATE `jsh_temp_mock_plan`
    SET bill_no_str = CONCAT('LSCK', LPAD(id, 11, '0'))
    WHERE execute_status = 0 
      AND bill_no_str = '';

    -- [步骤5：内存级临时表及时解构释放]
    DROP TEMPORARY TABLE IF EXISTS tmp_sequence;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.sp_fill_biz_bill_item_fact_new_with_progress
BEGIN
  DECLARE v_job_id BIGINT;
  DECLARE v_min BIGINT;
  DECLARE v_max BIGINT;
  DECLARE v_from BIGINT;
  DECLARE v_to BIGINT;
  DECLARE v_total BIGINT;
  DECLARE v_done BIGINT DEFAULT 0;
  DECLARE v_aff INT DEFAULT 0;

  IF p_batch_size IS NULL OR p_batch_size <= 0 THEN
    SET p_batch_size = 1000;
  END IF;

  SET v_job_id = UNIX_TIMESTAMP(NOW(3))*1000 + FLOOR(RAND()*1000);

  SELECT MIN(di.id), MAX(di.id), COUNT(*)
    INTO v_min, v_max, v_total
  FROM jsh_depot_item di
  JOIN jsh_depot_head dh
    ON dh.id = di.header_id
   AND dh.tenant_id = di.tenant_id
   AND dh.delete_flag = '0'
  WHERE di.delete_flag = '0'
    AND (p_tenant_id IS NULL OR di.tenant_id = p_tenant_id)
    AND dh.sub_type IN ('请购单','采购订单','采购','采购退货','销售订单','销售','销售退货','零售','零售退货','调拨');

  IF v_total IS NULL OR v_total = 0 THEN
    INSERT INTO biz_sync_progress(jobId, tenantId, status, msg, totalRows, doneRows, pct)
    VALUES (v_job_id, p_tenant_id, 'DONE', 'NO DATA', 0, 0, 100.00);
    SELECT v_job_id AS jobId, 'NO DATA' AS result;
  ELSE
    INSERT INTO biz_sync_progress(jobId, tenantId, status, msg, totalRows, doneRows, pct, batchFromId, batchToId)
    VALUES (v_job_id, p_tenant_id, 'RUNNING', 'START', v_total, 0, 0.00, v_min, v_max);

    SET v_from = v_min;

    WHILE v_from <= v_max DO
      SET v_to = LEAST(v_from + p_batch_size - 1, v_max);

      INSERT INTO biz_bill_item_fact_new (
        tenantId, sourceOrderId, sourceOrderItemId, sourceType, sourceSubType, businessDate, creator,
        supplierId, customerId, memberId, storeId, productId, warehouseId, outWarehouseId, inWarehouseId,
        quantity, amount, inventoryDirection, salesDirection,
        sourceOrderNo, purchaseOrderLinkNo, purchaseApplyLinkNo
      )
      SELECT
        di.tenant_id,
        dh.id,
        di.id,
        dh.type,
        dh.sub_type,
        dh.oper_time,
        dh.creator,
        CASE
          WHEN s.type='供应商' OR dh.sub_type IN ('请购单','采购订单','采购','采购退货') THEN dh.organ_id
          ELSE NULL
        END,
        CASE
          WHEN s.type='客户' OR dh.sub_type IN ('销售订单','销售','销售退货') THEN dh.organ_id
          ELSE NULL
        END,
        CASE
          WHEN s.type='会员' OR dh.sub_type IN ('零售','零售退货') THEN dh.organ_id
          ELSE NULL
        END,
        ou.orga_id,
        di.material_id,
        di.depot_id,
        CASE WHEN dh.sub_type='调拨' THEN di.depot_id ELSE NULL END,
        CASE WHEN dh.sub_type='调拨' THEN di.another_depot_id ELSE NULL END,
        di.oper_number,
        COALESCE(di.tax_last_money, di.all_price, 0),
        CASE
          WHEN dh.type='入库' AND dh.sub_type IN ('采购','销售退货','零售退货') THEN 1
          WHEN dh.type='出库' AND dh.sub_type IN ('采购退货','销售','零售') THEN -1
          ELSE 0
        END,
        CASE
          WHEN dh.type='出库' AND dh.sub_type IN ('销售','零售') THEN 1
          WHEN dh.type='入库' AND dh.sub_type IN ('销售退货','零售退货') THEN -1
          ELSE 0
        END,
        dh.number,
        CASE WHEN dh.sub_type='采购' THEN dh.link_number ELSE NULL END,
        CASE
          WHEN dh.sub_type='采购订单' THEN dh.link_apply
          WHEN dh.sub_type='采购' THEN po.link_apply
          ELSE NULL
        END
      FROM jsh_depot_item di
      JOIN jsh_depot_head dh
        ON dh.id = di.header_id
       AND dh.tenant_id = di.tenant_id
       AND dh.delete_flag = '0'
      LEFT JOIN jsh_depot_head po
        ON po.number = dh.link_number
       AND po.tenant_id = dh.tenant_id
       AND po.delete_flag = '0'
       AND po.sub_type = '采购订单'
      LEFT JOIN jsh_supplier s
        ON s.id = dh.organ_id
       AND s.tenant_id = dh.tenant_id
       AND s.delete_flag = '0'
      LEFT JOIN (
        SELECT tenant_id, user_id, MIN(orga_id) AS orga_id
        FROM jsh_orga_user_rel
        WHERE delete_flag = '0'
        GROUP BY tenant_id, user_id
      ) ou
        ON ou.tenant_id = dh.tenant_id
       AND ou.user_id = dh.creator
      WHERE di.delete_flag = '0'
        AND di.id BETWEEN v_from AND v_to
        AND (p_tenant_id IS NULL OR di.tenant_id = p_tenant_id)
        AND dh.sub_type IN ('请购单','采购订单','采购','采购退货','销售订单','销售','销售退货','零售','零售退货','调拨')
      ON DUPLICATE KEY UPDATE
        sourceOrderId = VALUES(sourceOrderId),
        sourceType = VALUES(sourceType),
        sourceSubType = VALUES(sourceSubType),
        businessDate = VALUES(businessDate),
        creator = VALUES(creator),
        supplierId = VALUES(supplierId),
        customerId = VALUES(customerId),
        memberId = VALUES(memberId),
        storeId = VALUES(storeId),
        productId = VALUES(productId),
        warehouseId = VALUES(warehouseId),
        outWarehouseId = VALUES(outWarehouseId),
        inWarehouseId = VALUES(inWarehouseId),
        quantity = VALUES(quantity),
        amount = VALUES(amount),
        inventoryDirection = VALUES(inventoryDirection),
        salesDirection = VALUES(salesDirection),
        sourceOrderNo = VALUES(sourceOrderNo),
        purchaseOrderLinkNo = VALUES(purchaseOrderLinkNo),
        purchaseApplyLinkNo = VALUES(purchaseApplyLinkNo),
        updatedAt = NOW();

      SET v_aff = ROW_COUNT();
      SET v_done = LEAST(v_done + p_batch_size, v_total);

      INSERT INTO biz_sync_progress(
        jobId, tenantId, batchFromId, batchToId, affectedRows,
        doneRows, totalRows, pct, status, msg
      )
      VALUES(
        v_job_id, p_tenant_id, v_from, v_to, v_aff,
        v_done, v_total,
        ROUND(v_done * 100.0 / NULLIF(v_total,0), 2),
        'RUNNING', 'BATCH_DONE'
      );

      SET v_from = v_to + 1;
    END WHILE;

    INSERT INTO biz_sync_progress(jobId, tenantId, doneRows, totalRows, pct, status, msg)
    VALUES(v_job_id, p_tenant_id, v_total, v_total, 100.00, 'DONE', 'FINISHED');

    SELECT v_job_id AS jobId, 'DONE' AS result;
  END IF;
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.sp_sync_retail_out_fact_batch
BEGIN
    -- 1. 批次控制与状态标志变量
    DECLARE v_batch_id VARCHAR(50);
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_batch_size INT DEFAULT 1000; -- 核心控制: 每 1000 条刷盘一次
    DECLARE v_current_count INT DEFAULT 0;  -- 内存缓存计数器
    
    -- 2. 承接游标提取源数据的临时变量
    DECLARE v_dh_id, v_di_id, v_creator, v_organ_id, v_depot_id BIGINT;
    DECLARE v_oper_time DATETIME;
    DECLARE v_type, v_sub_type VARCHAR(50);
    DECLARE v_quantity, v_amount DECIMAL(24,6);
    DECLARE v_store_id BIGINT;
    
    -- 3. 结果汇总统计指标
    DECLARE v_total_processed INT DEFAULT 0;
    DECLARE v_err_count INT DEFAULT 0;

    -- 4. 声明游标: 只抓取未删除、类型匹配、租户匹配、时间闭区间内的出库零售单
    DECLARE cur_source CURSOR FOR 
        SELECT 
            dh.id, 
            di.id, 
            dh.type, 
            dh.sub_type, 
            dh.oper_time, 
            dh.creator, 
            dh.organ_id,
            di.material_id, 
            di.depot_id, 
            di.oper_number, 
            COALESCE(di.tax_last_money, di.all_price, 0)
        FROM jsh_depot_head dh
        INNER JOIN jsh_depot_item di ON dh.id = di.header_id
        WHERE dh.tenant_id = p_tenant_id
          AND dh.oper_time >= p_start_time -- 闭区间开始
          AND dh.oper_time <= p_stop_time  -- 闭区间结束
          AND dh.type = '出库'
          AND dh.sub_type = '零售'
          AND dh.delete_flag = '0'
          AND di.delete_flag = '0';

    -- 游标遍历结束后的状态置位处理器
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- 初始化并组装同步批次流水号
    SET v_batch_id = CONCAT(
        'BATCH_PC_', 
        DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), 
        '_', 
        FLOOR(RAND()*1000)
    );

    -- 5. 创建基于内存引擎的临时表缓冲区
    CREATE TEMPORARY TABLE IF NOT EXISTS `tmp_batch_buffer` (
        `tenantId` bigint, `sourceOrderId` bigint, `sourceOrderItemId` bigint,
        `sourceType` varchar(32), `sourceSubType` varchar(32), `businessDate` datetime,
        `creator` bigint, `memberId` bigint, `storeId` bigint, `productId` bigint,
        `warehouseId` bigint, `quantity` decimal(24,6), `amount` decimal(24,6)
    ) ENGINE=MEMORY;
    
    -- 清空历史残余以维持数据纯净度
    TRUNCATE TABLE tmp_batch_buffer;

    -- 开启游标循环
    OPEN cur_source;
    
    read_loop: LOOP
        FETCH cur_source INTO 
            v_dh_id, v_di_id, v_type, v_sub_type, v_oper_time, v_creator, v_organ_id,
            v_depot_id, v_store_id, v_quantity, v_amount;
            
        IF v_done THEN
            LEAVE read_loop;
        END IF;

        -- 动态单条安全推导 StoreId (防止跨表 JOIN 导致一对多引发数据行翻倍)
        SET v_store_id = NULL;
        SELECT rel.orga_id INTO v_store_id 
        FROM jsh_orga_user_rel rel 
        WHERE rel.user_id = v_creator 
          AND rel.delete_flag = '0' 
        LIMIT 1;

        -- 将格式化完毕、推导完毕的数据先压入内存缓存
        INSERT INTO tmp_batch_buffer VALUES (
            p_tenant_id, v_dh_id, v_di_id, v_type, v_sub_type, v_oper_time, 
            v_creator, v_organ_id, v_store_id, v_depot_id, v_store_id, v_quantity, v_amount
        );
        
        SET v_current_count = v_current_count + 1;
        SET v_total_processed = v_total_processed + 1;

        -- 校验计数器：一旦在内存中积攒够 1000 条，执行大批量冲刷写盘
        IF v_current_count >= v_batch_size THEN
            CALL _internal_flush_buffer(v_batch_id, p_tenant_id, v_err_count);
            SET v_current_count = 0; -- 归零内存计数器
        END IF;

    END LOOP;
    
    CLOSE cur_source;

    -- 6. 零头补漏冲刷：循环结束，缓冲区内若存有零头，补发最后一次冲刷
    IF v_current_count > 0 THEN
        CALL _internal_flush_buffer(v_batch_id, p_tenant_id, v_err_count);
    END IF;

    -- 彻底销毁释放会话级内存临时表
    DROP TEMPORARY TABLE IF EXISTS tmp_batch_buffer;

    -- 7. 直接向调用终端呈现标准报表行数据集
    SELECT 
        'SUCCESS' AS `res_code`,
        v_batch_id AS `sync_batch_id`,
        CONCAT('分批同步完成！总扫描条数: ', v_total_processed) AS `res_msg`,
        (v_total_processed - v_err_count) AS `success_rows`,
        v_err_count AS `failed_rows`;

END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01._internal_flush_buffer
BEGIN
    -- 定义 SQL 异常状态捕捉变量
    DECLARE v_sql_state CHAR(5) DEFAULT '00000';
    DECLARE v_sql_errno INT DEFAULT 0;
    DECLARE v_err_msg VARCHAR(255) DEFAULT '';
    
    -- 声明批量写入阶段的异常处理器：若失败，记录错误状态，但不阻断执行流
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            v_sql_state = RETURNED_SQLSTATE, 
            v_sql_errno = MYSQL_ERRNO, 
            v_err_msg = MESSAGE_TEXT;
    END;

    -- ---------------------------------------------------------------------
    -- 核心逻辑A: 尝试一脚油门轰入事实表 (BATCH INSERT 模式)
    -- ---------------------------------------------------------------------
    START TRANSACTION;
        INSERT INTO biz_bill_item_fact (
            tenantId, sourceOrderId, sourceOrderItemId, sourceType, 
            sourceSubType, businessDate, creator, supplierId, 
            customerId, memberId, storeId, productId, 
            warehouseId, outWarehouseId, inWarehouseId, quantity, 
            amount, inventoryDirection, salesDirection
        )
        SELECT 
            tenantId, sourceOrderId, sourceOrderItemId, sourceType, 
            sourceSubType, businessDate, creator, NULL, 
            NULL, memberId, storeId, productId, 
            warehouseId, warehouseId, NULL, quantity, 
            amount, -1, 1
        FROM tmp_batch_buffer
        ON DUPLICATE KEY UPDATE 
            businessDate = VALUES(businessDate),
            quantity = VALUES(quantity),
            amount = VALUES(amount),
            updatedAt = NOW();
    COMMIT;

    -- ---------------------------------------------------------------------
    -- 核心逻辑B: 分支判断与容错降级
    -- ---------------------------------------------------------------------
    IF v_sql_state != '00000' THEN
        -- 说明这 1000 条里有数据坏包，执行原子级单条拆分遍历
        BEGIN
            DECLARE v_sub_done INT DEFAULT FALSE;
            DECLARE v_dh_id, v_di_id BIGINT;
            
            -- 建立游标重新读取内存缓冲区里的这批临时数据
            DECLARE cur_tmp CURSOR FOR 
                SELECT sourceOrderId, sourceOrderItemId FROM tmp_batch_buffer;
            DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_sub_done = TRUE;
            
            OPEN cur_tmp;
            tmp_loop: LOOP
                FETCH cur_tmp INTO v_dh_id, v_di_id;
                IF v_sub_done THEN 
                    LEAVE tmp_loop; 
                END IF;
                
                -- 在单条尝试的局部块中运行，隔离单条错误
                BEGIN
                    DECLARE v_single_state CHAR(5) DEFAULT '00000';
                    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN
                        GET DIAGNOSTICS CONDITION 1 v_single_state = RETURNED_SQLSTATE;
                        SET p_err_count = p_err_count + 1; -- 失败计数器累加
                    END;
                    
                    START TRANSACTION;
                        INSERT INTO biz_bill_item_fact (
                            tenantId, sourceOrderId, sourceOrderItemId, sourceType, 
                            sourceSubType, businessDate, creator, memberId, 
                            storeId, productId, warehouseId, outWarehouseId, 
                            quantity, amount, inventoryDirection, salesDirection
                        ) 
                        SELECT 
                            tenantId, sourceOrderId, sourceOrderItemId, sourceType, 
                            sourceSubType, businessDate, creator, memberId, 
                            storeId, productId, warehouseId, warehouseId, 
                            quantity, amount, -1, 1
                        FROM tmp_batch_buffer 
                        WHERE sourceOrderItemId = v_di_id
                        ON DUPLICATE KEY UPDATE 
                            businessDate = VALUES(businessDate), 
                            quantity = VALUES(quantity), 
                            amount = VALUES(amount), 
                            updatedAt = NOW();
                    COMMIT;
                    
                    -- 单条处理完毕后，根据状态精准写入对账日志
                    IF v_single_state != '00000' THEN
                        INSERT INTO log_bill_sync_detail (
                            syncBatchId, tenantId, sourceOrderId, 
                            sourceOrderItemId, syncStatus, errorMsg
                        ) VALUES (
                            p_batch_id, p_tenant_id, v_dh_id, 
                            v_di_id, 'FAILED', '批量降级拦截: 数据格式或长度受损'
                        );
                    ELSE
                        INSERT INTO log_bill_sync_detail (
                            syncBatchId, tenantId, sourceOrderId, 
                            sourceOrderItemId, syncStatus
                        ) VALUES (
                            p_batch_id, p_tenant_id, v_dh_id, 
                            v_di_id, 'BATCH_DOWNGRADE_OK'
                        );
                    END IF;
                END;
            END LOOP;
            CLOSE cur_tmp;
        END;
    ELSE
        -- 批量插入完全无错，将这 1000 条明细的状态一次性快捷记录到中间表
        INSERT INTO log_bill_sync_detail (
            syncBatchId, tenantId, sourceOrderId, 
            sourceOrderItemId, syncStatus
        )
        SELECT 
            p_batch_id, p_tenant_id, sourceOrderId, 
            sourceOrderItemId, 'BATCH_SUCCESS'
        FROM tmp_batch_buffer;
    END IF;

    -- 冲刷完毕后清空内存缓冲区，准备迎接下一批 1000 条数据入驻
    TRUNCATE TABLE tmp_batch_buffer;
END
-- relation-detector-fixture-end

