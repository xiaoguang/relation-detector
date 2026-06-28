-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_massive_data
CREATE PROCEDURE sp_generate_massive_data()
BEGIN
    DECLARE v_start TIMESTAMP DEFAULT NOW();

    -- 1. 清理并生成组织架构
    CALL sp_gen_org_structure();

    -- 2. 生成员工
    CALL sp_gen_employees();

    -- 3. 生成商品
    CALL sp_gen_products();

    -- 4. 生成供应商
    CALL sp_gen_suppliers();

    -- 5. 生成客户
    CALL sp_gen_customers();

    -- 6. 生成批号和库存
    CALL sp_gen_batches_inventory();

    -- 7. 生成采购数据
    CALL sp_gen_purchase_orders();

    -- 8. 生成销售数据
    CALL sp_gen_sales_orders();

    -- 9. 生成考勤
    CALL sp_gen_attendance();

    -- 10. 生成财务数据
    CALL sp_gen_finance_data();

    -- 11. 生成其他数据
    CALL sp_gen_other_data();

    SELECT CONCAT('全部数据生成完成! 耗时: ', TIMESTAMPDIFF(SECOND, v_start, NOW()), ' 秒') AS result;
    SELECT CONCAT('员工: ', (SELECT COUNT(*) FROM employees), ' 人') AS stat
    UNION ALL SELECT CONCAT('门店: ', (SELECT COUNT(*) FROM warehouses), ' 个')
    UNION ALL SELECT CONCAT('商品: ', (SELECT COUNT(*) FROM products), ' 个')
    UNION ALL SELECT CONCAT('批号: ', (SELECT COUNT(*) FROM product_batches), ' 个')
    UNION ALL SELECT CONCAT('销售单: ', (SELECT COUNT(*) FROM sales_orders), ' 单')
    UNION ALL SELECT CONCAT('采购单: ', (SELECT COUNT(*) FROM purchase_orders), ' 单')
    UNION ALL SELECT CONCAT('客户: ', (SELECT COUNT(*) FROM customers), ' 个')
    UNION ALL SELECT CONCAT('供应商: ', (SELECT COUNT(*) FROM suppliers), ' 个');
END//


-- ============================================================
-- 1. 生成组织架构: 总公司 -> 大区 -> 城市 -> 门店(100+)
-- 层级: 1总公司 -> 7大区 -> 每个大区3-5城市 -> 每个城市3-5门店
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_org_structure
CREATE PROCEDURE sp_gen_org_structure()
BEGIN
    DECLARE v_region_id BIGINT;
    DECLARE v_city_id BIGINT;
    DECLARE v_region_idx INT DEFAULT 0;
    DECLARE v_city_idx INT;
    DECLARE v_store_idx INT;
    DECLARE v_region_name VARCHAR(50);
    DECLARE v_city_name VARCHAR(50);
    DECLARE v_store_name VARCHAR(100);
    DECLARE v_store_code VARCHAR(20);
    DECLARE v_cities_per_region INT;
    DECLARE v_stores_per_city INT;

    -- 清理
    DELETE FROM inventory_transactions;
    DELETE FROM inventory;
    DELETE FROM sales_return_items;
    DELETE FROM sales_returns;
    DELETE FROM sales_order_items;
    DELETE FROM sales_orders;
    DELETE FROM purchase_receipt_items;
    DELETE FROM purchase_receipts;
    DELETE FROM purchase_order_items;
    DELETE FROM purchase_orders;
    DELETE FROM purchase_requisition_items;
    DELETE FROM purchase_requisitions;
    DELETE FROM product_batches;
    DELETE FROM supplier_products;
    DELETE FROM products;
    DELETE FROM product_categories;
    DELETE FROM shipments;
    DELETE FROM shipping_tracks;
    DELETE FROM attendance;
    DELETE FROM leave_records;
    DELETE FROM employee_salary_log;
    DELETE FROM employee_roles;
    DELETE FROM employees;
    DELETE FROM positions;
    DELETE FROM departments WHERE parent_id IS NOT NULL OR id > 7;
    DELETE FROM warehouses;
    DELETE FROM customers;
    DELETE FROM suppliers;
    DELETE FROM serial_numbers;
    DELETE FROM serial_number_logs;
    DELETE FROM consignment_inventory;
    DELETE FROM consignment_consumptions;
    DELETE FROM ar_aging_snapshots;
    DELETE FROM ap_aging_snapshots;
    DELETE FROM contracts;
    DELETE FROM contract_milestones;
    DELETE FROM damage_reports;
    DELETE FROM damage_report_items;
    DELETE FROM purchase_returns;
    DELETE FROM purchase_return_items;
    DELETE FROM cashier_journals;
    DELETE FROM voucher_items;
    DELETE FROM vouchers;
    DELETE FROM tax_invoices;
    DELETE FROM tax_filings;
    DELETE FROM reconciliations;
    DELETE FROM reconciliation_items;
    DELETE FROM settlements;
    DELETE FROM settlement_items;
    DELETE FROM salary_payments;
    DELETE FROM performance_reviews;
    DELETE FROM inspection_reports;
    DELETE FROM price_change_logs;
    DELETE FROM projects;
    DELETE FROM project_costs;
    DELETE FROM approval_instances;
    DELETE FROM approval_records;
    DELETE FROM audit_log;

    -- 总公司
    INSERT INTO departments (id, parent_id, name, code, budget, headcount_plan) VALUES
    (1, NULL, '集团公司总部', 'HQ', 50000000.00, 100);

    -- 7大区
    INSERT INTO departments (id, parent_id, name, code, budget, headcount_plan) VALUES
    (2, 1, '华东大区', 'REG_EAST', 8000000.00, 200),
    (3, 1, '华南大区', 'REG_SOUTH', 7000000.00, 180),
    (4, 1, '华北大区', 'REG_NORTH', 6000000.00, 150),
    (5, 1, '西南大区', 'REG_SW', 5000000.00, 130),
    (6, 1, '西北大区', 'REG_NW', 4000000.00, 100),
    (7, 1, '东北大区', 'REG_NE', 4500000.00, 110),
    (8, 1, '华中大区', 'REG_CENTRAL', 5500000.00, 140);

    -- 总部职能
    INSERT INTO departments (id, parent_id, name, code, budget, headcount_plan) VALUES
    (9, 1, '财务中心', 'FIN', 3000000.00, 50),
    (10, 1, '人力资源中心', 'HR', 2000000.00, 30),
    (11, 1, '信息技术中心', 'IT', 5000000.00, 60),
    (12, 1, '采购中心', 'PURCH', 3000000.00, 40),
    (13, 1, '供应链中心', 'SCM', 4000000.00, 50),
    (14, 1, '市场营销中心', 'MKT', 3500000.00, 45);

    -- 城市和门店
    SET v_region_idx = 0;
    WHILE v_region_idx < 7 DO
        CASE v_region_idx
            WHEN 0 THEN SET v_region_name = '华东';
            WHEN 1 THEN SET v_region_name = '华南';
            WHEN 2 THEN SET v_region_name = '华北';
            WHEN 3 THEN SET v_region_name = '西南';
            WHEN 4 THEN SET v_region_name = '西北';
            WHEN 5 THEN SET v_region_name = '东北';
            WHEN 6 THEN SET v_region_name = '华中';
        END CASE;

        SET v_cities_per_region = FLOOR(RAND() * 3) + 3;

        SET v_city_idx = 0;
        WHILE v_city_idx < v_cities_per_region DO
            CASE v_region_idx
                WHEN 0 THEN SET v_city_name = ELT(v_city_idx + 1, '上海', '南京', '杭州', '苏州', '宁波');
                WHEN 1 THEN SET v_city_name = ELT(v_city_idx + 1, '广州', '深圳', '东莞', '佛山', '厦门');
                WHEN 2 THEN SET v_city_name = ELT(v_city_idx + 1, '北京', '天津', '石家庄', '太原', '济南');
                WHEN 3 THEN SET v_city_name = ELT(v_city_idx + 1, '成都', '重庆', '昆明', '贵阳', '拉萨');
                WHEN 4 THEN SET v_city_name = ELT(v_city_idx + 1, '西安', '兰州', '西宁', '银川', '乌鲁木齐');
                WHEN 5 THEN SET v_city_name = ELT(v_city_idx + 1, '沈阳', '大连', '长春', '哈尔滨', '齐齐哈尔');
                WHEN 6 THEN SET v_city_name = ELT(v_city_idx + 1, '武汉', '长沙', '郑州', '合肥', '南昌');
            END CASE;

            SET v_stores_per_city = FLOOR(RAND() * 3) + 3;
            SET v_store_idx = 0;
            WHILE v_store_idx < v_stores_per_city DO
                SET v_store_name = CONCAT(v_city_name, ELT(v_store_idx + 1, '旗舰店', '中心店', '社区店', '奥莱店', '精选店'));
                SET v_store_code = CONCAT('WH-', LEFT(v_city_name, 2), '-', LPAD(v_store_idx + 1, 2, '0'));

                INSERT INTO warehouses (name, code, address, province, city, district, latitude, longitude, type, capacity_m3, status)
                VALUES (v_store_name, v_store_code,
                    CONCAT(v_city_name, '市', ELT(FLOOR(RAND()*5)+1, '朝阳区', '海淀区', '西城区', '东城区', '新区'), '物流大道', v_store_idx + 1, '号'),
                    v_region_name, v_city_name, ELT(FLOOR(RAND()*5)+1, '朝阳区', '海淀区', '西城区', '东城区', '新区'),
                    -- 城市经纬度(近似)
                    CASE v_city_name
                        WHEN '上海' THEN 31.2304 WHEN '南京' THEN 32.0603 WHEN '杭州' THEN 30.2741
                        WHEN '苏州' THEN 31.2990 WHEN '宁波' THEN 29.8683
                        WHEN '广州' THEN 23.1291 WHEN '深圳' THEN 22.5431 WHEN '东莞' THEN 23.0208
                        WHEN '佛山' THEN 23.0219 WHEN '厦门' THEN 24.4798
                        WHEN '北京' THEN 39.9042 WHEN '天津' THEN 39.3434 WHEN '石家庄' THEN 38.0428
                        WHEN '太原' THEN 37.8706 WHEN '济南' THEN 36.6512
                        WHEN '成都' THEN 30.5728 WHEN '重庆' THEN 29.4316 WHEN '昆明' THEN 25.0389
                        WHEN '贵阳' THEN 26.6470 WHEN '拉萨' THEN 29.6500
                        WHEN '西安' THEN 34.3416 WHEN '兰州' THEN 36.0611 WHEN '西宁' THEN 36.6171
                        WHEN '银川' THEN 38.4872 WHEN '乌鲁木齐' THEN 43.8256
                        WHEN '沈阳' THEN 41.8057 WHEN '大连' THEN 38.9140 WHEN '长春' THEN 43.8171
                        WHEN '哈尔滨' THEN 45.8038 WHEN '齐齐哈尔' THEN 47.3543
                        WHEN '武汉' THEN 30.5928 WHEN '长沙' THEN 28.2282 WHEN '郑州' THEN 34.7466
                        WHEN '合肥' THEN 31.8206 WHEN '南昌' THEN 28.6820
                        ELSE 30.0000
                    END + (RAND() - 0.5) * 0.1,
                    CASE v_city_name
                        WHEN '上海' THEN 121.4737 WHEN '南京' THEN 118.7969 WHEN '杭州' THEN 120.1551
                        WHEN '苏州' THEN 120.5853 WHEN '宁波' THEN 121.5440
                        WHEN '广州' THEN 113.2644 WHEN '深圳' THEN 114.0579 WHEN '东莞' THEN 113.7518
                        WHEN '佛山' THEN 113.1219 WHEN '厦门' THEN 118.0894
                        WHEN '北京' THEN 116.4074 WHEN '天津' THEN 117.1902 WHEN '石家庄' THEN 114.5149
                        WHEN '太原' THEN 112.5489 WHEN '济南' THEN 117.1205
                        WHEN '成都' THEN 104.0665 WHEN '重庆' THEN 106.5516 WHEN '昆明' THEN 102.7123
                        WHEN '贵阳' THEN 106.6302 WHEN '拉萨' THEN 91.1400
                        WHEN '西安' THEN 108.9398 WHEN '兰州' THEN 103.8343 WHEN '西宁' THEN 101.7782
                        WHEN '银川' THEN 106.2309 WHEN '乌鲁木齐' THEN 87.6168
                        WHEN '沈阳' THEN 123.4315 WHEN '大连' THEN 121.6186 WHEN '长春' THEN 125.3235
                        WHEN '哈尔滨' THEN 126.5350 WHEN '齐齐哈尔' THEN 123.9180
                        WHEN '武汉' THEN 114.3054 WHEN '长沙' THEN 112.9388 WHEN '郑州' THEN 113.6254
                        WHEN '合肥' THEN 117.2272 WHEN '南昌' THEN 115.8579
                        ELSE 120.0000
                    END + (RAND() - 0.5) * 0.1,
                    ELT(FLOOR(RAND()*3)+1, 'main', 'transit', 'main'),
                    ROUND(1000 + RAND() * 5000, 3), 'active');

                SET v_store_idx = v_store_idx + 1;
            END WHILE;

            SET v_city_idx = v_city_idx + 1;
        END WHILE;

        SET v_region_idx = v_region_idx + 1;
    END WHILE;

    -- 更新仓库管理器(暂时置NULL, 员工生成后更新)
    SELECT CONCAT('组织架构生成: 门店', COUNT(*), '个') FROM warehouses;

    -- 生成职位体系
    INSERT INTO positions (id, department_id, name, code, level, min_salary, max_salary, headcount) VALUES
    (1, 1, '总经理', 'CEO', 15, 50000, 150000, 1),
    (2, 1, '副总经理', 'VP', 13, 35000, 80000, 3);
    -- 为每个部门生成职位
    INSERT INTO positions (department_id, name, code, level, min_salary, max_salary, headcount)
    SELECT d.id, CONCAT(d.name, '经理'), CONCAT('MGR_', d.code), 9, 12000, 30000, 1
    FROM departments d WHERE d.id > 1;
    INSERT INTO positions (department_id, name, code, level, min_salary, max_salary, headcount)
    SELECT d.id, CONCAT(d.name, '主管'), CONCAT('SUP_', d.code), 6, 8000, 18000, 3
    FROM departments d WHERE d.id > 1;
    INSERT INTO positions (department_id, name, code, level, min_salary, max_salary, headcount)
    SELECT d.id, CONCAT(d.name, '专员'), CONCAT('STAFF_', d.code), 3, 4000, 10000, 10
    FROM departments d WHERE d.id > 1;
