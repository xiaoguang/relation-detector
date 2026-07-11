-- Portable ERP schema generated from sample-data/mysql/8.0 object inventory.
-- SQL intentionally uses a common portable subset for relation-detector common token-event golden.

CREATE TABLE departments (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  name VARCHAR,
  code VARCHAR UNIQUE,
  manager_id BIGINT,
  budget DECIMAL,
  headcount_plan BIGINT,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES departments (id),
  CONSTRAINT fk_dept_manager FOREIGN KEY (manager_id) REFERENCES employees (id),
  INDEX idx_parent (parent_id),
  INDEX idx_status (status)
);

CREATE TABLE positions (
  id BIGINT PRIMARY KEY,
  department_id BIGINT,
  name VARCHAR,
  code VARCHAR,
  level BIGINT,
  min_salary DECIMAL,
  max_salary DECIMAL,
  headcount BIGINT,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_pos_dept FOREIGN KEY (department_id) REFERENCES departments (id),
  UNIQUE uk_dept_code (department_id, code),
  INDEX idx_level (level)
);

CREATE TABLE employees (
  id BIGINT PRIMARY KEY,
  employee_no VARCHAR UNIQUE,
  name VARCHAR,
  gender VARCHAR,
  id_card VARCHAR UNIQUE,
  phone VARCHAR,
  email VARCHAR,
  birth_date DATE,
  hire_date DATE,
  department_id BIGINT,
  position_id BIGINT,
  manager_id BIGINT,
  salary DECIMAL,
  social_security_base DECIMAL,
  housing_fund_base DECIMAL,
  bank_name VARCHAR,
  bank_account VARCHAR,
  status VARCHAR,
  resignation_date DATE,
  resignation_reason VARCHAR,
  address VARCHAR,
  emergency_contact VARCHAR,
  emergency_phone VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_emp_pos FOREIGN KEY (position_id) REFERENCES positions (id),
  CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees (id),
  INDEX idx_dept (department_id),
  INDEX idx_position (position_id)
);

CREATE TABLE employee_salary_log (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  old_salary DECIMAL,
  new_salary DECIMAL,
  change_reason VARCHAR,
  effective_date DATE,
  approved_by BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT fk_sal_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  INDEX idx_employee (employee_id),
  INDEX idx_effective_date (effective_date)
);

CREATE TABLE attendance (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  attendance_date DATE,
  clock_in TIMESTAMP,
  clock_out TIMESTAMP,
  status TIMESTAMP,
  late_minutes BIGINT,
  early_minutes BIGINT,
  overtime_hours DECIMAL,
  remark VARCHAR,
  CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  UNIQUE uk_emp_date (employee_id, attendance_date),
  INDEX idx_date (attendance_date)
);

CREATE TABLE leave_records (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  leave_type VARCHAR,
  start_date DATE,
  end_date DATE,
  days DECIMAL,
  reason VARCHAR,
  status VARCHAR,
  approved_by BIGINT,
  approved_at TIMESTAMP,
  created_at TIMESTAMP,
  CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  INDEX idx_emp (employee_id),
  INDEX idx_date_range (start_date, end_date)
);

CREATE TABLE roles (
  id BIGINT PRIMARY KEY,
  name VARCHAR UNIQUE,
  code VARCHAR UNIQUE,
  description VARCHAR,
  is_system BOOLEAN,
  created_at TIMESTAMP
);

CREATE TABLE permissions (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  name VARCHAR,
  code VARCHAR UNIQUE,
  resource_type VARCHAR,
  resource_path VARCHAR,
  action VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_perm_parent FOREIGN KEY (parent_id) REFERENCES permissions (id),
  INDEX idx_parent (parent_id),
  INDEX idx_resource (resource_type)
);

CREATE TABLE role_permissions (
  id BIGINT PRIMARY KEY,
  role_id BIGINT,
  permission_id BIGINT,
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id),
  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions (id),
  UNIQUE uk_role_perm (role_id, permission_id)
);

CREATE TABLE employee_roles (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  role_id BIGINT,
  granted_by BIGINT,
  granted_at TIMESTAMP,
  expires_at TIMESTAMP,
  CONSTRAINT fk_er_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_er_role FOREIGN KEY (role_id) REFERENCES roles (id),
  UNIQUE uk_emp_role (employee_id, role_id)
);

CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  action VARCHAR,
  target_type VARCHAR,
  target_id BIGINT,
  old_value VARCHAR,
  new_value VARCHAR,
  ip_address VARCHAR,
  user_agent VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  INDEX idx_employee (employee_id),
  INDEX idx_target (target_type, target_id)
);

CREATE TABLE product_categories (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  name VARCHAR,
  code VARCHAR UNIQUE,
  description VARCHAR,
  sort_order BIGINT,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES product_categories (id),
  INDEX idx_parent (parent_id)
);

CREATE TABLE suppliers (
  id BIGINT PRIMARY KEY,
  name VARCHAR,
  code VARCHAR UNIQUE,
  contact_person VARCHAR,
  phone VARCHAR,
  email VARCHAR,
  address VARCHAR,
   VARCHAR,
  city VARCHAR,
  district VARCHAR,
  latitude DECIMAL,
  longitude DECIMAL,
  bank_account VARCHAR,
  tax_id VARCHAR,
  credit_level VARCHAR,
  cooperation_status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_status (cooperation_status),
  INDEX idx_city (city)
);

CREATE TABLE products (
  id BIGINT PRIMARY KEY,
  sku VARCHAR UNIQUE,
  name VARCHAR,
  category_id BIGINT,
  unit VARCHAR,
  spec VARCHAR,
  brand VARCHAR,
  barcode VARCHAR,
  purchase_price DECIMAL,
  wholesale_price DECIMAL,
  retail_price DECIMAL,
  min_stock BIGINT,
  max_stock BIGINT,
  batch_managed BOOLEAN,
  shelf_life_days BIGINT,
  weight_kg DECIMAL,
  volume_m3 DECIMAL,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES product_categories (id),
  INDEX idx_category (category_id),
  INDEX idx_status (status)
);

CREATE TABLE supplier_products (
  id BIGINT PRIMARY KEY,
  supplier_id BIGINT,
  product_id BIGINT,
  supplier_price DECIMAL,
  lead_time_days BIGINT,
  min_order_qty BIGINT,
  shipping_cost_per_km DECIMAL,
  return_rate DECIMAL,
  quality_score DECIMAL,
  is_preferred BOOLEAN,
  last_order_date DATE,
  total_order_count BIGINT,
  total_order_qty BIGINT,
  CONSTRAINT fk_sp_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  CONSTRAINT fk_sp_product FOREIGN KEY (product_id) REFERENCES products (id),
  UNIQUE uk_supplier_product (supplier_id, product_id),
  INDEX idx_product_price (product_id, supplier_price)
);

