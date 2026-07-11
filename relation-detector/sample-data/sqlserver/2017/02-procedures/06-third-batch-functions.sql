-- ============================================================
-- SQL Server natural business table-valued functions.
-- These are business reporting functions, not relation coverage fixtures.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.fn_contract_milestone_status
CREATE OR ALTER FUNCTION [dbo].[fn_contract_milestone_status]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[id] AS [contract_id], cm.[id] AS [milestone_id], c.[party_id], cm.[amount], cm.[status] FROM [dbo].[contracts] AS c INNER JOIN [dbo].[contract_milestones] AS cm ON cm.[contract_id] = c.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_quality_inspection_overview
CREATE OR ALTER FUNCTION [dbo].[fn_quality_inspection_overview]()
RETURNS TABLE
AS
RETURN (
    SELECT ir.[id] AS [inspection_report_id], ir.[product_id], ir.[batch_id], ir.[standard_id], ist.[sample_size], ir.[inspector_id] FROM [dbo].[inspection_reports] AS ir LEFT JOIN [dbo].[inspection_standards] AS ist ON ist.[id] = ir.[standard_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_project_cost_summary
CREATE OR ALTER FUNCTION [dbo].[fn_project_cost_summary]()
RETURNS TABLE
AS
RETURN (
    SELECT p.[id] AS [project_id], p.[manager_id], SUM(pc.[amount]) AS [actual_cost] FROM [dbo].[projects] AS p LEFT JOIN [dbo].[project_costs] AS pc ON pc.[project_id] = p.[id] GROUP BY p.[id], p.[manager_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_ar_aging_by_customer
CREATE OR ALTER FUNCTION [dbo].[fn_ar_aging_by_customer]()
RETURNS TABLE
AS
RETURN (
    SELECT a.[customer_id], a.[order_id], SUM(a.[invoice_amount]) AS [invoice_amount], SUM(a.[paid_amount]) AS [paid_amount] FROM [dbo].[ar_aging_snapshots] AS a GROUP BY a.[customer_id], a.[order_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_ap_aging_by_supplier
CREATE OR ALTER FUNCTION [dbo].[fn_ap_aging_by_supplier]()
RETURNS TABLE
AS
RETURN (
    SELECT a.[supplier_id], a.[order_id], SUM(a.[invoice_amount]) AS [invoice_amount], SUM(a.[paid_amount]) AS [paid_amount] FROM [dbo].[ap_aging_snapshots] AS a GROUP BY a.[supplier_id], a.[order_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_tax_invoice_audit
CREATE OR ALTER FUNCTION [dbo].[fn_tax_invoice_audit]()
RETURNS TABLE
AS
RETURN (
    SELECT ti.[id] AS [tax_invoice_id], ti.[verified_by], tf.[prepared_by],
           ti.[amount_including_tax], ti.[tax_amount]
    FROM [dbo].[tax_invoices] AS ti
    LEFT JOIN [dbo].[tax_filings] AS tf ON tf.[tax_period] = ti.[tax_period]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_approval_workflow_nodes
CREATE OR ALTER FUNCTION [dbo].[fn_approval_workflow_nodes]()
RETURNS TABLE
AS
RETURN (
    SELECT aw.[id] AS [workflow_id], an.[id] AS [node_id], an.[approver_id], an.[node_level] FROM [dbo].[approval_workflows] AS aw INNER JOIN [dbo].[approval_nodes] AS an ON an.[workflow_id] = aw.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_serial_warranty_lookup
CREATE OR ALTER FUNCTION [dbo].[fn_serial_warranty_lookup]()
RETURNS TABLE
AS
RETURN (
    SELECT sn.[id] AS [serial_id], sn.[product_id], sn.[batch_id], sn.[warehouse_id], sn.[status] FROM [dbo].[serial_numbers] AS sn
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_consignment_inventory_status
CREATE OR ALTER FUNCTION [dbo].[fn_consignment_inventory_status]()
RETURNS TABLE
AS
RETURN (
    SELECT ci.[customer_id], ci.[product_id], ci.[batch_id], ci.[consigned_qty], ci.[consumed_qty] FROM [dbo].[consignment_inventory] AS ci
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_employee_performance_kpi
CREATE OR ALTER FUNCTION [dbo].[fn_employee_performance_kpi]()
RETURNS TABLE
AS
RETURN (
    SELECT pr.[employee_id], pr.[reviewer_id], k.[id] AS [kpi_id], k.[target_value], pr.[overall_score] FROM [dbo].[performance_reviews] AS pr LEFT JOIN [dbo].[kpi_indicators] AS k ON k.[applicable_role_id] = pr.[employee_id]
);
-- relation-detector-fixture-end