END//


-- ============================================================
-- 2. 生成员工 (500+人)
-- 每个门店: 1店长 + 2-3销售 + 1-2仓管
-- 总部: 各个职能
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_employees
CREATE PROCEDURE sp_gen_employees()
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_wh_id BIGINT;
    DECLARE v_wh_name VARCHAR(100);
    DECLARE v_emp_count INT DEFAULT 0;
    DECLARE v_emp_no VARCHAR(20);
    DECLARE v_emp_name VARCHAR(50);
    DECLARE v_salary DECIMAL(12,2);
    DECLARE v_emp_id BIGINT;
    DECLARE v_dept_id BIGINT;
    DECLARE v_pos_id BIGINT;
    DECLARE v_mgr_id BIGINT;
    DECLARE v_city VARCHAR(50);

    DECLARE surnames VARCHAR(500) DEFAULT '张李王陈刘杨赵黄周吴徐孙马朱胡郭林何高郑梁罗宋谢唐韩冯邓曹许彭曾萧田潘董袁蔡蒋余于杜叶程苏魏吕丁任卢姚沈钟姜崔谭陆范汪廖石金贾韦夏付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤温芦';
    DECLARE given_names VARCHAR(500) DEFAULT '伟强明磊洋涛斌鹏飞龙华峰刚健超平军辉杰敏文博慧丽芳婷雪梅兰玲霞红琴蓉莉萍燕娟英花芬莲珠云秀';

    DECLARE cur CURSOR FOR SELECT id, name FROM warehouses;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- 生成总部员工
    -- 总经理
    INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
        department_id, position_id, manager_id, salary, bank_name, bank_account, status, address)
    VALUES ('EMP20250001', '张建国', 'M', '310101197505120001', '13800000001', 'ceo@erp.com',
        '1975-05-12', '2020-01-01', 1, 1, NULL, 100000.00, '工商银行', '6222021001000001', 'active', '上海市浦东新区');

    -- 各中心总监
    INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
        department_id, position_id, manager_id, salary, bank_name, bank_account, status, address)
    SELECT
        CONCAT('EMP2025', LPAD(seq + 1, 4, '0')),
        CONCAT(SUBSTRING(surnames, (seq * 2) % LENGTH(surnames) + 1, 1), SUBSTRING(given_names, (seq * 3) % LENGTH(given_names) + 1, 1), SUBSTRING(given_names, (seq * 5 + 1) % LENGTH(given_names) + 1, 1)),
        IF(seq % 3 = 0, 'F', 'M'),
        CONCAT('31010119', LPAD(80 + seq, 2, '0'), '01', LPAD(seq + 100, 6, '0')),
        CONCAT('138', LPAD(seq + 1000, 8, '0')),
        CONCAT('emp', seq + 100, '@erp.com'),
        DATE_SUB(CURDATE(), INTERVAL 30 + seq YEAR),
        DATE_SUB(CURDATE(), INTERVAL seq * 200 + 365 DAY),
        ELT(seq + 1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
        seq + 1,
        1,
        ROUND(15000 + RAND() * 35000, 2),
        ELT(FLOOR(RAND()*3)+1, '工商银行', '建设银行', '农业银行'),
        CONCAT('6222', LPAD(seq + 10000, 14, '0')),
        'active',
        '上海市'
    FROM (SELECT 0 AS seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
          UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
          UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19) nums;

    -- 各中心员工
    INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
        department_id, position_id, manager_id, salary, bank_name, bank_account, status, address)
    SELECT
        CONCAT('EMP2025', LPAD(seq + 50, 4, '0')),
        CONCAT(SUBSTRING(surnames, (seq * 3 + 10) % LENGTH(surnames) + 1, 1), SUBSTRING(given_names, (seq * 4 + 5) % LENGTH(given_names) + 1, 1), SUBSTRING(given_names, (seq * 7 + 3) % LENGTH(given_names) + 1, 1)),
        IF(seq % 4 = 0, 'F', 'M'),
        CONCAT('31010119', LPAD(85 + seq, 2, '0'), '01', LPAD(seq + 500, 6, '0')),
        CONCAT('139', LPAD(seq + 2000, 8, '0')),
        CONCAT('staff', seq + 200, '@erp.com'),
        DATE_SUB(CURDATE(), INTERVAL 22 + seq % 20 YEAR),
        DATE_SUB(CURDATE(), INTERVAL seq * 50 + 200 DAY),
        ELT(FLOOR(RAND()*7)+1, 9, 10, 11, 12, 13, 14, 1),
        FLOOR(RAND() * 20) + 1,
        FLOOR(RAND() * 20) + 2,
        ROUND(5000 + RAND() * 15000, 2),
        ELT(FLOOR(RAND()*3)+1, '工商银行', '建设银行', '农业银行'),
        CONCAT('6222', LPAD(seq + 20000, 14, '0')),
        IF(RAND() < 0.05, 'resigned', 'active'),
        '上海市'
    FROM (SELECT 0 AS seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
          UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
          UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
          UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24
          UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29
          UNION SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34
          UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39
          UNION SELECT 40 UNION SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44
          UNION SELECT 45 UNION SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49
          UNION SELECT 50 UNION SELECT 51 UNION SELECT 52 UNION SELECT 53 UNION SELECT 54
          UNION SELECT 55 UNION SELECT 56 UNION SELECT 57 UNION SELECT 58 UNION SELECT 59
          UNION SELECT 60 UNION SELECT 61 UNION SELECT 62 UNION SELECT 63 UNION SELECT 64
          UNION SELECT 65 UNION SELECT 66 UNION SELECT 67 UNION SELECT 68 UNION SELECT 69
          UNION SELECT 70 UNION SELECT 71 UNION SELECT 72 UNION SELECT 73 UNION SELECT 74
          UNION SELECT 75 UNION SELECT 76 UNION SELECT 77 UNION SELECT 78 UNION SELECT 79) nums;

    -- 为每个门店生成员工
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_wh_id, v_wh_name;
        IF v_done THEN LEAVE read_loop; END IF;

        -- 确定门店所属大区(简化: 根据门店名称判断)
        SET v_dept_id = 2 + FLOOR(RAND() * 7);

        -- 获取该部门的职位ID
        SELECT id INTO v_pos_id FROM positions WHERE department_id = v_dept_id AND name LIKE '%经理%' LIMIT 1;
        IF v_pos_id IS NULL THEN SELECT id INTO v_pos_id FROM positions WHERE department_id = 1 LIMIT 1; END IF;

        -- 店长 (1人)
        SET v_emp_count = v_emp_count + 1;
        SET v_emp_no = CONCAT('EMP', LPAD(v_emp_count + 100, 6, '0'));
        SET v_emp_name = CONCAT(SUBSTRING(surnames, (v_emp_count * 3) % LENGTH(surnames) + 1, 1), '店长');
        SET v_salary = ROUND(8000 + RAND() * 12000, 2);

        INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
            department_id, position_id, salary, bank_name, bank_account, status, address)
        VALUES (v_emp_no, v_emp_name, IF(RAND() > 0.3, 'M', 'F'),
            CONCAT('310', LPAD(v_emp_count, 15, '0')),
            CONCAT('138', LPAD(v_emp_count + 10000, 8, '0')),
            CONCAT('store', v_emp_count, '@erp.com'),
            DATE_SUB(CURDATE(), INTERVAL 25 + FLOOR(RAND()*15) YEAR),
            DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*1000) + 200 DAY),
            v_dept_id, v_pos_id, NULL, v_salary,
            ELT(FLOOR(RAND()*3)+1, '工商银行', '建设银行', '农业银行'),
            CONCAT('6222', LPAD(v_emp_count + 50000, 14, '0')),
            'active', CONCAT(SUBSTRING_INDEX(v_wh_name, '店', 1), '市'));

        SET v_emp_id = LAST_INSERT_ID();

        -- 更新仓库经理
        UPDATE warehouses SET manager_id = v_emp_id WHERE id = v_wh_id;

        -- 销售员 (2-3人)
        SELECT id INTO v_pos_id FROM positions WHERE department_id = v_dept_id AND name LIKE '%专员%' LIMIT 1;
        IF v_pos_id IS NULL THEN SELECT id INTO v_pos_id FROM positions WHERE department_id = 1 LIMIT 1; END IF;

        SET @sales_count = FLOOR(RAND() * 2) + 2;
        SET @si = 0;
        WHILE @si < @sales_count DO
            SET v_emp_count = v_emp_count + 1;
            SET v_emp_no = CONCAT('EMP', LPAD(v_emp_count + 100, 6, '0'));
            SET v_emp_name = CONCAT(SUBSTRING(surnames, (v_emp_count * 5) % LENGTH(surnames) + 1, 1), SUBSTRING(given_names, (v_emp_count * 3) % LENGTH(given_names) + 1, 1), SUBSTRING(given_names, (v_emp_count * 7) % LENGTH(given_names) + 1, 1));
            SET v_salary = ROUND(4000 + RAND() * 8000, 2);

            INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
                department_id, position_id, manager_id, salary, bank_name, bank_account, status, address)
            VALUES (v_emp_no, v_emp_name, IF(RAND() > 0.4, 'M', 'F'),
                CONCAT('310', LPAD(v_emp_count, 15, '0')),
                CONCAT('139', LPAD(v_emp_count + 20000, 8, '0')),
                CONCAT('sales', v_emp_count, '@erp.com'),
                DATE_SUB(CURDATE(), INTERVAL 20 + FLOOR(RAND()*15) YEAR),
                DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*800) + 100 DAY),
                v_dept_id, v_pos_id, v_emp_id, v_salary,
                ELT(FLOOR(RAND()*3)+1, '工商银行', '建设银行', '农业银行'),
                CONCAT('6222', LPAD(v_emp_count + 60000, 14, '0')),
                IF(RAND() < 0.08, 'resigned', 'active'),
                CONCAT(SUBSTRING_INDEX(v_wh_name, '店', 1), '市'));
            SET @si = @si + 1;
        END WHILE;

        -- 仓管员 (1-2人)
        SELECT id INTO v_pos_id FROM positions WHERE department_id = v_dept_id AND name LIKE '%主管%' LIMIT 1;
        IF v_pos_id IS NULL THEN SELECT id INTO v_pos_id FROM positions WHERE department_id = 1 LIMIT 1; END IF;

        SET @wk_count = FLOOR(RAND() * 2) + 1;
        SET @wi = 0;
        WHILE @wi < @wk_count DO
            SET v_emp_count = v_emp_count + 1;
            SET v_emp_no = CONCAT('EMP', LPAD(v_emp_count + 100, 6, '0'));
            SET v_emp_name = CONCAT(SUBSTRING(surnames, (v_emp_count * 7) % LENGTH(surnames) + 1, 1), SUBSTRING(given_names, (v_emp_count * 4) % LENGTH(given_names) + 1, 1), SUBSTRING(given_names, (v_emp_count * 9) % LENGTH(given_names) + 1, 1));
            SET v_salary = ROUND(5000 + RAND() * 6000, 2);

            INSERT INTO employees (employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
                department_id, position_id, manager_id, salary, bank_name, bank_account, status, address)
            VALUES (v_emp_no, v_emp_name, IF(RAND() > 0.3, 'M', 'F'),
                CONCAT('310', LPAD(v_emp_count, 15, '0')),
                CONCAT('137', LPAD(v_emp_count + 30000, 8, '0')),
                CONCAT('wh', v_emp_count, '@erp.com'),
                DATE_SUB(CURDATE(), INTERVAL 22 + FLOOR(RAND()*18) YEAR),
                DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*900) + 100 DAY),
                v_dept_id, v_pos_id, v_emp_id, v_salary,
                ELT(FLOOR(RAND()*3)+1, '工商银行', '建设银行', '农业银行'),
                CONCAT('6222', LPAD(v_emp_count + 70000, 14, '0')),
                'active',
                CONCAT(SUBSTRING_INDEX(v_wh_name, '店', 1), '市'));
            SET @wi = @wi + 1;
        END WHILE;
    END LOOP;
    CLOSE cur;

    SELECT CONCAT('员工生成: ', COUNT(*), '人') FROM employees;