CREATE TABLE product_batches (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  batch_no VARCHAR,
  production_date DATE,
  expiry_date DATE,
  supplier_id BIGINT,
  purchase_price DECIMAL,
  initial_qty BIGINT,
  current_qty BIGINT,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  UNIQUE uk_product_batch (product_id, batch_no),
  INDEX idx_expiry (expiry_date)
);

CREATE TABLE warehouses (
  id BIGINT PRIMARY KEY,
  name VARCHAR,
  code VARCHAR UNIQUE,
  address VARCHAR,
  province VARCHAR,
  city VARCHAR,
  district VARCHAR,
  latitude DECIMAL,
  longitude DECIMAL,
  manager_id BIGINT,
  type VARCHAR,
  capacity_m3 DECIMAL,
  status BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT fk_wh_manager FOREIGN KEY (manager_id) REFERENCES employees (id),
  INDEX idx_type (type),
  INDEX idx_city (city)
);

CREATE TABLE inventory (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  batch_id BIGINT,
  warehouse_id BIGINT,
  shelf_location VARCHAR,
  quantity BIGINT,
  locked_quantity BIGINT,
  available_quantity BIGINT,
  last_stocktake_date DATE,
  updated_at TIMESTAMP,
  CONSTRAINT fk_inv_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_inv_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_inv_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  UNIQUE uk_product_batch_wh (product_id, batch_id, warehouse_id),
  INDEX idx_warehouse (warehouse_id)
);

CREATE TABLE inventory_transactions (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  batch_id BIGINT,
  warehouse_id BIGINT,
  transaction_type VARCHAR,
  quantity_change BIGINT,
  before_qty BIGINT,
  after_qty BIGINT,
  reference_type VARCHAR,
  reference_id BIGINT,
  operator_id BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_it_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_it_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_it_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  INDEX idx_product (product_id),
  INDEX idx_warehouse (warehouse_id)
);

CREATE TABLE purchase_requisitions (
  id BIGINT PRIMARY KEY,
  requisition_no VARCHAR UNIQUE,
  department_id BIGINT,
  requester_id BIGINT,
  requisition_date DATE,
  required_date DATE,
  urgency VARCHAR,
  total_amount DECIMAL,
  status VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_pr_dept FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_pr_requester FOREIGN KEY (requester_id) REFERENCES employees (id),
  INDEX idx_dept (department_id),
  INDEX idx_requester (requester_id)
);

CREATE TABLE purchase_requisition_items (
  id BIGINT PRIMARY KEY,
  requisition_id BIGINT,
  product_id BIGINT,
  quantity BIGINT,
  estimated_price DECIMAL,
  amount DECIMAL,
  remark VARCHAR,
  CONSTRAINT fk_pri_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions (id),
  CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products (id),
  INDEX idx_product (product_id)
);

CREATE TABLE purchase_orders (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR UNIQUE,
  supplier_id BIGINT,
  requisition_id BIGINT,
  department_id BIGINT,
  purchaser_id BIGINT,
  order_date DATE,
  expected_delivery_date DATE,
  actual_delivery_date DATE,
  total_amount DECIMAL,
  paid_amount DECIMAL,
  payment_terms VARCHAR,
  status VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  CONSTRAINT fk_po_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions (id),
  CONSTRAINT fk_po_purchaser FOREIGN KEY (purchaser_id) REFERENCES employees (id),
  INDEX idx_supplier (supplier_id),
  INDEX idx_requisition (requisition_id)
);

CREATE TABLE purchase_order_items (
  id BIGINT PRIMARY KEY,
  order_id BIGINT,
  product_id BIGINT,
  quantity BIGINT,
  unit_price DECIMAL,
  amount DECIMAL,
  received_qty BIGINT,
  returned_qty BIGINT,
  remark VARCHAR,
  CONSTRAINT fk_poi_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
  CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products (id),
  INDEX idx_product (product_id)
);

CREATE TABLE purchase_receipts (
  id BIGINT PRIMARY KEY,
  receipt_no VARCHAR UNIQUE,
  order_id BIGINT,
  warehouse_id BIGINT,
  receiver_id BIGINT,
  receipt_date DATE,
  batch_no VARCHAR,
  total_qty BIGINT,
  total_amount DECIMAL,
  status VARCHAR,
  inspection_result VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_prec_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
  CONSTRAINT fk_prec_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_prec_receiver FOREIGN KEY (receiver_id) REFERENCES employees (id),
  INDEX idx_order (order_id),
  INDEX idx_warehouse (warehouse_id)
);

CREATE TABLE purchase_receipt_items (
  id BIGINT PRIMARY KEY,
  receipt_id BIGINT,
  order_item_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  received_qty BIGINT,
  accepted_qty BIGINT,
  rejected_qty BIGINT,
  unit_price DECIMAL,
  production_date DATE,
  expiry_date DATE,
  remark VARCHAR,
  CONSTRAINT fk_preci_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts (id),
  CONSTRAINT fk_preci_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_preci_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_order_item (order_item_id),
  INDEX idx_product (product_id)
);

CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  code VARCHAR UNIQUE,
  name VARCHAR,
  type VARCHAR,
  contact_person VARCHAR,
  phone VARCHAR,
  email VARCHAR,
  address VARCHAR,
  credit_limit DECIMAL,
  credit_days BIGINT,
  balance DECIMAL,
  membership_level VARCHAR,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_type (type),
  INDEX idx_status (status)
);

CREATE TABLE sales_orders (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR UNIQUE,
  customer_id BIGINT,
  salesperson_id BIGINT,
  warehouse_id BIGINT,
  order_date DATE,
  delivery_date DATE,
  total_amount DECIMAL,
  discount_amount DECIMAL,
  paid_amount DECIMAL,
  tax_amount DECIMAL,
  payment_method VARCHAR,
  status VARCHAR,
  invoice_no VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_so_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_so_salesperson FOREIGN KEY (salesperson_id) REFERENCES employees (id),
  CONSTRAINT fk_so_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  INDEX idx_customer (customer_id),
  INDEX idx_salesperson (salesperson_id)
);

CREATE TABLE sales_order_items (
  id BIGINT PRIMARY KEY,
  order_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  quantity BIGINT,
  unit_price DECIMAL,
  discount DECIMAL,
  amount DECIMAL,
  returned_qty BIGINT,
  CONSTRAINT fk_soi_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_soi_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_soi_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_order (order_id),
  INDEX idx_product (product_id)
);

CREATE TABLE sales_returns (
  id BIGINT PRIMARY KEY,
  return_no VARCHAR UNIQUE,
  order_id BIGINT,
  customer_id BIGINT,
  warehouse_id BIGINT,
  handler_id BIGINT,
  return_date DATE,
  return_reason VARCHAR,
  return_type VARCHAR,
  total_amount DECIMAL,
  refund_amount DECIMAL,
  restock_fee DECIMAL,
  status VARCHAR,
  approved_by BIGINT,
  approved_at TIMESTAMP,
  refund_voucher_id BIGINT,
  return_shipping_fee DECIMAL,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_sr_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_sr_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_sr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_sr_handler FOREIGN KEY (handler_id) REFERENCES employees (id),
  CONSTRAINT fk_sr_approved FOREIGN KEY (approved_by) REFERENCES employees (id),
  CONSTRAINT fk_sr_refund_voucher FOREIGN KEY (refund_voucher_id) REFERENCES vouchers (id),
  INDEX idx_order (order_id),
  INDEX idx_customer (customer_id)
);

