-- ============================================================================
-- Hospital Management System - Extended Modules Migration (MySQL 8)
-- Database : hospital_system
--
-- Adds the schema for the additional workflow modules:
--   * Admin & Hospital Setup (hospital profile, roles, permissions, users)
--   * Doctor Management
--   * Hospital Staff & Shift Management (shift master, duty roster)
--   * Consultation & Clinical Records
--   * Billing & Payments (bill line items, payment linkage)
--   * Patient Registration / Appointment enhancements (additive columns only)
--
-- IMPORTANT
--   * This script is ADDITIVE and NON-DESTRUCTIVE. It never drops or truncates
--     existing tables or columns. Existing records are preserved.
--   * It is safe to run repeatedly:
--       - CREATE TABLE ... IF NOT EXISTS for new tables
--       - ADD COLUMN only when the column does not already exist
--       - INSERT IGNORE for seed data
--   * It depends on the earlier script `erp_migration.sql` (that creates the
--     hr_department / hr_designation / employee tables reused here).
--
-- Run with:
--   mysql -u root -p hospital_system < extended_migration.sql
-- ============================================================================

USE hospital_system;

-- ----------------------------------------------------------------------------
-- Helper: add a column only if it does not already exist.
-- MySQL 8 has no "ADD COLUMN IF NOT EXISTS", so we use a dynamic statement
-- driven by information_schema. Each call is a no-op on a second run.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_def    VARCHAR(255)
)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE()
                         AND table_name = p_table
                         AND column_name = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ============================================================================
-- MODULE 1 : ADMIN & HOSPITAL SETUP
-- ============================================================================