END//


-- ============================================================
-- 3. 生成商品: 50+品类, 500+商品
-- 品类体系: 3级分类
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_products
CREATE PROCEDURE sp_gen_products()
BEGIN
    DECLARE v_cat1_id BIGINT;
    DECLARE v_cat2_id BIGINT;
    DECLARE v_cat1_idx INT DEFAULT 0;
    DECLARE v_cat2_idx INT;
    DECLARE v_prod_idx INT;
    DECLARE v_cat1_name VARCHAR(50);
    DECLARE v_cat2_name VARCHAR(50);
    DECLARE v_cat1_code VARCHAR(20);
    DECLARE v_cat2_code VARCHAR(20);
    DECLARE v_sku VARCHAR(50);
    DECLARE v_prod_name VARCHAR(200);
    DECLARE v_total_sku INT DEFAULT 0;

    -- 一级分类 (8个)
    DROP TEMPORARY TABLE IF EXISTS tmp_cat1;
    CREATE TEMPORARY TABLE tmp_cat1 (name VARCHAR(50), code VARCHAR(20));
    INSERT INTO tmp_cat1 VALUES
    ('食品饮料', 'FOOD'), ('日用百货', 'DAILY'), ('电子数码', 'ELEC'),
    ('服装鞋帽', 'CLOTH'), ('办公用品', 'OFFICE'), ('母婴用品', 'BABY'),
    ('美妆个护', 'BEAUTY'), ('家居家装', 'HOME');

    -- 二级分类 (每个一级3-5个)
    DROP TEMPORARY TABLE IF EXISTS tmp_cat2;
    CREATE TEMPORARY TABLE tmp_cat2 (cat1 VARCHAR(50), name VARCHAR(50), code VARCHAR(20));
    INSERT INTO tmp_cat2 VALUES
    ('食品饮料', '休闲零食', 'SNACK'), ('食品饮料', '饮料冲调', 'DRINK'), ('食品饮料', '生鲜食材', 'FRESH'), ('食品饮料', '粮油调味', 'OIL'), ('食品饮料', '方便速食', 'FAST'),
    ('日用百货', '清洁用品', 'CLEAN'), ('日用百货', '厨房用具', 'KITCHEN'), ('日用百货', '收纳整理', 'STORE'), ('日用百货', '个人护理', 'PERSONAL'),
    ('电子数码', '手机配件', 'PHONE'), ('电子数码', '电脑外设', 'PC'), ('电子数码', '智能穿戴', 'WEAR'), ('电子数码', '影音娱乐', 'AV'), ('电子数码', '智能家居', 'SMART'),
    ('服装鞋帽', '男装', 'MEN'), ('服装鞋帽', '女装', 'WOMEN'), ('服装鞋帽', '童装', 'KIDS'), ('服装鞋帽', '运动户外', 'SPORT'), ('服装鞋帽', '内衣袜品', 'UNDER'),
    ('办公用品', '文具本册', 'STAT'), ('办公用品', '办公设备', 'EQUIP'), ('办公用品', '绘画用品', 'DRAW'), ('办公用品', '文件管理', 'FILE'),
    ('母婴用品', '奶粉辅食', 'MILK'), ('母婴用品', '纸尿裤', 'DIAPER'), ('母婴用品', '玩具教育', 'TOY'), ('母婴用品', '婴儿出行', 'TRAVEL'),
    ('美妆个护', '护肤面霜', 'SKIN'), ('美妆个护', '彩妆香水', 'MAKEUP'), ('美妆个护', '洗发护发', 'HAIR'), ('美妆个护', '口腔护理', 'ORAL'),
    ('家居家装', '灯具照明', 'LIGHT'), ('家居家装', '家纺布艺', 'TEXTILE'), ('家居家装', '家具桌椅', 'FURN'), ('家居家装', '五金工具', 'TOOL');

    -- 商品名模板
    DROP TEMPORARY TABLE IF EXISTS tmp_prod_names;
    CREATE TEMPORARY TABLE tmp_prod_names (cat2 VARCHAR(50), name VARCHAR(100), spec VARCHAR(100), brand VARCHAR(50));
    INSERT INTO tmp_prod_names VALUES
    ('休闲零食', '原味薯片', '120g/袋', '脆脆乐'), ('休闲零食', '番茄味薯片', '120g/袋', '脆脆乐'),
    ('休闲零食', '烧烤味薯片', '120g/袋', '脆脆乐'), ('休闲零食', '坚果混合装', '500g/罐', '健康果园'),
    ('休闲零食', '开心果', '250g/袋', '坚果坊'), ('休闲零食', '腰果仁', '300g/罐', '坚果坊'),
    ('休闲零食', '牛肉干', '200g/袋', '草原风'), ('休闲零食', '猪肉脯', '250g/袋', '美味源'),
    ('休闲零食', '巧克力礼盒', '300g/盒', '甜蜜坊'), ('休闲零食', '曲奇饼干', '400g/盒', '烘焙家'),
    ('饮料冲调', '矿泉水', '550ml*24瓶', '清泉'), ('饮料冲调', '气泡水', '330ml*24罐', '泡泡乐'),
    ('饮料冲调', '有机绿茶', '250g/罐', '茗茶坊'), ('饮料冲调', '红茶礼盒', '200g/盒', '茗茶坊'),
    ('饮料冲调', '速溶咖啡', '100条/盒', '晨醒'), ('饮料冲调', '挂耳咖啡', '30包/盒', '匠人咖啡'),
    ('饮料冲调', '豆奶', '250ml*16盒', '豆香园'), ('饮料冲调', '椰子水', '330ml*12瓶', '热带风'),
    ('饮料冲调', '酸奶', '200g*12杯', '牧场鲜'), ('饮料冲调', '蜂蜜柚子茶', '500g/瓶', '蜜语'),
    ('生鲜食材', '冷冻鸡胸肉', '1kg/袋', '鲜优'), ('生鲜食材', '进口牛排', '250g/盒', '鲜优'),
    ('生鲜食材', '三文鱼', '200g/盒', '海鲜汇'), ('生鲜食材', '虾仁', '500g/袋', '海鲜汇'),
    ('生鲜食材', '有机蔬菜礼盒', '3kg/箱', '绿野'), ('生鲜食材', '土鸡蛋', '30枚/盒', '农家乐'),
    ('生鲜食材', '牛奶', '1L*6盒', '牧场鲜'), ('生鲜食材', '豆腐', '400g/盒', '豆香园'),
    ('粮油调味', '橄榄油', '1L/瓶', '橄榄园'), ('粮油调味', '花生油', '5L/桶', '鲁香'),
    ('粮油调味', '生抽酱油', '500ml/瓶', '味鲜'), ('粮油调味', '老抽酱油', '500ml/瓶', '味鲜'),
    ('粮油调味', '盐', '400g/袋', '海晶'), ('粮油调味', '白糖', '500g/袋', '甜心'),
    ('粮油调味', '大米', '10kg/袋', '东北粮仓'), ('粮油调味', '面粉', '5kg/袋', '中粮'),
    ('方便速食', '方便面', '120g*5包', '速康'), ('方便速食', '螺蛳粉', '300g*3包', '柳州味'),
    ('方便速食', '自热火锅', '400g/盒', '火锅控'), ('方便速食', '速冻水饺', '1kg/袋', '饺饺者'),
    ('方便速食', '汤圆', '500g/袋', '甜甜蜜'), ('方便速食', '即食米饭', '200g*6盒', '速康'),
    -- 日用百货
    ('清洁用品', '洗衣液', '3kg/桶', '洁丽'), ('清洁用品', '洗洁精', '1.5kg/瓶', '洁丽'),
    ('清洁用品', '洗衣凝珠', '50颗/盒', '洁丽'), ('清洁用品', '玻璃清洁剂', '500ml/瓶', '亮晶晶'),
    ('清洁用品', '马桶清洁剂', '500ml/瓶', '洁丽'), ('清洁用品', '地面清洁剂', '2L/桶', '洁丽'),
    ('厨房用具', '不粘锅', '32cm', '厨神'), ('厨房用具', '菜刀套装', '5件套', '锋利'),
    ('厨房用具', '砧板', '竹制大号', '厨神'), ('厨房用具', '保鲜盒套装', '10件套', '厨神'),
    ('厨房用具', '保温杯', '500ml', '暖芯'), ('厨房用具', '便当盒', '双层', '便当家'),
    ('收纳整理', '收纳箱', '60L', '收纳家'), ('收纳整理', '衣架', '20个装', '收纳家'),
    ('收纳整理', '压缩袋', '大号5个', '收纳家'), ('收纳整理', '鞋盒', '12个装', '收纳家'),
    ('个人护理', '纸巾', '3层*10卷', '柔柔'), ('个人护理', '湿巾', '80片*3包', '柔柔'),
    ('个人护理', '洗手液', '500ml', '洁丽'), ('个人护理', '毛巾', '纯棉3条装', '柔柔'),
    -- 电子数码
    ('手机配件', 'Type-C数据线', '1.5m快充', 'TechLink'), ('手机配件', '无线充电器', '15W', 'PowerUp'),
    ('手机配件', '手机壳', '防摔透明', 'CasePro'), ('手机配件', '钢化膜', '2片装', 'CasePro'),
    ('手机配件', '手机支架', '金属折叠', 'TechLink'), ('手机配件', '蓝牙自拍杆', '三脚架款', 'PhotoFun'),
    ('电脑外设', '机械键盘', '87键RGB', 'KeyCraft'), ('电脑外设', '无线鼠标', '静音', 'ClickPro'),
    ('电脑外设', '4K显示器', '27寸IPS', 'ViewPro'), ('电脑外设', 'USB-C HUB', '7合1', 'TechLink'),
    ('电脑外设', '笔记本支架', '铝合金', 'ErgoStand'), ('电脑外设', '移动硬盘', '1TB', 'DataSafe'),
    ('智能穿戴', '智能手表', 'AMOLED 46mm', 'SmartWear'), ('智能穿戴', '智能手环', '心率血氧', 'FitLife'),
    ('智能穿戴', '无线蓝牙耳机', 'TWS降噪', 'SoundMax'), ('智能穿戴', '骨传导耳机', '运动防水', 'SoundMax'),
    ('影音娱乐', '蓝牙音箱', '便携防水', 'SoundMax'), ('影音娱乐', '智能音箱', 'AI语音', 'SoundMax'),
    ('影音娱乐', '头戴耳机', 'Hi-Res', 'SoundMax'), ('影音娱乐', '投影仪', '1080P便携', 'ViewPro'),
    ('智能家居', '智能灯泡', 'WiFi彩光', 'SmartHome'), ('智能家居', '智能插座', 'WiFi定时', 'SmartHome'),
    ('智能家居', '智能门锁', '指纹密码', 'SafeHome'), ('智能家居', '扫地机器人', '激光导航', 'RoboClean'),
    -- 服装鞋帽
    ('男装', '商务衬衫', '纯棉免烫', '绅士'), ('男装', '休闲裤', '弹力棉', '绅士'),
    ('男装', '羽绒服', '90%白鸭绒', '暖冬'), ('男装', 'POLO衫', '纯棉条纹', '绅士'),
    ('男装', '牛仔裤', '直筒弹力', '牛仔控'), ('男装', '商务皮鞋', '头层牛皮', '绅士'),
    ('女装', '连衣裙', '雪纺碎花', '时尚佳人'), ('女装', '真丝围巾', '100%桑蚕丝', '时尚佳人'),
    ('女装', '羊绒大衣', '100%山羊绒', '奢品'), ('女装', '针织开衫', '纯羊毛', '时尚佳人'),
    ('女装', '阔腿裤', '垂感面料', '时尚佳人'), ('女装', '半身裙', 'A字百褶', '时尚佳人'),
    ('童装', '儿童T恤', '纯棉印花', '童趣'), ('童装', '儿童羽绒服', '80%白鸭绒', '童趣'),
    ('童装', '儿童运动鞋', '网面透气', '童趣'), ('童装', '婴儿连体衣', '纯棉A类', '婴爱'),
    ('运动户外', '运动跑鞋', '飞织气垫', '速跑'), ('运动户外', '运动T恤', '速干面料', '速跑'),
    ('运动户外', '运动短裤', '弹力速干', '速跑'), ('运动户外', '运动背包', '30L防水', '速跑'),
    ('运动户外', '瑜伽垫', 'TPE双面', '柔韧'), ('运动户外', '登山杖', '碳纤维', '探路者'),
    ('内衣袜品', '男士内裤', '纯棉4条装', '舒适派'), ('内衣袜品', '女士内衣', '无钢圈', '舒适派'),
    ('内衣袜品', '运动袜', '5双装', '舒适派'), ('内衣袜品', '丝袜', '3双装', '丝美人'),
    -- 办公用品
    ('文具本册', 'A4复印纸', '500张*5包', '纸立方'), ('文具本册', '中性笔', '0.5mm 12支装', '写乐'),
    ('文具本册', '笔记本', 'A5皮面192页', '纸立方'), ('文具本册', '荧光笔', '6色套装', '写乐'),
    ('文具本册', '便签纸', '彩色10本', '纸立方'), ('文具本册', '订书机', '中号', '得力'),
    ('办公设备', '激光打印机', 'A4黑白双面', 'PrintPro'), ('办公设备', '碎纸机', '段状15张/次', 'SecurePro'),
    ('办公设备', '办公椅', '人体工学网布', '舒适办公'), ('办公设备', '投影幕布', '100寸电动', 'ViewPro'),
    ('办公设备', '会议平板', '65寸触摸', 'ViewPro'), ('办公设备', '考勤机', '指纹+人脸', 'SecurePro'),
    ('绘画用品', '水彩笔', '24色', '绘彩'), ('绘画用品', '油画棒', '36色', '绘彩'),
    ('绘画用品', '素描本', 'A4 100页', '绘彩'), ('绘画用品', '马克笔', '48色双头', '绘彩'),
    ('文件管理', '文件夹', 'A4 20个装', '纸立方'), ('文件管理', '档案盒', '5cm 10个装', '纸立方'),
    ('文件管理', '文件柜', '四层抽屉', '纸立方'), ('文件管理', '白板', '120*90cm磁性', '纸立方'),
    -- 母婴用品
    ('奶粉辅食', '婴幼儿奶粉1段', '900g', '恩贝'), ('奶粉辅食', '婴幼儿奶粉2段', '900g', '恩贝'),
    ('奶粉辅食', '婴幼儿奶粉3段', '900g', '恩贝'), ('奶粉辅食', '米粉', '225g*2盒', '恩贝'),
    ('奶粉辅食', '果泥', '90g*6袋', '果乐多'), ('奶粉辅食', '磨牙棒', '64g/盒', '恩贝'),
    ('纸尿裤', '纸尿裤S码', '82片', '舒舒乐'), ('纸尿裤', '纸尿裤M码', '72片', '舒舒乐'),
    ('纸尿裤', '纸尿裤L码', '60片', '舒舒乐'), ('纸尿裤', '拉拉裤XL码', '48片', '舒舒乐'),
    ('玩具教育', '积木套装', '100粒', '智多星'), ('玩具教育', '拼图', '200片', '智多星'),
    ('玩具教育', '早教机', '7寸触摸屏', '智多星'), ('玩具教育', '遥控汽车', '越野款', '童趣'),
    ('婴儿出行', '婴儿推车', '轻便折叠', '婴爱'), ('婴儿出行', '安全座椅', '0-12岁', '婴爱'),
    ('婴儿出行', '婴儿背带', '透气款', '婴爱'), ('婴儿出行', '妈咪包', '大容量', '婴爱'),
    -- 美妆个护
    ('护肤面霜', '保湿面霜', '50g', '花颜'), ('护肤面霜', '精华液', '30ml', '花颜'),
    ('护肤面霜', '防晒霜', 'SPF50+ 60ml', '花颜'), ('护肤面霜', '面膜', '5片装', '花颜'),
    ('护肤面霜', '眼霜', '15g', '花颜'), ('护肤面霜', '卸妆水', '200ml', '花颜'),
    ('彩妆香水', '粉底液', '30ml', '美妆大师'), ('彩妆香水', '口红', '3.5g', '美妆大师'),
    ('彩妆香水', '眼影盘', '12色', '美妆大师'), ('彩妆香水', '香水', '50ml', '法国香氛'),
    ('洗发护发', '洗发水', '500ml', '丝柔'), ('洗发护发', '护发素', '500ml', '丝柔'),
    ('洗发护发', '发膜', '200ml', '丝柔'), ('洗发护发', '弹力素', '150ml', '丝柔'),
    ('口腔护理', '牙膏', '180g', '齿白'), ('口腔护理', '牙刷', '软毛4支装', '齿白'),
    ('口腔护理', '漱口水', '500ml', '齿白'), ('口腔护理', '牙线', '50m', '齿白'),
    -- 家居家装
    ('灯具照明', 'LED吸顶灯', '36W圆形', '光之家'), ('灯具照明', '台灯', 'LED护眼', '光之家'),
    ('灯具照明', '落地灯', '客厅简约', '光之家'), ('灯具照明', '筒灯', '5W 10个装', '光之家'),
    ('家纺布艺', '四件套', '纯棉1.8m', '梦之家'), ('家纺布艺', '夏凉被', '200*230cm', '梦之家'),
    ('家纺布艺', '枕芯', '一对装', '梦之家'), ('家纺布艺', '窗帘', '定制遮光', '梦之家'),
    ('家具桌椅', '电脑桌', '120*60cm', '木语'), ('家具桌椅', '书架', '5层', '木语'),
    ('家具桌椅', '衣柜', '三门', '木语'), ('家具桌椅', '鞋柜', '翻斗超薄', '木语'),
    ('五金工具', '工具箱套装', '45件', '工匠'), ('五金工具', '电钻', '锂电冲击', '工匠'),
    ('五金工具', '梯子', '人字梯4步', '工匠'), ('五金工具', '测距仪', '激光50m', '工匠');

    -- 插入一级分类
    SET v_cat1_idx = 0;
    WHILE v_cat1_idx < 8 DO
        SELECT name, code INTO v_cat1_name, v_cat1_code FROM tmp_cat1 LIMIT v_cat1_idx, 1;

        INSERT INTO product_categories (parent_id, name, code, sort_order) VALUES (NULL, v_cat1_name, v_cat1_code, v_cat1_idx + 1);
        SET v_cat1_id = LAST_INSERT_ID();

        -- 插入二级分类
        SET v_cat2_idx = 0;
        WHILE v_cat2_idx < 100 DO
            SELECT COUNT(*) INTO @cat2_count FROM tmp_cat2 WHERE cat1 = v_cat1_name;
            IF v_cat2_idx >= @cat2_count THEN
                SET v_cat2_idx = 100;
                ITERATE;
            END IF;

            SELECT name, code INTO v_cat2_name, v_cat2_code FROM tmp_cat2 WHERE cat1 = v_cat1_name LIMIT v_cat2_idx, 1;

            INSERT INTO product_categories (parent_id, name, code, sort_order) VALUES (v_cat1_id, v_cat2_name, v_cat2_code, v_cat2_idx + 1);
            SET v_cat2_id = LAST_INSERT_ID();

            -- 插入商品 (每个二级分类8-12个)
            SET v_prod_idx = 0;
            WHILE v_prod_idx < 12 DO
                SELECT COUNT(*) INTO @prod_count FROM tmp_prod_names WHERE cat2 = v_cat2_name;
                IF v_prod_idx >= @prod_count THEN
                    SET v_prod_idx = 12;
                    ITERATE;
                END IF;

                SELECT name, spec, brand INTO v_prod_name, @spec, @brand
                FROM tmp_prod_names WHERE cat2 = v_cat2_name LIMIT v_prod_idx, 1;

                SET v_sku = CONCAT(v_cat2_code, '-', LPAD(v_total_sku + 1, 4, '0'));

                INSERT INTO products (sku, name, category_id, unit, spec, brand, barcode,
                    purchase_price, wholesale_price, retail_price, min_stock, max_stock,
                    batch_managed, shelf_life_days, weight_kg, volume_m3)
                VALUES (v_sku, v_prod_name, v_cat2_id, IF(RAND()>0.5, '件', '个'),
                    @spec, @brand, CONCAT('690', LPAD(v_total_sku + 1, 10, '0')),
                    ROUND(5 + RAND() * 200, 2),
                    ROUND(10 + RAND() * 400, 2),
                    ROUND(15 + RAND() * 600, 2),
                    FLOOR(5 + RAND() * 50),
                    FLOOR(100 + RAND() * 500),
                    IF(RAND() > 0.3, TRUE, FALSE),
                    FLOOR(30 + RAND() * 700),
                    ROUND(0.1 + RAND() * 10, 3),
                    ROUND(0.001 + RAND() * 0.1, 6));

                SET v_total_sku = v_total_sku + 1;
                SET v_prod_idx = v_prod_idx + 1;
            END WHILE;

            SET v_cat2_idx = v_cat2_idx + 1;
        END WHILE;

        SET v_cat1_idx = v_cat1_idx + 1;
    END WHILE;

    -- 清理临时表
    DROP TEMPORARY TABLE tmp_cat1;
    DROP TEMPORARY TABLE tmp_cat2;
    DROP TEMPORARY TABLE tmp_prod_names;

    SELECT CONCAT('商品生成: ', COUNT(*), '个') FROM products;