CREATE TABLE sales_return_items (
  id BIGINT PRIMARY KEY,
  return_id BIGINT,
  order_item_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  return_qty BIGINT,
  unit_price DECIMAL,
  amount DECIMAL,
  status VARCHAR,
  CONSTRAINT fk_sri_return FOREIGN KEY (return_id) REFERENCES sales_returns (id),
  CONSTRAINT fk_sri_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_sri_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_order_item (order_item_id)
);

CREATE TABLE purchase_returns (
  id BIGINT PRIMARY KEY,
  return_no VARCHAR UNIQUE,
  purchase_order_id BIGINT,
  purchase_receipt_id BIGINT,
  supplier_id BIGINT,
  warehouse_id BIGINT,
  handler_id BIGINT,
  return_date DATE,
  return_reason VARCHAR,
  return_type VARCHAR,
  total_amount DECIMAL,
  refund_received DECIMAL,
  shipping_fee DECIMAL,
  status VARCHAR,
  approved_by BIGINT,
  approved_at TIMESTAMP,
  refund_voucher_id BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_prt_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
  CONSTRAINT fk_prt_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts (id),
  CONSTRAINT fk_prt_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  CONSTRAINT fk_prt_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_prt_handler FOREIGN KEY (handler_id) REFERENCES employees (id),
  CONSTRAINT fk_prt_approved FOREIGN KEY (approved_by) REFERENCES employees (id),
  CONSTRAINT fk_prt_voucher FOREIGN KEY (refund_voucher_id) REFERENCES vouchers (id),
  INDEX idx_po (purchase_order_id),
  INDEX idx_supplier (supplier_id)
);

CREATE TABLE purchase_return_items (
  id BIGINT PRIMARY KEY,
  return_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  return_qty BIGINT,
  unit_price DECIMAL,
  amount DECIMAL,
  reason VARCHAR,
  CONSTRAINT fk_pri_return FOREIGN KEY (return_id) REFERENCES purchase_returns (id),
  CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_pri_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_product (product_id),
  INDEX idx_batch (batch_id)
);

CREATE TABLE damage_reports (
  id BIGINT PRIMARY KEY,
  report_no VARCHAR UNIQUE,
  warehouse_id BIGINT,
  report_type VARCHAR,
  report_date DATE,
  reported_by BIGINT,
  total_quantity BIGINT,
  total_loss_amount DECIMAL,
  status VARCHAR,
  approved_by BIGINT,
  approved_at TIMESTAMP,
  executed_by BIGINT,
  executed_at TIMESTAMP,
  voucher_id BIGINT,
  description VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_dr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_dr_reported FOREIGN KEY (reported_by) REFERENCES employees (id),
  CONSTRAINT fk_dr_approved FOREIGN KEY (approved_by) REFERENCES employees (id),
  CONSTRAINT fk_dr_executed FOREIGN KEY (executed_by) REFERENCES employees (id),
  CONSTRAINT fk_dr_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  INDEX idx_warehouse (warehouse_id),
  INDEX idx_type (report_type)
);

CREATE TABLE damage_report_items (
  id BIGINT PRIMARY KEY,
  report_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  quantity BIGINT,
  unit_cost DECIMAL,
  loss_amount DECIMAL,
  reason VARCHAR,
  CONSTRAINT fk_dri_report FOREIGN KEY (report_id) REFERENCES damage_reports (id),
  CONSTRAINT fk_dri_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_dri_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_product (product_id),
  INDEX idx_batch (batch_id)
);

CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,
  code VARCHAR UNIQUE,
  parent_id BIGINT,
  name VARCHAR,
  account_type VARCHAR,
  balance_direction VARCHAR,
  is_cash BOOLEAN,
  is_bank BOOLEAN,
  bank_name VARCHAR,
  bank_account VARCHAR,
  current_balance DECIMAL,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_acct_parent FOREIGN KEY (parent_id) REFERENCES accounts (id),
  INDEX idx_parent (parent_id),
  INDEX idx_type (account_type)
);

CREATE TABLE vouchers (
  id BIGINT PRIMARY KEY,
  voucher_no VARCHAR UNIQUE,
  voucher_date DATE,
  voucher_type VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  total_debit DECIMAL,
  total_credit DECIMAL,
  prepared_by BIGINT,
  reviewed_by BIGINT,
  posted_by BIGINT,
  status VARCHAR,
  summary VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_v_prepared FOREIGN KEY (prepared_by) REFERENCES employees (id),
  CONSTRAINT fk_v_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees (id),
  CONSTRAINT fk_v_posted FOREIGN KEY (posted_by) REFERENCES employees (id),
  INDEX idx_date (voucher_date),
  INDEX idx_type (voucher_type)
);

CREATE TABLE voucher_items (
  id BIGINT PRIMARY KEY,
  voucher_id BIGINT,
  account_id BIGINT,
  line_no BIGINT,
  direction VARCHAR,
  amount DECIMAL,
  summary VARCHAR,
  CONSTRAINT fk_vi_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  CONSTRAINT fk_vi_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  INDEX idx_voucher (voucher_id),
  INDEX idx_account (account_id)
);

CREATE TABLE cashier_journals (
  id BIGINT PRIMARY KEY,
  journal_no VARCHAR UNIQUE,
  journal_date DATE,
  account_id BIGINT,
  cashier_id BIGINT,
  journal_type VARCHAR,
  amount DECIMAL,
  counterparty VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  voucher_id BIGINT,
  bank_account VARCHAR,
  check_no VARCHAR,
  status VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_cj_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_cj_cashier FOREIGN KEY (cashier_id) REFERENCES employees (id),
  CONSTRAINT fk_cj_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  INDEX idx_date (journal_date),
  INDEX idx_account (account_id)
);

CREATE TABLE salary_payments (
  id BIGINT PRIMARY KEY,
  payment_no VARCHAR UNIQUE,
  employee_id BIGINT,
  payment_date DATE,
  salary_month VARCHAR,
  base_salary DECIMAL,
  overtime_pay DECIMAL,
  bonus DECIMAL,
  deduction DECIMAL,
  social_security_personal DECIMAL,
  housing_fund_personal DECIMAL,
  income_tax DECIMAL,
  net_pay DECIMAL,
  social_security_company DECIMAL,
  housing_fund_company DECIMAL,
  payment_method VARCHAR,
  status VARCHAR,
  paid_at TIMESTAMP,
  voucher_id BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_sp_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_sp_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  UNIQUE uk_emp_month (employee_id, salary_month),
  INDEX idx_date (payment_date)
);