-- Hospital profile (single-row configuration, id forced to 1).
CREATE TABLE IF NOT EXISTS hospital_profile (
    profile_id       INT NOT NULL DEFAULT 1,
    hospital_name    VARCHAR(150) NOT NULL,
    address          VARCHAR(255),
    city             VARCHAR(100),
    phone            VARCHAR(20),
    email            VARCHAR(100),
    website          VARCHAR(150),
    registration_no  VARCHAR(80),
    established_year VARCHAR(10),
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO hospital_profile
    (profile_id, hospital_name, address, city, phone, email, website, registration_no, established_year)
VALUES
    (1, 'City Multispeciality Hospital', '12 MG Road', 'Pune', '+91 98765 43210',
     'info@cityhospital.com', 'www.CityMultispecialityHospital.com', 'REG-1998-4521', '1998');

-- Application roles (Admin / Doctor / Receptionist / Staff).
CREATE TABLE IF NOT EXISTS app_role (
    role_id     INT NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO app_role (role_name, description) VALUES
    ('Admin',        'Full system access including setup, users and configuration'),
    ('Doctor',       'Clinical access: consultations, prescriptions and patient history'),
    ('Receptionist', 'Front desk: patient registration, appointments and billing'),
    ('Staff',        'Hospital staff: view duty roster and personal schedule');

-- Fine-grained permissions. Each permission maps to a module action.
CREATE TABLE IF NOT EXISTS app_permission (
    permission_id   INT NOT NULL AUTO_INCREMENT,
    permission_key  VARCHAR(60) NOT NULL,
    description     VARCHAR(200),
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_perm_key (permission_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO app_permission (permission_key, description) VALUES
    ('SETUP_PROFILE',     'Manage hospital profile'),
    ('SETUP_DEPARTMENT',  'Manage departments'),
    ('SETUP_USER',        'Manage users and roles'),
    ('DOCTOR_MANAGE',     'Add, edit and deactivate doctors'),
    ('STAFF_MANAGE',      'Manage staff and shifts'),
    ('STAFF_VIEW',        'View staff duty schedule'),
    ('LEAVE_APPROVE',     'Approve or reject leave requests'),
    ('PATIENT_MANAGE',    'Register and edit patients'),
    ('APPOINTMENT_MANAGE','Book, reschedule and cancel appointments'),
    ('CONSULT_MANAGE',    'Record consultations and prescriptions'),
    ('BILLING_MANAGE',    'Generate bills and record payments'),
    ('FINANCE_MANAGE',    'Access finance ERP module'),
    ('HR_MANAGE',         'Access HR ERP module'),
    ('INVENTORY_MANAGE',  'Access inventory ERP module');

-- Role -> permission mapping.
CREATE TABLE IF NOT EXISTS app_role_permission (
    role_id       INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES app_role (role_id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES app_permission (permission_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Application users (RBAC). Kept separate from the legacy `login` table so the
-- classic login screen keeps working unchanged as a fallback.
CREATE TABLE IF NOT EXISTS app_user (
    user_id        INT NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50) NOT NULL,
    password       VARCHAR(100) NOT NULL,
    full_name      VARCHAR(100),
    role_id        INT NOT NULL,
    employee_id    VARCHAR(20),
    status         VARCHAR(20) NOT NULL DEFAULT 'Active',
    last_login     DATETIME,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_name (username),
    KEY idx_app_user_role (role_id),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES app_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Grant every permission to Admin.
INSERT IGNORE INTO app_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_role r CROSS JOIN app_permission p
WHERE r.role_name = 'Admin';

-- Doctor-role permissions.
INSERT IGNORE INTO app_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_role r JOIN app_permission p
  ON p.permission_key IN ('CONSULT_MANAGE','PATIENT_MANAGE','BILLING_MANAGE','STAFF_VIEW')
WHERE r.role_name = 'Doctor';

-- Receptionist-role permissions.
INSERT IGNORE INTO app_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_role r JOIN app_permission p
  ON p.permission_key IN ('PATIENT_MANAGE','APPOINTMENT_MANAGE','BILLING_MANAGE','STAFF_VIEW')
WHERE r.role_name = 'Receptionist';

-- Staff-role permissions.
INSERT IGNORE INTO app_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_role r JOIN app_permission p
  ON p.permission_key IN ('STAFF_VIEW')
WHERE r.role_name = 'Staff';

-- Seed app users mirroring the existing login accounts, plus one per role.
INSERT IGNORE INTO app_user (username, password, full_name, role_id, status)
SELECT 'admin', 'admin123', 'System Administrator',
       (SELECT role_id FROM app_role WHERE role_name='Admin'), 'Active'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='admin');

INSERT IGNORE INTO app_user (username, password, full_name, role_id, status)
SELECT 'doctor', 'doctor123', 'Dr. Demo Doctor',
       (SELECT role_id FROM app_role WHERE role_name='Doctor'), 'Active'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='doctor');

INSERT IGNORE INTO app_user (username, password, full_name, role_id, status)
SELECT 'reception', 'reception123', 'Front Desk',
       (SELECT role_id FROM app_role WHERE role_name='Receptionist'), 'Active'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='reception');

INSERT IGNORE INTO app_user (username, password, full_name, role_id, status)
SELECT 'staff', 'staff123', 'Hospital Staff',
       (SELECT role_id FROM app_role WHERE role_name='Staff'), 'Active'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='staff');

-- ============================================================================
-- MODULE 2 : DOCTOR MANAGEMENT
-- ============================================================================
CREATE TABLE IF NOT EXISTS doctor (
    doctor_id       INT NOT NULL AUTO_INCREMENT,
    doctor_code     VARCHAR(20) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    specialization  VARCHAR(100),
    department_id   INT,
    qualification   VARCHAR(150),
    experience_yrs  INT DEFAULT 0,
    phone           VARCHAR(20),
    email           VARCHAR(100),
    consultation_fee DECIMAL(10,2) DEFAULT 0.00,
    available_days  VARCHAR(100),
    available_from  VARCHAR(20),
    available_to    VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'Active',
    PRIMARY KEY (doctor_id),
    UNIQUE KEY uk_doctor_code (doctor_code),
    KEY idx_doctor_dept (department_id),
    CONSTRAINT fk_doctor_dept FOREIGN KEY (department_id) REFERENCES hr_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Best-effort import of the legacy `doctors` table (only if it exists and the
-- new doctor table is empty). The legacy table has no primary key, so rows are
-- de-duplicated by name. Everything is guarded so a missing table is ignored.
DROP PROCEDURE IF EXISTS import_legacy_doctors;
DELIMITER $$
CREATE PROCEDURE import_legacy_doctors()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'doctors')
       AND (SELECT COUNT(*) FROM doctor) = 0 THEN
        INSERT INTO doctor (doctor_code, name, specialization, phone, status)
        SELECT CONCAT('DOC-', LPAD(ROW_NUMBER() OVER (ORDER BY Name), 3, '0')),
               Name, specialization, Phone_No, 'Active'
        FROM (SELECT DISTINCT Name, specialization, Phone_No FROM doctors) d
        WHERE Name IS NOT NULL AND TRIM(Name) <> '';
    END IF;
END$$
DELIMITER ;
CALL import_legacy_doctors();

-- ============================================================================
-- MODULE 3 : HOSPITAL STAFF & SHIFT MANAGEMENT
-- ============================================================================

-- Shift master (Morning / Evening / Night ...).
CREATE TABLE IF NOT EXISTS shift_master (
    shift_id     INT NOT NULL AUTO_INCREMENT,
    shift_name   VARCHAR(50) NOT NULL,
    start_time   VARCHAR(10) NOT NULL,
    end_time     VARCHAR(10) NOT NULL,
    description  VARCHAR(150),
    PRIMARY KEY (shift_id),
    UNIQUE KEY uk_shift_name (shift_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO shift_master (shift_name, start_time, end_time, description) VALUES
    ('Morning', '06:00', '14:00', 'Morning duty'),
    ('Evening', '14:00', '22:00', 'Evening duty'),
    ('Night',   '22:00', '06:00', 'Night duty');

-- Duty roster: one staff member, one date, one shift. The unique key prevents
-- a staff member being assigned to two shifts on the same date.
CREATE TABLE IF NOT EXISTS duty_roster (
    roster_id     INT NOT NULL AUTO_INCREMENT,
    employee_id   VARCHAR(20) NOT NULL,
    shift_id      INT NOT NULL,
    roster_date   DATE NOT NULL,
    department_id INT,
    status        VARCHAR(20) NOT NULL DEFAULT 'Scheduled',
    PRIMARY KEY (roster_id),
    UNIQUE KEY uk_roster_emp_date (employee_id, roster_date),
    KEY idx_roster_date (roster_date),
    KEY idx_roster_shift (shift_id),
    CONSTRAINT fk_roster_emp FOREIGN KEY (employee_id) REFERENCES employee (employee_id),
    CONSTRAINT fk_roster_shift FOREIGN KEY (shift_id) REFERENCES shift_master (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- MODULE 4 : PATIENT REGISTRATION ENHANCEMENTS (additive columns only)
-- ============================================================================
CALL add_column_if_missing('patient_info1', 'patient_code',      'VARCHAR(20) NULL');
CALL add_column_if_missing('patient_info1', 'date_of_birth',     'DATE NULL');
CALL add_column_if_missing('patient_info1', 'age',               'INT NULL');
CALL add_column_if_missing('patient_info1', 'contact',           'VARCHAR(20) NULL');
CALL add_column_if_missing('patient_info1', 'address',           'VARCHAR(255) NULL');
CALL add_column_if_missing('patient_info1', 'emergency_contact', 'VARCHAR(20) NULL');
CALL add_column_if_missing('patient_info1', 'blood_group',       'VARCHAR(10) NULL');
CALL add_column_if_missing('patient_info1', 'patient_type',      "VARCHAR(10) NOT NULL DEFAULT 'OPD'");
CALL add_column_if_missing('patient_info1', 'registration_date', 'DATETIME NULL');
CALL add_column_if_missing('patient_info1', 'status',            "VARCHAR(20) NOT NULL DEFAULT 'Active'");

-- ============================================================================
-- MODULE 5 : APPOINTMENT & QUEUE ENHANCEMENTS (additive columns only)
-- ============================================================================
CALL add_column_if_missing('bookappointment2', 'patient_id',    'INT NULL');
CALL add_column_if_missing('bookappointment2', 'doctor_id',     'INT NULL');
CALL add_column_if_missing('bookappointment2', 'queue_no',      'INT NULL');
CALL add_column_if_missing('bookappointment2', 'checkin_time',  'DATETIME NULL');
CALL add_column_if_missing('bookappointment2', 'notes',         'VARCHAR(255) NULL');

-- ============================================================================
-- MODULE 6 : CONSULTATION & CLINICAL RECORDS
-- ============================================================================
CREATE TABLE IF NOT EXISTS consultation (
    consultation_id     INT NOT NULL AUTO_INCREMENT,
    appointment_id      INT,
    patient_id          INT,
    patient_name        VARCHAR(100) NOT NULL,
    doctor_id           INT,
    doctor_name         VARCHAR(100),
    consultation_date   DATETIME NOT NULL,
    blood_pressure      VARCHAR(20),
    temperature         VARCHAR(20),
    pulse               VARCHAR(20),
    weight              VARCHAR(20),
    symptoms            VARCHAR(500),
    diagnosis           VARCHAR(500),
    treatment_plan      VARCHAR(500),
    notes               VARCHAR(500),
    created_by          VARCHAR(50),
    PRIMARY KEY (consultation_id),
    KEY idx_consult_patient (patient_id),
    KEY idx_consult_appt (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS prescription (
    prescription_id    INT NOT NULL AUTO_INCREMENT,
    consultation_id    INT NOT NULL,
    medicine_name      VARCHAR(150) NOT NULL,
    dosage             VARCHAR(80),
    frequency          VARCHAR(80),
    duration           VARCHAR(80),
    instructions       VARCHAR(255),
    PRIMARY KEY (prescription_id),
    KEY idx_rx_consult (consultation_id),
    CONSTRAINT fk_rx_consult FOREIGN KEY (consultation_id)
        REFERENCES consultation (consultation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- MODULE 7 : BILLING & PAYMENTS ENHANCEMENTS
-- ============================================================================

-- Line items of a bill (consultation, lab, medicine, bed ...).
CREATE TABLE IF NOT EXISTS bill_item (
    item_id      INT NOT NULL AUTO_INCREMENT,
    bill_id      INT NOT NULL,
    charge_type  VARCHAR(50) NOT NULL,
    description  VARCHAR(200),
    amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (item_id),
    KEY idx_bill_item_bill (bill_id),
    CONSTRAINT fk_bill_item_bill FOREIGN KEY (bill_id) REFERENCES bill (bill_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Bill enhancements. `amount` (existing column) is kept as the final total so
-- the Finance ERP continues to reconcile without change.
CALL add_column_if_missing('bill', 'bill_number',  'VARCHAR(30) NULL');
CALL add_column_if_missing('bill', 'patient_id',   'INT NULL');
CALL add_column_if_missing('bill', 'appointment_id','INT NULL');
CALL add_column_if_missing('bill', 'subtotal',     'DECIMAL(12,2) NOT NULL DEFAULT 0.00');
CALL add_column_if_missing('bill', 'discount',     'DECIMAL(12,2) NOT NULL DEFAULT 0.00');
CALL add_column_if_missing('bill', 'total',        'DECIMAL(12,2) NOT NULL DEFAULT 0.00');
CALL add_column_if_missing('bill', 'status',       "VARCHAR(20) NOT NULL DEFAULT 'Pending'");
CALL add_column_if_missing('bill', 'created_by',   'VARCHAR(50) NULL');

-- Backfill: existing bills keep their amount as the total.
UPDATE bill SET subtotal = amount WHERE subtotal = 0 AND amount IS NOT NULL;
UPDATE bill SET total    = amount WHERE total = 0 AND amount IS NOT NULL;

-- Payment linkage to patient (finance_payment.bill_id already exists).
CALL add_column_if_missing('finance_payment', 'patient_id', 'INT NULL');

-- ----------------------------------------------------------------------------
-- Cleanup helper procedure
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS import_legacy_doctors;

-- ============================================================================
-- End of extended migration
-- ============================================================================