END//


-- ============================================================
-- 4. 生成供应商 (50家, 含地理坐标)
-- 每个产品3-5个供应商，确保竞争关系
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_suppliers
CREATE PROCEDURE sp_gen_suppliers()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_city VARCHAR(50);
    DECLARE v_province VARCHAR(50);
    DECLARE v_lat DECIMAL(10,7);
    DECLARE v_lon DECIMAL(10,7);
    DECLARE v_product_count INT;
    DECLARE v_prod_id BIGINT;
    DECLARE v_prod_price DECIMAL(12,2);
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_supplier_count INT;
    DECLARE v_supplier_ids VARCHAR(500) DEFAULT '';

    -- 城市坐标表(临时)
    DROP TEMPORARY TABLE IF EXISTS tmp_city_coords;
    CREATE TEMPORARY TABLE tmp_city_coords (city VARCHAR(50), province VARCHAR(50), lat DECIMAL(10,7), lon DECIMAL(10,7));
    INSERT INTO tmp_city_coords VALUES
    ('深圳', '广东省', 22.5431, 114.0579), ('广州', '广东省', 23.1291, 113.2644),
    ('东莞', '广东省', 23.0208, 113.7518), ('佛山', '广东省', 23.0219, 113.1219),
    ('杭州', '浙江省', 30.2741, 120.1551), ('宁波', '浙江省', 29.8683, 121.5440),
    ('温州', '浙江省', 27.9939, 120.6993),
    ('上海', '上海市', 31.2304, 121.4737),
    ('北京', '北京市', 39.9042, 116.4074),
    ('南京', '江苏省', 32.0603, 118.7969), ('苏州', '江苏省', 31.2990, 120.5853),
    ('无锡', '江苏省', 31.4912, 120.3119),
    ('成都', '四川省', 30.5728, 104.0665),
    ('重庆', '重庆市', 29.4316, 106.5516),
    ('武汉', '湖北省', 30.5928, 114.3054),
    ('郑州', '河南省', 34.7466, 113.6254),
    ('长沙', '湖南省', 28.2282, 112.9388),
    ('西安', '陕西省', 34.3416, 108.9398),
    ('青岛', '山东省', 36.0671, 120.3826), ('济南', '山东省', 36.6512, 117.1205),
    ('厦门', '福建省', 24.4798, 118.0894), ('福州', '福建省', 26.0745, 119.2965),
    ('天津', '天津市', 39.3434, 117.1902),
    ('合肥', '安徽省', 31.8206, 117.2272),
    ('南昌', '江西省', 28.6820, 115.8579),
    ('大连', '辽宁省', 38.9140, 121.6186), ('沈阳', '辽宁省', 41.8057, 123.4315),
    ('石家庄', '河北省', 38.0428, 114.5149),
    ('太原', '山西省', 37.8706, 112.5489),
    ('长春', '吉林省', 43.8171, 125.3235),
    ('哈尔滨', '黑龙江省', 45.8038, 126.5350),
    ('昆明', '云南省', 25.0389, 102.7123),
    ('贵阳', '贵州省', 26.6470, 106.6302),
    ('南宁', '广西', 22.8170, 108.3665),
    ('海口', '海南省', 20.0174, 110.3492),
    ('兰州', '甘肃省', 36.0611, 103.8343),
    ('银川', '宁夏', 38.4872, 106.2309),
    ('西宁', '青海省', 36.6171, 101.7782),
    ('乌鲁木齐', '新疆', 43.8256, 87.6168),
    ('拉萨', '西藏', 29.6500, 91.1400),
    ('呼和浩特', '内蒙古', 40.8424, 111.7490);

    -- 生成50家供应商
    WHILE v_i < 50 DO
        SELECT city, province, lat, lon INTO v_city, v_province, v_lat, v_lon
        FROM tmp_city_coords ORDER BY RAND() LIMIT 1;

        INSERT INTO suppliers (name, code, contact_person, phone, email, address,
            province, city, district, latitude, longitude,
            bank_name, bank_account, tax_id, credit_level)
        VALUES (
            CONCAT(v_city, ELT(FLOOR(RAND()*6)+1, '科技有限公司', '实业有限公司', '商贸有限公司', '工贸有限公司', '集团有限公司', '供应链有限公司')),
            CONCAT('SUP', LPAD(v_i + 1, 4, '0')),
            CONCAT(SUBSTRING('张李王陈刘杨赵黄周吴', (v_i % 10) * 1 + 1, 1), '经理'),
            CONCAT('1', LPAD(3000000000 + v_i * 100, 10, '0')),
            CONCAT('supplier', v_i + 1, '@supplier.com'),
            CONCAT(v_city, '市工业园区', v_i + 1, '号'),
            v_province, v_city, CONCAT(ELT(FLOOR(RAND()*5)+1, '南山区', '福田区', '宝安区', '龙岗区', '工业园区')),
            v_lat + (RAND() - 0.5) * 0.05, v_lon + (RAND() - 0.5) * 0.05,
            ELT(FLOOR(RAND()*4)+1, '工商银行', '建设银行', '农业银行', '招商银行'),
            CONCAT('6222', LPAD(v_i + 100000, 14, '0')),
            CONCAT('913', LPAD(v_i + 10000, 15, '0')),
            ELT(FLOOR(RAND()*4)+1, 'A', 'A', 'B', 'B')
        );
        SET v_i = v_i + 1;
    END WHILE;

    -- 确保每个产品有3-5个供应商(关键!)
    SELECT COUNT(*) INTO v_product_count FROM products;
    SELECT COUNT(*) INTO v_supplier_count FROM suppliers;

    SET v_prod_id = 1;
    WHILE v_prod_id <= v_product_count DO
        SELECT purchase_price INTO v_prod_price FROM products WHERE id = v_prod_id;

        -- 清空已有(如果有的话)
        DELETE FROM supplier_products WHERE product_id = v_prod_id;

        -- 随机选3-5个供应商
        SET @num_suppliers = FLOOR(RAND() * 3) + 3;
        INSERT INTO supplier_products (supplier_id, product_id, supplier_price, lead_time_days,
            min_order_qty, shipping_cost_per_km, return_rate, quality_score, is_preferred)
        SELECT
            s.id,
            v_prod_id,
            -- 价格: 围绕进货价波动(-10%到+15%)
            ROUND(v_prod_price * (0.90 + RAND() * 0.25), 2),
            -- 交期: 3-15天
            FLOOR(3 + RAND() * 13),
            -- 起订量
            FLOOR(5 + RAND() * 50),
            -- 每公里物流费率: 0.3-1.0元/km
            ROUND(0.30 + RAND() * 0.70, 4),
            -- 退货率: 1-15%
            ROUND(0.01 + RAND() * 0.14, 4),
            -- 质量评分: 75-100
            ROUND(75 + RAND() * 25, 2),
            -- 价格最低的标记为优选
            FALSE
        FROM suppliers s
        WHERE s.cooperation_status = 'active'
        ORDER BY RAND()
        LIMIT @num_suppliers;

        -- 标记价格最低的为优选
        UPDATE supplier_products sp
        SET is_preferred = TRUE
        WHERE sp.product_id = v_prod_id
        ORDER BY sp.supplier_price ASC
        LIMIT 1;

        SET v_prod_id = v_prod_id + 1;
    END WHILE;

    DROP TEMPORARY TABLE tmp_city_coords;

    SELECT CONCAT('供应商: ', (SELECT COUNT(*) FROM suppliers), '家, 供应关系: ',
        (SELECT COUNT(*) FROM supplier_products), '条') AS result;