CREATE TABLE reconciliations (
  id BIGINT PRIMARY KEY,
  recon_no VARCHAR UNIQUE,
  account_id BIGINT,
  recon_date DATE,
  period_start DATE,
  period_end DATE,
  book_balance DECIMAL,
  bank_balance DECIMAL,
  difference DECIMAL,
  unreconciled_income DECIMAL,
  unreconciled_expense DECIMAL,
  adjusted_balance DECIMAL,
  prepared_by BIGINT,
  reviewed_by BIGINT,
  status VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_recon_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_recon_prepared FOREIGN KEY (prepared_by) REFERENCES employees (id),
  CONSTRAINT fk_recon_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees (id),
  INDEX idx_account (account_id),
  INDEX idx_period (period_start, period_end)
);

CREATE TABLE reconciliation_items (
  id BIGINT PRIMARY KEY,
  reconciliation_id BIGINT,
  journal_id BIGINT,
  transaction_date DATE,
  description VARCHAR,
  debit_amount DECIMAL,
  credit_amount DECIMAL,
  is_matched BOOLEAN,
  matched_item_id BIGINT,
  difference_reason VARCHAR,
  CONSTRAINT fk_ri_recon FOREIGN KEY (reconciliation_id) REFERENCES reconciliations (id),
  INDEX idx_recon (reconciliation_id),
  INDEX idx_journal (journal_id)
);

CREATE TABLE settlements (
  id BIGINT PRIMARY KEY,
  settlement_no VARCHAR UNIQUE,
  settlement_type BIGINT,
  party_id BIGINT,
  settlement_date DATE,
  period_start DATE,
  period_end DATE,
  total_amount DECIMAL,
  settled_amount DECIMAL,
  unpaid_amount DECIMAL,
  payment_due_date DATE,
  payment_method VARCHAR,
  status VARCHAR,
  voucher_id BIGINT,
  prepared_by BIGINT,
  approved_by BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_settle_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  CONSTRAINT fk_settle_prepared FOREIGN KEY (prepared_by) REFERENCES employees (id),
  CONSTRAINT fk_settle_approved FOREIGN KEY (approved_by) REFERENCES employees (id),
  INDEX idx_party (settlement_type, party_id),
  INDEX idx_date (settlement_date)
);

CREATE TABLE settlement_items (
  id BIGINT PRIMARY KEY,
  settlement_id BIGINT,
  reference_type VARCHAR,
  reference_id BIGINT,
  amount DECIMAL,
  settled_amount DECIMAL,
  remark VARCHAR,
  CONSTRAINT fk_si_settlement FOREIGN KEY (settlement_id) REFERENCES settlements (id),
  INDEX idx_settlement (settlement_id),
  INDEX idx_reference (reference_type, reference_id)
);

CREATE TABLE shipments (
  id BIGINT PRIMARY KEY,
  shipment_no VARCHAR UNIQUE,
  order_id BIGINT,
  warehouse_id BIGINT,
  carrier VARCHAR,
  tracking_no VARCHAR,
  shipping_method VARCHAR,
  shipping_fee DECIMAL,
  package_count BIGINT,
  weight_kg DECIMAL,
  status VARCHAR,
  picker_id BIGINT,
  packer_id BIGINT,
  shipped_at TIMESTAMP,
  delivered_at TIMESTAMP,
  estimated_delivery_date DATE,
  actual_delivery_date DATE,
  from_address VARCHAR,
  to_address VARCHAR,
  receiver_name VARCHAR,
  receiver_phone VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_ship_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  INDEX idx_order (order_id),
  INDEX idx_tracking (tracking_no)
);

CREATE TABLE shipping_tracks (
  id BIGINT PRIMARY KEY,
  shipment_id BIGINT,
  track_time TIMESTAMP,
  location VARCHAR,
  status_desc VARCHAR,
  operator VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_st_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id),
  INDEX idx_shipment (shipment_id),
  INDEX idx_track_time (track_time)
);

CREATE TABLE commission_rules (
  id BIGINT PRIMARY KEY,
  name VARCHAR,
  product_category_id BIGINT,
  min_amount DECIMAL,
  max_amount DECIMAL,
  commission_rate DECIMAL,
  bonus DECIMAL,
  effective_date DATE,
  expiry_date DATE,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_cr_category FOREIGN KEY (product_category_id) REFERENCES product_categories (id),
  INDEX idx_category (product_category_id),
  INDEX idx_effective (effective_date, expiry_date)
);

CREATE TABLE sales_commissions (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  order_id BIGINT,
  order_item_id BIGINT,
  period VARCHAR,
  base_amount DECIMAL,
  commission_rate DECIMAL,
  commission_amount DECIMAL,
  bonus DECIMAL,
  total_commission DECIMAL,
  status VARCHAR,
  calculated_at TIMESTAMP,
  paid_at TIMESTAMP,
  settlement_id BIGINT,
  CONSTRAINT fk_sc_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_sc_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  INDEX idx_employee (employee_id),
  INDEX idx_period (period)
);

CREATE TABLE promotions (
  id BIGINT PRIMARY KEY,
  name VARCHAR,
  code VARCHAR UNIQUE,
  promotion_type VARCHAR,
  discount_value DECIMAL,
  min_purchase_amount DECIMAL,
  max_discount_amount DECIMAL,
  usage_limit BIGINT,
  used_count BIGINT,
  start_date DATE,
  end_date DATE,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_date_range (start_date, end_date),
  INDEX idx_status (status)
);

CREATE TABLE promotion_products (
  id BIGINT PRIMARY KEY,
  promotion_id BIGINT,
  product_id BIGINT,
  category_id BIGINT,
  CONSTRAINT fk_pp_promo FOREIGN KEY (promotion_id) REFERENCES promotions (id),
  CONSTRAINT fk_pp_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_pp_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
  UNIQUE uk_promo_product (promotion_id, product_id, category_id)
);

CREATE TABLE promotion_usages (
  id BIGINT PRIMARY KEY,
  promotion_id BIGINT,
  order_id BIGINT,
  customer_id BIGINT,
  discount_applied DECIMAL,
  used_at TIMESTAMP,
  CONSTRAINT fk_pu_promo FOREIGN KEY (promotion_id) REFERENCES promotions (id),
  CONSTRAINT fk_pu_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_pu_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  UNIQUE uk_promo_order (promotion_id, order_id),
  INDEX idx_customer (customer_id)
);

CREATE TABLE invoices (
  id BIGINT PRIMARY KEY,
  invoice_no VARCHAR UNIQUE,
  invoice_type VARCHAR,
  supplier_id BIGINT,
  customer_id BIGINT,
  invoice_date DATE,
  due_date DATE,
  total_amount DECIMAL,
  tax_amount DECIMAL,
  tax_rate DECIMAL,
  status VARCHAR,
  verified_by BIGINT,
  verified_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_inv_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  CONSTRAINT fk_inv_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  INDEX idx_supplier (supplier_id),
  INDEX idx_customer (customer_id)
);

