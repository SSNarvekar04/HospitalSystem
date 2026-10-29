-- ============================================================================
-- Hospital Management System - ERP Modules Migration Script (MySQL 8)
-- Database : hospital_system
--
-- Adds tables for three ERP modules:
--   Module 1 : HR & Payroll
--   Module 2 : Inventory & Pharmacy
--   Module 3 : Finance & Accounting
--
-- IMPORTANT
--   * Existing tables (login, patient_info1, bookappointment2, bill,
--     emp_info, department, doctors, patient, disease, patient_disease)
--     are NOT dropped, truncated or modified.
--   * All statements are safe to run more than once (CREATE ... IF NOT EXISTS,
--     INSERT IGNORE for seed data).
--
-- Run with:
--   mysql -u root -p hospital_system < erp_migration.sql
-- ============================================================================

USE hospital_system;

-- ============================================================================
-- MODULE 1 : HR & PAYROLL
-- ============================================================================

-- Hospital departments (used by HR module, separate from legacy `department`
-- table which has no primary key and is only used by the read-only screen).
CREATE TABLE IF NOT EXISTS hr_department (
    department_id   INT NOT NULL AUTO_INCREMENT,
    department_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (department_id),
    UNIQUE KEY uk_hr_dept_name (department_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Employee designations (e.g. Doctor, Nurse, Receptionist, Pharmacist, ...)
CREATE TABLE IF NOT EXISTS hr_designation (
    designation_id   INT NOT NULL AUTO_INCREMENT,
    designation_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (designation_id),
    UNIQUE KEY uk_hr_dsg_name (designation_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Employees master data. employee_id is a user supplied alphanumeric code.
CREATE TABLE IF NOT EXISTS employee (
    employee_id    VARCHAR(20)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    contact        VARCHAR(20)  NULL,
    email          VARCHAR(100) NULL,
    designation_id INT          NULL,
    department_id  INT          NULL,
    joining_date   DATE         NULL,
    basic_salary   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    allowances     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deductions     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(20)  NOT NULL DEFAULT 'Active',
    PRIMARY KEY (employee_id),
    KEY idx_emp_dept (department_id),
    KEY idx_emp_dsg (designation_id),
    CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES hr_department (department_id),
    CONSTRAINT fk_emp_dsg  FOREIGN KEY (designation_id) REFERENCES hr_designation (designation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daily attendance. One row per employee per date.
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id   INT NOT NULL AUTO_INCREMENT,
    employee_id     VARCHAR(20) NOT NULL,
    attendance_date DATE NOT NULL,
    status          VARCHAR(20) NOT NULL, -- Present / Absent / Leave
    PRIMARY KEY (attendance_id),
    UNIQUE KEY uk_att_emp_date (employee_id, attendance_date),
    KEY idx_att_date (attendance_date),
    CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Employee leave applications.
CREATE TABLE IF NOT EXISTS employee_leave (
    leave_id     INT NOT NULL AUTO_INCREMENT,
    employee_id  VARCHAR(20) NOT NULL,
    leave_type   VARCHAR(50) NOT NULL,     -- Sick / Casual / Earned / ...
    start_date   DATE NOT NULL,
    end_date     DATE NOT NULL,
    reason       VARCHAR(255) NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'Pending', -- Pending / Approved / Rejected
    PRIMARY KEY (leave_id),
    KEY idx_leave_emp (employee_id),
    KEY idx_leave_status (status),
    CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Monthly payroll. One row per employee per month.
-- net_salary = basic_salary + allowances - deductions.
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id     INT NOT NULL AUTO_INCREMENT,
    employee_id    VARCHAR(20)   NOT NULL,
    pay_month      VARCHAR(7)    NOT NULL,  -- format YYYY-MM
    basic_salary   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    allowances     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deductions     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    paid_days      INT           NOT NULL DEFAULT 0,
    net_salary     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    generated_date TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payroll_id),
    UNIQUE KEY uk_payroll_emp_month (employee_id, pay_month),
    KEY idx_payroll_month (pay_month),
    CONSTRAINT fk_payroll_emp FOREIGN KEY (employee_id) REFERENCES employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- MODULE 2 : INVENTORY & PHARMACY
-- ============================================================================

CREATE TABLE IF NOT EXISTS inventory_category (
    category_id   INT NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_inv_cat_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory_supplier (
    supplier_id   INT NOT NULL AUTO_INCREMENT,
    supplier_name VARCHAR(100) NOT NULL,
    contact       VARCHAR(20) NULL,
    PRIMARY KEY (supplier_id),
    UNIQUE KEY uk_inv_sup_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory_item (
    item_id        INT NOT NULL AUTO_INCREMENT,
    item_code      VARCHAR(20)   NOT NULL,
    item_name      VARCHAR(100)  NOT NULL,
    category_id    INT           NULL,
    unit           VARCHAR(20)   NULL,       -- tablet / strip / box / vial / piece
    selling_price  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    batch_number   VARCHAR(50)   NULL,
    expiry_date    DATE          NULL,       -- NULL when not applicable
    supplier_id    INT           NULL,
    quantity       INT           NOT NULL DEFAULT 0,
    reorder_level  INT           NOT NULL DEFAULT 10,
    status         VARCHAR(20)   NOT NULL DEFAULT 'Active',
    PRIMARY KEY (item_id),
    UNIQUE KEY uk_inv_item_code (item_code),
    KEY idx_inv_cat (category_id),
    KEY idx_inv_sup (supplier_id),
    KEY idx_inv_expiry (expiry_date),
    CONSTRAINT fk_inv_cat FOREIGN KEY (category_id) REFERENCES inventory_category (category_id),
    CONSTRAINT fk_inv_sup FOREIGN KEY (supplier_id) REFERENCES inventory_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Every stock-in / stock-out is journaled here. quantity on inventory_item
-- is always updated inside the same database transaction.
CREATE TABLE IF NOT EXISTS stock_transaction (
    transaction_id   INT NOT NULL AUTO_INCREMENT,
    item_id          INT NOT NULL,
    transaction_type VARCHAR(20)   NOT NULL, -- STOCK_IN / STOCK_OUT
    quantity         INT           NOT NULL,
    unit_price       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    transaction_date DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference        VARCHAR(100)  NULL,
    remarks          VARCHAR(255)  NULL,
    PRIMARY KEY (transaction_id),
    KEY idx_st_item (item_id),
    KEY idx_st_date (transaction_date),
    CONSTRAINT fk_st_item FOREIGN KEY (item_id) REFERENCES inventory_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- MODULE 3 : FINANCE & ACCOUNTING
-- ============================================================================

-- Payments collected against entries in the existing `bill` table.
-- This keeps actual collected money clearly separate from pending bills
-- (pending = bill.amount - sum(finance_payment.amount)).
CREATE TABLE IF NOT EXISTS finance_payment (
    payment_id     INT NOT NULL AUTO_INCREMENT,
    bill_id        INT NOT NULL,
    patient_name   VARCHAR(100)  NULL,
    amount         DECIMAL(12,2) NOT NULL,
    payment_date   DATE          NOT NULL,
    payment_method VARCHAR(50)   NOT NULL, -- Cash / UPI / Card / Bank Transfer / Other
    reference      VARCHAR(100)  NULL,
    remarks        VARCHAR(255)  NULL,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_fp_reference (reference),
    KEY idx_fp_bill (bill_id),
    KEY idx_fp_date (payment_date),
    CONSTRAINT fk_fp_bill FOREIGN KEY (bill_id) REFERENCES bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Hospital expenses: salaries, medicine purchases, maintenance, utilities etc.
CREATE TABLE IF NOT EXISTS finance_expense (
    expense_id     INT NOT NULL AUTO_INCREMENT,
    expense_type   VARCHAR(50)   NOT NULL, -- Salary / Medicine Purchase / Maintenance / Utility / Other
    description    VARCHAR(255)  NULL,
    amount         DECIMAL(12,2) NOT NULL,
    expense_date   DATE          NOT NULL,
    payment_method VARCHAR(50)   NULL,
    reference      VARCHAR(100)  NULL,
    PRIMARY KEY (expense_id),
    UNIQUE KEY uk_fe_reference (reference),
    KEY idx_fe_date (expense_date),
    KEY idx_fe_type (expense_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- Optional seed data (safe, skipped when already present)
-- ============================================================================

INSERT IGNORE INTO hr_department (department_name) VALUES
    ('General Medicine'), ('Cardiology'), ('Orthopedics'), ('Pediatrics'),
    ('Pharmacy'), ('Nursing'), ('Administration'), ('Reception');

INSERT IGNORE INTO hr_designation (designation_name) VALUES
    ('Doctor'), ('Nurse'), ('Pharmacist'), ('Receptionist'),
    ('Accountant'), ('Lab Technician'), ('Administrator');

INSERT IGNORE INTO inventory_category (category_name) VALUES
    ('Medicine'), ('Surgical Equipment'), ('ICU Equipment'),
    ('Consumables'), ('Lab Reagents');

INSERT IGNORE INTO inventory_supplier (supplier_name, contact) VALUES
    ('MediSupply Co.', '9876543210'),
    ('HealthPlus Distributors', '9876543211'),
    ('SurgiCare Ltd.', '9876543212');