END//


-- ============================================================
-- 5. 生成客户 (200+)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_customers
CREATE PROCEDURE sp_gen_customers()
BEGIN
    DECLARE v_i INT DEFAULT 0;

    -- 企业客户
    WHILE v_i < 150 DO
        INSERT INTO customers (code, name, type, contact_person, phone, email, address,
            credit_limit, credit_days, membership_level, status)
        VALUES (
            CONCAT('CUST', LPAD(v_i + 1, 5, '0')),
            CONCAT(ELT(FLOOR(RAND()*20)+1, '深圳', '广州', '北京', '上海', '杭州', '成都', '武汉', '南京', '郑州', '长沙', '西安', '重庆', '青岛', '大连', '厦门', '天津', '苏州', '东莞', '合肥', '福州'),
                ELT(FLOOR(RAND()*6)+1, '商贸有限公司', '科技有限公司', '实业有限公司', '进出口有限公司', '连锁超市有限公司', '电子商务有限公司')),
            'company',
            CONCAT(SUBSTRING('张李王陈刘杨赵黄周吴', (v_i % 10) * 1 + 1, 1), '总'),
            CONCAT('1', LPAD(5000000000 + v_i * 100, 10, '0')),
            CONCAT('cust', v_i + 1, '@company.com'),
            CONCAT(ELT(FLOOR(RAND()*20)+1, '深圳市', '广州市', '北京市', '上海市', '杭州市', '成都市', '武汉市', '南京市', '郑州市', '长沙市', '西安市', '重庆市', '青岛市', '大连市', '厦门市', '天津市', '苏州市', '东莞市', '合肥市', '福州市'), '商业区'),
            ROUND(50000 + RAND() * 500000, 2),
            ELT(FLOOR(RAND()*3)+1, 30, 45, 60),
            ELT(FLOOR(RAND()*5)+1, 'normal', 'silver', 'gold', 'platinum', 'diamond'),
            'active'
        );
        SET v_i = v_i + 1;
    END WHILE;

    -- 个人客户
    SET v_i = 0;
    WHILE v_i < 50 DO
        INSERT INTO customers (code, name, type, contact_person, phone, email, address,
            credit_limit, credit_days, membership_level, status)
        VALUES (
            CONCAT('CUST', LPAD(v_i + 151, 5, '0')),
            CONCAT(SUBSTRING('张李王陈刘杨赵黄周吴', (v_i % 10) * 1 + 1, 1), SUBSTRING('伟明磊洋涛斌鹏飞龙华', (v_i % 10) * 1 + 1, 1)),
            'individual',
            CONCAT(SUBSTRING('张李王陈刘杨赵黄周吴', (v_i % 10) * 1 + 1, 1), SUBSTRING('伟明磊洋涛斌鹏飞龙华', (v_i % 10) * 1 + 1, 1)),
            CONCAT('1', LPAD(6000000000 + v_i * 100, 10, '0')),
            CONCAT('user', v_i + 1, '@email.com'),
            CONCAT(ELT(FLOOR(RAND()*20)+1, '深圳市', '广州市', '北京市', '上海市', '杭州市', '成都市', '武汉市', '南京市', '郑州市', '长沙市', '西安市', '重庆市', '青岛市', '大连市', '厦门市', '天津市', '苏州市', '东莞市', '合肥市', '福州市'), '住宅区'),
            ROUND(5000 + RAND() * 30000, 2),
            15,
            ELT(FLOOR(RAND()*4)+1, 'normal', 'silver', 'gold', 'silver'),
            'active'
        );
        SET v_i = v_i + 1;
    END WHILE;

    SELECT CONCAT('客户: ', COUNT(*), '个') FROM customers;