CREATE TABLE three_way_matching (
  id BIGINT PRIMARY KEY,
  invoice_id BIGINT,
  purchase_order_id BIGINT,
  purchase_receipt_id BIGINT,
  product_id BIGINT,
  po_quantity BIGINT,
  receipt_quantity BIGINT,
  invoice_quantity BIGINT,
  po_price DECIMAL,
  receipt_price DECIMAL,
  invoice_price DECIMAL,
  quantity_match BOOLEAN,
  price_match BOOLEAN,
  match_status VARCHAR,
  match_result VARCHAR,
  matched_by BIGINT,
  matched_at TIMESTAMP,
  CONSTRAINT fk_twm_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
  CONSTRAINT fk_twm_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
  CONSTRAINT fk_twm_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts (id),
  CONSTRAINT fk_twm_product FOREIGN KEY (product_id) REFERENCES products (id),
  INDEX idx_invoice (invoice_id),
  INDEX idx_po (purchase_order_id)
);

CREATE TABLE fixed_assets (
  id BIGINT PRIMARY KEY,
  asset_no VARCHAR UNIQUE,
  name VARCHAR,
  category VARCHAR,
  purchase_date DATE,
  purchase_amount DECIMAL,
  salvage_value DECIMAL,
  useful_life_months BIGINT,
  monthly_depreciation DECIMAL,
  accumulated_depreciation DECIMAL,
  net_book_value DECIMAL,
  department_id BIGINT,
  custodian_id BIGINT,
  location VARCHAR,
  status BIGINT,
  last_depreciation_date DATE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_fa_dept FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_fa_custodian FOREIGN KEY (custodian_id) REFERENCES employees (id),
  INDEX idx_dept (department_id),
  INDEX idx_category (category)
);

CREATE TABLE depreciation_log (
  id BIGINT PRIMARY KEY,
  asset_id BIGINT,
  depreciation_date DATE,
  depreciation_amount DECIMAL,
  before_accumulated DECIMAL,
  after_accumulated DECIMAL,
  before_net_value DECIMAL,
  after_net_value DECIMAL,
  voucher_id BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT fk_dl_asset FOREIGN KEY (asset_id) REFERENCES fixed_assets (id),
  UNIQUE uk_asset_date (asset_id, depreciation_date),
  INDEX idx_date (depreciation_date)
);

CREATE TABLE boms (
  id BIGINT PRIMARY KEY,
  parent_product_id BIGINT,
  child_product_id BIGINT,
  quantity DECIMAL,
  unit VARCHAR,
  scrap_rate DECIMAL,
  sort_order BIGINT,
  effective_date DATE,
  expiry_date DATE,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_bom_parent FOREIGN KEY (parent_product_id) REFERENCES products (id),
  CONSTRAINT fk_bom_child FOREIGN KEY (child_product_id) REFERENCES products (id),
  UNIQUE uk_parent_child (parent_product_id, child_product_id, effective_date),
  INDEX idx_child (child_product_id)
);

CREATE TABLE work_orders (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR UNIQUE,
  product_id BIGINT,
  bom_id BIGINT,
  planned_quantity BIGINT,
  completed_quantity BIGINT,
  rejected_quantity BIGINT,
  warehouse_id BIGINT,
  start_date DATE,
  due_date DATE,
  completed_date DATE,
  status VARCHAR,
  priority VARCHAR,
  released_by BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_wo_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_wo_bom FOREIGN KEY (bom_id) REFERENCES boms (id),
  CONSTRAINT fk_wo_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  INDEX idx_product (product_id),
  INDEX idx_status (status)
);

CREATE TABLE work_order_materials (
  id BIGINT PRIMARY KEY,
  work_order_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  required_qty DECIMAL,
  issued_qty DECIMAL,
  returned_qty DECIMAL,
  actual_consumed DECIMAL,
  unit VARCHAR,
  status VARCHAR,
  CONSTRAINT fk_wom_wo FOREIGN KEY (work_order_id) REFERENCES work_orders (id),
  CONSTRAINT fk_wom_product FOREIGN KEY (product_id) REFERENCES products (id),
  INDEX idx_wo (work_order_id),
  INDEX idx_product (product_id)
);

CREATE TABLE service_tickets (
  id BIGINT PRIMARY KEY,
  ticket_no VARCHAR UNIQUE,
  customer_id BIGINT,
  order_id BIGINT,
  product_id BIGINT,
  ticket_type BIGINT,
  priority VARCHAR,
  subject VARCHAR,
  description VARCHAR,
  status VARCHAR,
  assigned_to BIGINT,
  resolution VARCHAR,
  resolved_at TIMESTAMP,
  satisfaction_score BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_st_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_st_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_st_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_st_assigned FOREIGN KEY (assigned_to) REFERENCES employees (id),
  INDEX idx_customer (customer_id),
  INDEX idx_order (order_id)
);

CREATE TABLE contracts (
  id BIGINT PRIMARY KEY,
  contract_no VARCHAR UNIQUE,
  contract_type VARCHAR,
  party_type VARCHAR,
  party_id BIGINT,
  subject VARCHAR,
  total_amount DECIMAL,
  currency VARCHAR,
  signed_date DATE,
  start_date DATE,
  end_date DATE,
  payment_terms VARCHAR,
  delivery_terms VARCHAR,
  status VARCHAR,
  prepared_by BIGINT,
  approved_by BIGINT,
  signed_by VARCHAR,
  attachment_path VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_ct_prepared FOREIGN KEY (prepared_by) REFERENCES employees (id),
  CONSTRAINT fk_ct_approved FOREIGN KEY (approved_by) REFERENCES employees (id),
  INDEX idx_party (party_type, party_id),
  INDEX idx_status (status)
);

CREATE TABLE contract_milestones (
  id BIGINT PRIMARY KEY,
  contract_id BIGINT,
  milestone_name VARCHAR,
  milestone_type VARCHAR,
  planned_date DATE,
  actual_date DATE,
  amount DECIMAL,
  completion_pct DECIMAL,
  status VARCHAR,
  responsible_person BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_cm_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),
  INDEX idx_contract (contract_id),
  INDEX idx_planned_date (planned_date)
);

CREATE TABLE ar_aging_snapshots (
  id BIGINT PRIMARY KEY,
  snapshot_date DATE,
  customer_id BIGINT,
  order_id BIGINT,
  invoice_amount DECIMAL,
  paid_amount DECIMAL,
  outstanding_amount DECIMAL,
  due_date DATE,
  aging_days BIGINT,
  aging_bucket VARCHAR,
  bad_debt_provision DECIMAL,
  last_collection_date DATE,
  collection_notes VARCHAR,
  CONSTRAINT fk_ar_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_ar_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  INDEX idx_customer (customer_id),
  INDEX idx_aging_bucket (aging_bucket)
);

CREATE TABLE ap_aging_snapshots (
  id BIGINT PRIMARY KEY,
  snapshot_date DATE,
  supplier_id BIGINT,
  order_id BIGINT,
  invoice_amount DECIMAL,
  paid_amount DECIMAL,
  outstanding_amount DECIMAL,
  due_date DATE,
  aging_bucket VARCHAR,
  planned_payment_date DATE,
  CONSTRAINT fk_ap_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  CONSTRAINT fk_ap_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
  INDEX idx_supplier (supplier_id),
  INDEX idx_aging_bucket (aging_bucket)
);

CREATE TABLE tax_invoices (
  id BIGINT PRIMARY KEY,
  invoice_no VARCHAR UNIQUE,
  invoice_code VARCHAR,
  invoice_type VARCHAR,
  tax_direction VARCHAR,
  party_type VARCHAR,
  party_id BIGINT,
  invoice_date DATE,
  amount_excluding_tax DECIMAL,
  tax_rate DECIMAL,
  tax_amount DECIMAL,
  amount_including_tax DECIMAL,
  verification_status VARCHAR,
  verified_at TIMESTAMP,
  verified_by BIGINT,
  tax_period VARCHAR,
  deduction_period VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  status VARCHAR,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_ti_verified FOREIGN KEY (verified_by) REFERENCES employees (id),
  INDEX idx_party (party_type, party_id),
  INDEX idx_tax_period (tax_period)
);

CREATE TABLE tax_filings (
  id BIGINT PRIMARY KEY,
  tax_period VARCHAR,
  tax_type VARCHAR,
  output_tax DECIMAL,
  input_tax DECIMAL,
  input_tax_transfer DECIMAL,
  tax_payable DECIMAL,
  tax_paid DECIMAL,
  filing_date DATE,
  filing_deadline DATE,
  status VARCHAR,
  prepared_by BIGINT,
  voucher_id BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_tf_prepared FOREIGN KEY (prepared_by) REFERENCES employees (id),
  UNIQUE uk_period_type (tax_period, tax_type),
  INDEX idx_status (status)
);

CREATE TABLE inspection_standards (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  standard_name VARCHAR,
  inspection_items VARCHAR,
  sampling_method VARCHAR,
  sample_size BIGINT,
  aql_level DECIMAL,
  status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_is_product FOREIGN KEY (product_id) REFERENCES products (id),
  UNIQUE uk_product_standard (product_id, standard_name)
);

CREATE TABLE inspection_reports (
  id BIGINT PRIMARY KEY,
  report_no VARCHAR UNIQUE,
  inspection_type VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  standard_id BIGINT,
  sample_size BIGINT,
  inspected_qty BIGINT,
  qualified_qty BIGINT,
  defective_qty BIGINT,
  defect_rate DECIMAL,
  inspection_result VARCHAR,
  inspector_id BIGINT,
  inspection_date DATE,
  defect_description VARCHAR,
  disposition VARCHAR,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_ir_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_ir_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_ir_standard FOREIGN KEY (standard_id) REFERENCES inspection_standards (id),
  CONSTRAINT fk_ir_inspector FOREIGN KEY (inspector_id) REFERENCES employees (id),
  INDEX idx_product (product_id),
  INDEX idx_batch (batch_id)
);

CREATE TABLE approval_workflows (
  id BIGINT PRIMARY KEY,
  workflow_name VARCHAR,
  workflow_code VARCHAR UNIQUE,
  target_type VARCHAR,
  description VARCHAR,
  is_active BOOLEAN,
  created_at TIMESTAMP
);

CREATE TABLE approval_nodes (
  id BIGINT PRIMARY KEY,
  workflow_id BIGINT,
  node_name VARCHAR,
  node_level BIGINT,
  approver_type VARCHAR,
  approver_id BIGINT,
  approval_mode VARCHAR,
  timeout_hours BIGINT,
  can_delegate BOOLEAN,
  created_at TIMESTAMP,
  CONSTRAINT fk_an_workflow FOREIGN KEY (workflow_id) REFERENCES approval_workflows (id),
  UNIQUE uk_workflow_level (workflow_id, node_level)
);

CREATE TABLE approval_instances (
  id BIGINT PRIMARY KEY,
  instance_no VARCHAR UNIQUE,
  workflow_id BIGINT,
  target_type VARCHAR,
  target_id BIGINT,
  target_summary VARCHAR,
  current_node_level BIGINT,
  total_nodes BIGINT,
  submitted_by BIGINT,
  submitted_at TIMESTAMP,
  status TIMESTAMP,
  completed_at TIMESTAMP,
  remark VARCHAR,
  CONSTRAINT fk_ai_workflow FOREIGN KEY (workflow_id) REFERENCES approval_workflows (id),
  CONSTRAINT fk_ai_submitted FOREIGN KEY (submitted_by) REFERENCES employees (id),
  INDEX idx_target (target_type, target_id),
  INDEX idx_status (status)
);

CREATE TABLE approval_records (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  node_id BIGINT,
  approver_id BIGINT,
  action VARCHAR,
  comment VARCHAR,
  action_at TIMESTAMP,
  delegated_to BIGINT,
  CONSTRAINT fk_ar_instance FOREIGN KEY (instance_id) REFERENCES approval_instances (id),
  CONSTRAINT fk_ar_node FOREIGN KEY (node_id) REFERENCES approval_nodes (id),
  CONSTRAINT fk_ar_approver FOREIGN KEY (approver_id) REFERENCES employees (id),
  INDEX idx_instance (instance_id)
);

CREATE TABLE cash_flow_forecasts (
  id BIGINT PRIMARY KEY,
  forecast_date DATE,
  forecast_type VARCHAR,
  beginning_balance DECIMAL,
  expected_collections DECIMAL,
  expected_payments DECIMAL,
  expected_salary DECIMAL,
  expected_tax DECIMAL,
  other_income DECIMAL,
  other_expense DECIMAL,
  net_cash_flow DECIMAL,
  ending_balance DECIMAL,
  created_at TIMESTAMP,
  UNIQUE uk_forecast_date_type (forecast_date, forecast_type),
  INDEX idx_forecast_date (forecast_date)
);

CREATE TABLE projects (
  id BIGINT PRIMARY KEY,
  project_no VARCHAR UNIQUE,
  name VARCHAR,
  project_type BIGINT,
  department_id BIGINT,
  manager_id BIGINT,
  budget DECIMAL,
  start_date DATE,
  planned_end_date DATE,
  actual_end_date DATE,
  status VARCHAR,
  priority VARCHAR,
  description VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_proj_dept FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_proj_manager FOREIGN KEY (manager_id) REFERENCES employees (id),
  INDEX idx_dept (department_id),
  INDEX idx_manager (manager_id)
);

CREATE TABLE project_costs (
  id BIGINT PRIMARY KEY,
  project_id BIGINT,
  cost_type VARCHAR,
  cost_date DATE,
  amount DECIMAL,
  description VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  recorded_by BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT fk_pc_project FOREIGN KEY (project_id) REFERENCES projects (id),
  INDEX idx_project (project_id),
  INDEX idx_date (cost_date)
);