END//


-- ============================================================
-- 6. 生成批号和库存
-- 每个商品生成2-5个批号，分配到不同门店
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_batches_inventory
CREATE PROCEDURE sp_gen_batches_inventory()
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_prod_id BIGINT;
    DECLARE v_prod_price DECIMAL(12,2);
    DECLARE v_shelf_life INT;
    DECLARE v_supplier_id BIGINT;
    DECLARE v_batch_idx INT;
    DECLARE v_batch_no VARCHAR(50);
    DECLARE v_batch_id BIGINT;
    DECLARE v_prod_date DATE;
    DECLARE v_expiry_date DATE;
    DECLARE v_init_qty INT;
    DECLARE v_wh_id BIGINT;
    DECLARE v_wh_done INT DEFAULT FALSE;

    DECLARE cur CURSOR FOR SELECT id, purchase_price, shelf_life_days FROM products;
    DECLARE cur_wh CURSOR FOR SELECT id FROM warehouses ORDER BY RAND() LIMIT 5;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    SELECT id INTO v_supplier_id FROM suppliers LIMIT 1;

    OPEN cur;
    prod_loop: LOOP
        FETCH cur INTO v_prod_id, v_prod_price, v_shelf_life;
        IF v_done THEN LEAVE prod_loop; END IF;

        SET v_batch_idx = 0;
        WHILE v_batch_idx < FLOOR(RAND() * 4) + 2 DO
            SET v_batch_no = CONCAT('BT', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 180) DAY), '%y%m%d'), LPAD(v_batch_idx + 1, 3, '0'));
            SET v_prod_date = DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * v_shelf_life) DAY);
            SET v_expiry_date = DATE_ADD(v_prod_date, INTERVAL v_shelf_life DAY);
            SET v_init_qty = FLOOR(50 + RAND() * 500);

            INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
                supplier_id, purchase_price, initial_qty, current_qty, status)
            VALUES (v_prod_id, v_batch_no, v_prod_date, v_expiry_date,
                FLOOR(RAND() * 50) + 1, v_prod_price, v_init_qty, v_init_qty,
                IF(v_expiry_date < CURDATE(), 'expired', 'active'));
            SET v_batch_id = LAST_INSERT_ID();

            -- 分配到随机门店
            OPEN cur_wh;
            wh_loop: LOOP
                FETCH cur_wh INTO v_wh_id;
                IF v_wh_done THEN LEAVE wh_loop; END IF;

                INSERT INTO inventory (product_id, batch_id, warehouse_id, shelf_location, quantity)
                VALUES (v_prod_id, v_batch_id, v_wh_id,
                    CONCAT('A-', LPAD(FLOOR(RAND() * 20) + 1, 2, '0'), '-', LPAD(FLOOR(RAND() * 10) + 1, 2, '0')),
                    FLOOR(v_init_qty / 5 + RAND() * v_init_qty / 3));
            END LOOP;
            CLOSE cur_wh;
            SET v_wh_done = FALSE;

            SET v_batch_idx = v_batch_idx + 1;
        END WHILE;
    END LOOP;
    CLOSE cur;

    SELECT CONCAT('批号: ', COUNT(*), '个, 库存记录: ', (SELECT COUNT(*) FROM inventory), '条') FROM product_batches;
END//


-- ============================================================
-- 7. 生成采购单 (1000+条)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_purchase_orders
CREATE PROCEDURE sp_gen_purchase_orders()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT;
    DECLARE v_supplier_id BIGINT;
    DECLARE v_product_id BIGINT;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_total DECIMAL(18,2);
    DECLARE v_order_date DATE;
    DECLARE v_supplier_count INT;
    DECLARE v_product_count INT;

    SELECT COUNT(*) INTO v_supplier_count FROM suppliers;
    SELECT COUNT(*) INTO v_product_count FROM products;

    WHILE v_i < 1000 DO
        SET v_supplier_id = FLOOR(RAND() * v_supplier_count) + 1;
        SET v_order_date = DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 365) DAY);
        SET v_order_no = CONCAT('PO-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(v_i + 1, 5, '0'));

        INSERT INTO purchase_orders (order_no, supplier_id, department_id, purchaser_id,
            order_date, expected_delivery_date, actual_delivery_date, total_amount, paid_amount, status)
        VALUES (v_order_no, v_supplier_id, FLOOR(RAND() * 14) + 1, FLOOR(RAND() * 100) + 20,
            v_order_date, DATE_ADD(v_order_date, INTERVAL FLOOR(RAND() * 10) + 3 DAY),
            IF(RAND() > 0.1, DATE_ADD(v_order_date, INTERVAL FLOOR(RAND() * 12) + 3 DAY), NULL),
            0, 0, IF(RAND() > 0.2, 'received', IF(RAND() > 0.5, 'ordered', 'partially_received')));
        SET v_order_id = LAST_INSERT_ID();

        SET v_total = 0;
        SET @items = FLOOR(RAND() * 5) + 1;
        SET @j = 0;
        WHILE @j < @items DO
            SET v_product_id = FLOOR(RAND() * v_product_count) + 1;
            SELECT purchase_price INTO v_price FROM products WHERE id = v_product_id;
            SET v_qty = FLOOR(10 + RAND() * 200);

            INSERT INTO purchase_order_items (order_id, product_id, quantity, unit_price, received_qty)
            VALUES (v_order_id, v_product_id, v_qty, v_price, FLOOR(v_qty * (0.7 + RAND() * 0.3)));

            SET v_total = v_total + (v_qty * v_price);
            SET @j = @j + 1;
        END WHILE;

        UPDATE purchase_orders SET total_amount = v_total, paid_amount = ROUND(v_total * RAND(), 2) WHERE id = v_order_id;
        SET v_i = v_i + 1;
    END WHILE;

    SELECT CONCAT('采购单: ', COUNT(*), '单') FROM purchase_orders;
END//


-- ============================================================
-- 8. 生成销售单 (5000+条)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_sales_orders
CREATE PROCEDURE sp_gen_sales_orders()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT;
    DECLARE v_customer_id BIGINT;
    DECLARE v_salesperson_id BIGINT;
    DECLARE v_warehouse_id BIGINT;
    DECLARE v_product_id BIGINT;
    DECLARE v_batch_id BIGINT;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_total DECIMAL(18,2);
    DECLARE v_order_date DATE;
    DECLARE v_customer_count INT;
    DECLARE v_product_count INT;
    DECLARE v_wh_count INT;
    DECLARE v_emp_count INT;
    DECLARE v_available INT;

    SELECT COUNT(*) INTO v_customer_count FROM customers;
    SELECT COUNT(*) INTO v_product_count FROM products;
    SELECT COUNT(*) INTO v_wh_count FROM warehouses;
    SELECT COUNT(*) INTO v_emp_count FROM employees WHERE status IN ('active', 'probation');

    WHILE v_i < 5000 DO
        SET v_customer_id = FLOOR(RAND() * v_customer_count) + 1;
        SET v_warehouse_id = FLOOR(RAND() * v_wh_count) + 1;
        SET v_salesperson_id = (SELECT id FROM employees WHERE status IN ('active', 'probation') ORDER BY RAND() LIMIT 1);
        SET v_order_date = DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 365) DAY);
        SET v_order_no = CONCAT('SO-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(v_i + 1, 6, '0'));

        INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
            order_date, delivery_date, discount_amount, total_amount, paid_amount, tax_amount,
            payment_method, status)
        VALUES (v_order_no, v_customer_id, v_salesperson_id, v_warehouse_id,
            v_order_date, IF(RAND() > 0.3, DATE_ADD(v_order_date, INTERVAL FLOOR(RAND() * 5) + 1 DAY), NULL),
            ROUND(RAND() * 50, 2), 0, 0, 0,
            ELT(FLOOR(RAND() * 6) + 1, 'cash', 'card', 'transfer', 'credit', 'wechat', 'alipay'),
            IF(RAND() > 0.15, IF(RAND() > 0.3, 'delivered', 'confirmed'), IF(RAND() > 0.5, 'returned', 'cancelled')));
        SET v_order_id = LAST_INSERT_ID();

        SET v_total = 0;
        SET @items = FLOOR(RAND() * 5) + 1;
        SET @j = 0;
        WHILE @j < @items DO
            SET v_product_id = FLOOR(RAND() * v_product_count) + 1;
            SELECT retail_price INTO v_price FROM products WHERE id = v_product_id;
            SET v_qty = FLOOR(1 + RAND() * 10);

            -- 获取批号
            SELECT pb.id INTO v_batch_id
            FROM product_batches pb
            JOIN inventory i ON pb.id = i.batch_id
            WHERE pb.product_id = v_product_id AND i.warehouse_id = v_warehouse_id
              AND pb.status = 'active' AND pb.current_qty >= v_qty
            LIMIT 1;

            IF v_batch_id IS NOT NULL THEN
                INSERT INTO sales_order_items (order_id, product_id, batch_id, quantity, unit_price, discount, amount)
                VALUES (v_order_id, v_product_id, v_batch_id, v_qty, v_price, ROUND(RAND() * 10, 2), v_qty * v_price);

                SET v_total = v_total + (v_qty * v_price);
            END IF;
            SET @j = @j + 1;
        END WHILE;

        UPDATE sales_orders
        SET total_amount = v_total,
            paid_amount = IF(status = 'delivered', ROUND(v_total * (0.5 + RAND() * 0.5), 2), 0),
            tax_amount = ROUND(v_total * 0.13, 2)
        WHERE id = v_order_id;

        SET v_i = v_i + 1;
    END WHILE;

    SELECT CONCAT('销售单: ', COUNT(*), '单') FROM sales_orders;
END//