CREATE TABLE exchange_rates (
  id BIGINT PRIMARY KEY,
  from_currency VARCHAR,
  to_currency VARCHAR,
  rate_date DATE,
  rate DECIMAL,
  rate_source VARCHAR,
  created_at TIMESTAMP,
  UNIQUE uk_currency_date (from_currency, to_currency, rate_date),
  INDEX idx_date (rate_date)
);

CREATE TABLE foreign_currency_accounts (
  id BIGINT PRIMARY KEY,
  account_id BIGINT,
  currency VARCHAR,
  original_balance DECIMAL,
  cny_equivalent DECIMAL,
  last_revaluation_date DATE,
  created_at TIMESTAMP,
  CONSTRAINT fk_fca_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  UNIQUE uk_account_currency (account_id, currency)
);

CREATE TABLE performance_reviews (
  id BIGINT PRIMARY KEY,
  review_no VARCHAR UNIQUE,
  employee_id BIGINT,
  reviewer_id BIGINT,
  review_period VARCHAR,
  review_type VARCHAR,
   DECIMAL,
  competency_score DECIMAL,
  attitude_score DECIMAL,
  attendance_score DECIMAL,
  grade VARCHAR,
  self_assessment VARCHAR,
  reviewer_comment VARCHAR,
  improvement_plan VARCHAR,
  salary_adjustment DECIMAL,
  promotion_recommendation BOOLEAN,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_pr_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees (id),
  UNIQUE uk_emp_period_type (employee_id, review_period, review_type),
  INDEX idx_reviewer (reviewer_id)
);

CREATE TABLE kpi_indicators (
  id BIGINT PRIMARY KEY,
  name VARCHAR,
  indicator_type VARCHAR,
  unit VARCHAR,
  target_direction VARCHAR,
  target_value DECIMAL,
  target_min DECIMAL,
  target_max DECIMAL,
  weight DECIMAL,
  applicable_role_id BIGINT,
  department_id BIGINT,
  status VARCHAR,
  created_at TIMESTAMP,
  INDEX idx_role (applicable_role_id),
  INDEX idx_dept (department_id)
);

CREATE TABLE serial_numbers (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  batch_id BIGINT,
  serial_no VARCHAR,
  status VARCHAR,
  warehouse_id BIGINT,
  purchase_receipt_id BIGINT,
  sales_order_id BIGINT,
  return_id BIGINT,
  current_owner_id BIGINT,
  warranty_start DATE,
  warranty_end DATE,
  last_scan_date TIMESTAMP,
  last_scan_location VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_sn_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_sn_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_sn_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  UNIQUE uk_serial (product_id, serial_no),
  INDEX idx_status (status)
);

CREATE TABLE serial_number_logs (
  id BIGINT PRIMARY KEY,
  serial_number_id BIGINT,
  event_type VARCHAR,
  from_status VARCHAR,
  to_status VARCHAR,
  from_location VARCHAR,
  to_location VARCHAR,
  reference_type VARCHAR,
  reference_id BIGINT,
  operator_id BIGINT,
  event_time TIMESTAMP,
  remark VARCHAR,
  CONSTRAINT fk_snl_sn FOREIGN KEY (serial_number_id) REFERENCES serial_numbers (id),
  INDEX idx_sn (serial_number_id),
  INDEX idx_event_time (event_time)
);

CREATE TABLE consignment_inventory (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  batch_id BIGINT,
  customer_id BIGINT,
  consigned_qty BIGINT,
  consumed_qty BIGINT,
  available_qty BIGINT,
  unit_price DECIMAL,
  consigned_date DATE,
  last_consumed_date DATE,
  settlement_period VARCHAR,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT fk_ci_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_ci_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_ci_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  UNIQUE uk_product_batch_customer (product_id, batch_id, customer_id),
  INDEX idx_customer (customer_id)
);

CREATE TABLE consignment_consumptions (
  id BIGINT PRIMARY KEY,
  consignment_id BIGINT,
  consumed_qty BIGINT,
  consumed_date DATE,
  unit_price DECIMAL,
  amount DECIMAL,
  confirmed_by_customer BOOLEAN,
  sales_order_id BIGINT,
  remark VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_cc_consignment FOREIGN KEY (consignment_id) REFERENCES consignment_inventory (id),
  INDEX idx_consignment (consignment_id),
  INDEX idx_date (consumed_date)
);

CREATE TABLE price_change_logs (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  price_type VARCHAR,
  old_price DECIMAL,
  new_price DECIMAL,
  change_reason VARCHAR,
  effective_date DATE,
  changed_by BIGINT,
  approved_by BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT fk_pcl_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_pcl_changed FOREIGN KEY (changed_by) REFERENCES employees (id),
  INDEX idx_product (product_id),
  INDEX idx_effective_date (effective_date)
);

CREATE TABLE tenants (
  id BIGINT PRIMARY KEY,
  tenant_code VARCHAR UNIQUE,
  tenant_name VARCHAR,
  legal_entity_name VARCHAR,
  tax_no VARCHAR,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE ledger_books (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT,
  book_code VARCHAR,
  book_name VARCHAR,
  base_currency VARCHAR,
  fiscal_year_start_month BIGINT,
  is_default BOOLEAN,
  status VARCHAR,
  CONSTRAINT fk_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  UNIQUE uk_ledger_book (tenant_id, book_code)
);

CREATE TABLE customer_addresses (
  id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  address_type VARCHAR,
  receiver_name VARCHAR,
  receiver_phone VARCHAR,
  province VARCHAR,
  city VARCHAR,
  district VARCHAR,
  street VARCHAR,
  postal_code VARCHAR,
  is_default BOOLEAN,
  CONSTRAINT fk_customer_address_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  INDEX idx_customer_address_city (province, city),
  INDEX idx_customer_address_default (customer_id, is_default)
);

CREATE TABLE supplier_addresses (
  id BIGINT PRIMARY KEY,
  supplier_id BIGINT,
  address_type VARCHAR,
  contact_name VARCHAR,
  contact_phone VARCHAR,
  province VARCHAR,
  city VARCHAR,
  district VARCHAR,
  street VARCHAR,
  postal_code VARCHAR,
  is_default BOOLEAN,
  CONSTRAINT fk_supplier_address_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
  INDEX idx_supplier_address_city (province, city),
  INDEX idx_supplier_address_default (supplier_id, is_default)
);

CREATE TABLE tax_rates (
  id BIGINT PRIMARY KEY,
  tax_code VARCHAR UNIQUE,
  tax_name VARCHAR,
  tax_type VARCHAR,
  rate DECIMAL,
  effective_from DATE,
  effective_to DATE,
  status VARCHAR,
  INDEX idx_tax_rate_effective (tax_type, effective_from, effective_to)
);

CREATE TABLE accounting_periods (
  id BIGINT PRIMARY KEY,
  ledger_book_id BIGINT,
  period_code VARCHAR,
  period_start DATE,
  period_end DATE,
  status VARCHAR,
  closed_by BIGINT,
  closed_at TIMESTAMP,
  CONSTRAINT fk_period_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books (id),
  CONSTRAINT fk_period_closed_by FOREIGN KEY (closed_by) REFERENCES employees (id),
  UNIQUE uk_accounting_period (ledger_book_id, period_code)
);

CREATE TABLE period_close_jobs (
  id BIGINT PRIMARY KEY,
  period_id BIGINT,
  job_code VARCHAR,
  job_name VARCHAR,
  status VARCHAR,
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  message VARCHAR,
  CONSTRAINT fk_period_close_job_period FOREIGN KEY (period_id) REFERENCES accounting_periods (id),
  UNIQUE uk_period_job (period_id, job_code)
);

CREATE TABLE payment_receipts (
  id BIGINT PRIMARY KEY,
  receipt_no VARCHAR UNIQUE,
  receipt_type BIGINT,
  party_type BIGINT,
  party_id BIGINT,
  account_id BIGINT,
  receipt_date DATE,
  amount DECIMAL,
  currency VARCHAR,
  status VARCHAR,
  handled_by BIGINT,
  confirmed_at TIMESTAMP,
  remark VARCHAR,
  CONSTRAINT fk_payment_receipt_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_payment_receipt_handler FOREIGN KEY (handled_by) REFERENCES employees (id),
  INDEX idx_payment_receipt_party (party_type, party_id),
  INDEX idx_payment_receipt_date (receipt_date)
);

CREATE TABLE payment_receipt_allocations (
  id BIGINT PRIMARY KEY,
  receipt_id BIGINT,
  reference_type VARCHAR,
  reference_id BIGINT,
  allocated_amount DECIMAL,
  CONSTRAINT fk_payment_allocation_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipts (id),
  INDEX idx_payment_allocation_reference (reference_type, reference_id)
);

CREATE TABLE payments (
  id BIGINT PRIMARY KEY,
  payment_no VARCHAR UNIQUE,
  customer_id BIGINT,
  order_id BIGINT,
  receipt_id BIGINT,
  journal_id BIGINT,
  payment_date DATE,
  amount DECIMAL,
  currency VARCHAR,
  payment_method VARCHAR,
  payment_status VARCHAR,
  reconciliation_status VARCHAR,
  created_at TIMESTAMP,
  CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_orders (id),
  CONSTRAINT fk_payment_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipts (id),
  CONSTRAINT fk_payment_journal FOREIGN KEY (journal_id) REFERENCES cashier_journals (id),
  INDEX idx_payment_customer (customer_id),
  INDEX idx_payment_order (order_id)
);

CREATE TABLE stocktakes (
  id BIGINT PRIMARY KEY,
  stocktake_no VARCHAR UNIQUE,
  warehouse_id BIGINT,
  stocktake_date DATE,
  stocktake_type VARCHAR,
  status VARCHAR,
  created_by BIGINT,
  reviewed_by BIGINT,
  posted_at TIMESTAMP,
  CONSTRAINT fk_stocktake_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_stocktake_created_by FOREIGN KEY (created_by) REFERENCES employees (id),
  CONSTRAINT fk_stocktake_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees (id)
);

CREATE TABLE stocktake_items (
  id BIGINT PRIMARY KEY,
  stocktake_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  book_quantity BIGINT,
  counted_quantity BIGINT,
  variance_quantity BIGINT,
  variance_reason VARCHAR,
  CONSTRAINT fk_stocktake_item_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktakes (id),
  CONSTRAINT fk_stocktake_item_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_stocktake_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  INDEX idx_stocktake_item_product (product_id, batch_id)
);

CREATE TABLE stock_transfers (
  id BIGINT PRIMARY KEY,
  transfer_no VARCHAR UNIQUE,
  from_warehouse_id BIGINT,
  to_warehouse_id BIGINT,
  requested_by BIGINT,
  approved_by BIGINT,
  transfer_date DATE,
  status VARCHAR,
  CONSTRAINT fk_transfer_from_wh FOREIGN KEY (from_warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_transfer_to_wh FOREIGN KEY (to_warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_transfer_requested_by FOREIGN KEY (requested_by) REFERENCES employees (id),
  CONSTRAINT fk_transfer_approved_by FOREIGN KEY (approved_by) REFERENCES employees (id)
);

CREATE TABLE stock_transfer_items (
  id BIGINT PRIMARY KEY,
  transfer_id BIGINT,
  product_id BIGINT,
  batch_id BIGINT,
  quantity BIGINT,
  received_quantity BIGINT,
  CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers (id),
  CONSTRAINT fk_transfer_item_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_transfer_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id)
);

CREATE TABLE inventory_reservations (
  id BIGINT PRIMARY KEY,
  reservation_no VARCHAR UNIQUE,
  product_id BIGINT,
  batch_id BIGINT,
  warehouse_id BIGINT,
  source_type VARCHAR,
  source_id BIGINT,
  reserved_quantity BIGINT,
  released_quantity BIGINT,
  status VARCHAR,
  expires_at TIMESTAMP,
  CONSTRAINT fk_inv_res_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_inv_res_batch FOREIGN KEY (batch_id) REFERENCES product_batches (id),
  CONSTRAINT fk_inv_res_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  INDEX idx_inv_res_source (source_type, source_id)
);

CREATE TABLE production_routes (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  route_code VARCHAR,
  route_name VARCHAR,
  version_no VARCHAR,
  status VARCHAR,
  CONSTRAINT fk_route_product FOREIGN KEY (product_id) REFERENCES products (id),
  UNIQUE uk_product_route (product_id, route_code, version_no)
);

CREATE TABLE production_operations (
  id BIGINT PRIMARY KEY,
  route_id BIGINT,
  operation_no BIGINT,
  operation_name VARCHAR,
  work_center VARCHAR,
  standard_minutes DECIMAL,
  predecessor_operation_id BIGINT,
  CONSTRAINT fk_operation_route FOREIGN KEY (route_id) REFERENCES production_routes (id),
  CONSTRAINT fk_operation_predecessor FOREIGN KEY (predecessor_operation_id) REFERENCES production_operations (id),
  UNIQUE uk_route_operation (route_id, operation_no)
);

CREATE TABLE employee_shifts (
  id BIGINT PRIMARY KEY,
  shift_code VARCHAR UNIQUE,
  shift_name VARCHAR,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  planned_hours DECIMAL,
  is_night_shift BOOLEAN
);

CREATE TABLE employee_shift_assignments (
  id BIGINT PRIMARY KEY,
  employee_id BIGINT,
  shift_id BIGINT,
  work_date DATE,
  warehouse_id BIGINT,
  status VARCHAR,
  CONSTRAINT fk_shift_assignment_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_shift_assignment_shift FOREIGN KEY (shift_id) REFERENCES employee_shifts (id),
  CONSTRAINT fk_shift_assignment_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  UNIQUE uk_employee_shift_date (employee_id, work_date)
);