-- ============================================================
-- 9. 生成考勤数据
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_attendance
CREATE PROCEDURE sp_gen_attendance()
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_emp_id BIGINT;
    DECLARE v_day INT DEFAULT 0;
    DECLARE v_att_date DATE;
    DECLARE v_dow INT;
    DECLARE v_status VARCHAR(20);
    DECLARE v_late INT;
    DECLARE v_clock_in DATETIME;
    DECLARE v_clock_out DATETIME;

    DECLARE cur CURSOR FOR SELECT id FROM employees WHERE status IN ('active', 'probation') LIMIT 200;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    OPEN cur;
    emp_loop: LOOP
        FETCH cur INTO v_emp_id;
        IF v_done THEN LEAVE emp_loop; END IF;

        SET v_day = 0;
        WHILE v_day < 90 DO
            SET v_att_date = DATE_SUB(CURDATE(), INTERVAL v_day DAY);
            SET v_dow = DAYOFWEEK(v_att_date);

            IF v_dow IN (1, 7) THEN SET v_day = v_day + 1; ITERATE; END IF;

            SET @r = RAND();
            IF @r < 0.04 THEN SET v_status = 'absent'; SET v_clock_in = NULL; SET v_clock_out = NULL; SET v_late = 0;
            ELSEIF @r < 0.12 THEN SET v_status = 'late'; SET v_late = FLOOR(RAND() * 45) + 1; SET v_clock_in = ADDTIME(TIMESTAMP(v_att_date, '08:00:00'), SEC_TO_TIME(v_late * 60)); SET v_clock_out = TIMESTAMP(v_att_date, '17:30:00');
            ELSEIF @r < 0.16 THEN SET v_status = 'early'; SET v_late = 0; SET v_clock_in = TIMESTAMP(v_att_date, '08:00:00'); SET v_clock_out = TIMESTAMP(v_att_date, '16:30:00');
            ELSE SET v_status = 'normal'; SET v_late = 0; SET v_clock_in = TIMESTAMP(v_att_date, '08:00:00'); SET v_clock_out = TIMESTAMP(v_att_date, '17:30:00');
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (v_emp_id, v_att_date, v_clock_in, v_clock_out, v_status, v_late)
            ON DUPLICATE KEY UPDATE status = VALUES(status);

            SET v_day = v_day + 1;
        END WHILE;
    END LOOP;
    CLOSE cur;

    SELECT CONCAT('考勤: ', COUNT(*), '条') FROM attendance;
END//


-- ============================================================
-- 10. 生成财务数据
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_finance_data
CREATE PROCEDURE sp_gen_finance_data()
BEGIN
    -- 科目已在01-data中定义，这里补充余额
    UPDATE accounts SET current_balance = 500000.00 WHERE code = '1001';
    UPDATE accounts SET current_balance = 8000000.00 WHERE code = '100201';
    UPDATE accounts SET current_balance = 3000000.00 WHERE code = '100202';
    UPDATE accounts SET current_balance = 1500000.00 WHERE code = '100203';

    -- 为已完成的采购单生成发票
    INSERT INTO invoices (invoice_no, invoice_type, supplier_id, invoice_date, due_date,
        total_amount, tax_amount, tax_rate, status, verified_by, verified_at)
    SELECT
        CONCAT('INV-', DATE_FORMAT(po.order_date, '%Y%m%d'), '-', LPAD(po.id, 5, '0')),
        'purchase', po.supplier_id,
        DATE_ADD(po.order_date, INTERVAL FLOOR(RAND() * 5) DAY),
        DATE_ADD(po.order_date, INTERVAL 30 DAY),
        po.total_amount, ROUND(po.total_amount * 0.13, 2), 0.13,
        'verified', FLOOR(RAND() * 20) + 20, DATE_ADD(po.order_date, INTERVAL FLOOR(RAND() * 5) + 3 DAY)
    FROM purchase_orders po WHERE po.status = 'received' LIMIT 500;

    -- 生成税务发票
    INSERT INTO tax_invoices (invoice_no, invoice_code, invoice_type, tax_direction, party_type, party_id,
        invoice_date, amount_excluding_tax, tax_rate, tax_period, status)
    SELECT
        CONCAT('TAX-', DATE_FORMAT(po.order_date, '%Y%m%d'), '-', LPAD(po.id, 5, '0')),
        CONCAT('044002', LPAD(po.id, 8, '0')),
        'vat_special', 'input', 'supplier', po.supplier_id,
        DATE_ADD(po.order_date, INTERVAL FLOOR(RAND() * 5) DAY),
        ROUND(po.total_amount / 1.13, 2), 0.13,
        DATE_FORMAT(po.order_date, '%Y-%m'), 'issued'
    FROM purchase_orders po WHERE po.status = 'received' LIMIT 300;

    -- 生成工资发放
    INSERT INTO salary_payments (payment_no, employee_id, payment_date, salary_month,
        base_salary, overtime_pay, bonus, deduction,
        social_security_personal, housing_fund_personal, income_tax,
        net_pay, social_security_company, housing_fund_company, status, paid_at)
    SELECT
        CONCAT('SAL-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL m.month MONTH), '%Y%m'), '-', LPAD(e.id, 5, '0')),
        e.id,
        DATE_SUB(CURDATE(), INTERVAL m.month MONTH),
        DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL m.month MONTH), '%Y-%m'),
        e.salary,
        ROUND(RAND() * 500, 2),
        ROUND(RAND() * 1000, 2),
        0,
        ROUND(e.salary * 0.08, 2),
        ROUND(e.salary * 0.12, 2),
        ROUND(GREATEST(e.salary - 5000 - e.salary * 0.08 - e.salary * 0.12, 0) * 0.03, 2),
        e.salary + ROUND(RAND() * 500, 2) + ROUND(RAND() * 1000, 2) - ROUND(e.salary * 0.08, 2) - ROUND(e.salary * 0.12, 2) - ROUND(GREATEST(e.salary - 5000 - e.salary * 0.08 - e.salary * 0.12, 0) * 0.03, 2),
        ROUND(e.salary * 0.16, 2),
        ROUND(e.salary * 0.12, 2),
        'paid', NOW()
    FROM employees e
    CROSS JOIN (SELECT 0 AS month UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) m
    WHERE e.status IN ('active', 'probation')
    LIMIT 1000;

    SELECT CONCAT('财务数据生成完成') AS result;
END//


-- ============================================================
-- 11. 生成其他数据 (退货/发货/促销等)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_other_data
CREATE PROCEDURE sp_gen_other_data()
BEGIN
    -- 促销活动
    INSERT INTO promotions (name, code, promotion_type, discount_value, min_purchase_amount,
        max_discount_amount, usage_limit, start_date, end_date, status)
    VALUES
    ('618年中大促', 'PROMO618', 'discount_pct', 15.00, 100.00, 500.00, 2000, '2025-06-01', '2025-06-30', 'active'),
    ('双11狂欢', 'PROMO1111', 'discount_pct', 20.00, 200.00, 1000.00, 5000, '2025-11-01', '2025-11-11', 'draft'),
    ('新人满减', 'NEWUSER', 'discount_amount', 30.00, 199.00, 30.00, 1000, '2025-01-01', '2025-12-31', 'active'),
    ('会员日折扣', 'VIPDAY', 'discount_pct', 10.00, 300.00, 200.00, 500, '2025-06-15', '2025-06-20', 'active'),
    ('清仓特卖', 'CLEARANCE', 'discount_pct', 40.00, 0.00, 500.00, 500, '2025-07-01', '2025-08-31', 'draft');

    -- 审批流
    INSERT INTO approval_workflows (workflow_name, workflow_code, target_type, description) VALUES
    ('采购审批', 'PURCHASE_APPROVAL', 'purchase_requisition', '请购单多级审批'),
    ('退货审批', 'RETURN_APPROVAL', 'sales_return', '销售退货审批'),
    ('折扣审批', 'DISCOUNT_APPROVAL', 'sales_order', '大额折扣审批'),
    ('报损审批', 'DAMAGE_APPROVAL', 'damage_report', '报损报废审批'),
    ('合同审批', 'CONTRACT_APPROVAL', 'contract', '合同审批流程');

    INSERT INTO approval_nodes (workflow_id, node_name, node_level, approver_type, approval_mode, timeout_hours) VALUES
    (1, '部门经理', 1, 'department_manager', 'single', 24),
    (1, '采购总监', 2, 'role', 'single', 24),
    (2, '店长审批', 1, 'department_manager', 'single', 24),
    (2, '大区经理', 2, 'role', 'single', 48),
    (3, '店长审批', 1, 'department_manager', 'single', 24),
    (4, '部门经理', 1, 'department_manager', 'single', 24),
    (4, '财务总监', 2, 'role', 'single', 48),
    (5, '法务审核', 1, 'role', 'single', 48),
    (5, '总经理', 2, 'role', 'single', 48);

    -- KPI
    INSERT INTO kpi_indicators (name, indicator_type, unit, target_direction, target_value, weight) VALUES
    ('月销售额', 'sales', '元', 'higher_better', 500000, 0.30),
    ('客户回款率', 'financial', '%', 'higher_better', 95, 0.15),
    ('库存周转天数', 'operational', '天', 'lower_better', 30, 0.15),
    ('客户满意度', 'customer', '分', 'higher_better', 4.5, 0.15),
    ('退货率', 'operational', '%', 'lower_better', 5, 0.10),
    ('员工离职率', 'hr', '%', 'lower_better', 10, 0.15);

    -- 汇率
    INSERT INTO exchange_rates (from_currency, to_currency, rate_date, rate, rate_source) VALUES
    ('USD', 'CNY', CURDATE(), 7.25, 'BOC'),
    ('EUR', 'CNY', CURDATE(), 7.90, 'BOC'),
    ('JPY', 'CNY', CURDATE(), 0.049, 'BOC'),
    ('GBP', 'CNY', CURDATE(), 9.15, 'BOC'),
    ('HKD', 'CNY', CURDATE(), 0.93, 'BOC');

    -- 质检标准 (随机选50个商品)
    INSERT INTO inspection_standards (product_id, standard_name, sampling_method, sample_size, aql_level)
    SELECT id, CONCAT(name, '-检验标准'), 'gb2828', FLOOR(10 + RAND() * 30), ROUND(0.5 + RAND() * 2.0, 1)
    FROM products ORDER BY RAND() LIMIT 50;

    SELECT CONCAT('其他数据生成完成') AS result;
END//



SET autocommit = 1;
SET sql_log_bin = 1;

-- 执行: CALL sp_generate_massive_data();
-- relation-detector-fixture-end
