# City Multispeciality Hospital - Hospital Management System

A Java Swing + JDBC desktop application for hospital management. It started as a
classic reception system (patients, appointments, billing, staff/department
info) and has been extended, without rebuilding it, with:

1. Admin & Hospital Setup (profile, departments, users & role-based access)
2. Doctor Management
3. Hospital Staff & Shift Management
4. Patient Registration (enhanced)
5. Appointment & Queue Management (enhanced)
6. Consultation & Clinical Records
7. Billing & Payments (enhanced)
8. HR & Payroll, Inventory & Pharmacy, Finance & Accounting (ERP modules)

## Technology Stack

- Frontend: Java Swing (null-layout screens, matching the original design)
- Language: Java (JDK 24)
- Build / IDE: IntelliJ IDEA (plain module, compile with `javac`)
- Database: MySQL 8 (`mysql-connector-java-8.0.28`), database `hospital_system`
- Architecture: package `hospital.system`, one GUI class per screen + a service
  class per area (`Rbac`, `SetupService`, `StaffService`, `DoctorService`,
  `PatientService`, `AppointmentService`, `ConsultationService`,
  `BillingService`, `ErpService`). All SQL uses `PreparedStatement`.

## Project Layout

```
HospitalSystem/
├─ src/hospital/system/               # Application sources
├─ sql/erp_migration.sql              # ERP tables (HR / Inventory / Finance)
├─ sql/extended_migration.sql         # Admin, doctors, shifts, clinical, billing
├─ test/hospital/system/              # DB integration smoke tests
│   ├─ ErpSmokeTest.java
│   └─ WorkflowSmokeTest.java
├─ build/                             # Compiled classes (generated)
└─ README.md
```

## Database Configuration

The application connects to MySQL using the existing `connection` class:

```
jdbc:mysql://localhost:3306/hospital_system
User : root
```

Credentials are already present in `src/hospital/system/connection.java` (the
original project stores them there). Do not commit new passwords or secrets.

### Database setup

1. Start the MySQL 8 service and ensure the `hospital_system` database exists.
2. Apply both migration scripts, in order. Both are **additive and repeatable**:
   they never drop/truncate existing tables and never modify existing rows
   destructively.

```
mysql -u root -p hospital_system < sql/erp_migration.sql
mysql -u root -p hospital_system < sql/extended_migration.sql
```

`extended_migration.sql` uses a helper procedure to add columns only when they
are missing, so re-running it is safe (idempotent). It also imports the legacy
`doctors` table into the new `doctor` table once (only if `doctor` is empty).

### Tables added

| Area | Tables |
| ---- | ------ |
| Admin setup / RBAC | `hospital_profile`, `app_role`, `app_permission`, `app_role_permission`, `app_user` |
| Doctor management | `doctor` |
| Staff & shifts | `shift_master`, `duty_roster` |
| Clinical records | `consultation`, `prescription` |
| Billing | `bill_item` |
| HR & Payroll | `hr_department`, `hr_designation`, `employee`, `attendance`, `employee_leave`, `payroll` |
| Inventory | `inventory_category`, `inventory_supplier`, `inventory_item`, `stock_transaction` |
| Finance | `finance_payment`, `finance_expense` |

### Existing tables reused (additive columns only)

`patient_info1` (+patient_code, DOB, age, contact, address, emergency contact,
blood group, patient_type, status), `bookappointment2` (+patient_id, doctor_id,
queue_no, checkin_time, notes), `bill` (+bill_number, patient_id, appointment_id,
subtotal, discount, total, status, created_by - `amount` is kept equal to the
total for finance reconciliation), `finance_payment` (+patient_id).

The legacy tables `login`, `doctors`, `department`, `emp_info`, `patient`,
`disease`, `patient_disease` are left untouched. The old read-only screens still
read them.

## Authentication & Roles

`Login` tries the new `app_user` table first (`Rbac.authenticate`) and falls
back to the legacy `login` table (`Rbac.authenticateLegacy`) so existing
accounts keep working. A legacy account is treated as full Admin.

Seeded application users:

| Username | Password | Role |
| -------- | -------- | ---- |
| admin | admin123 | Admin |
| doctor | doctor123 | Doctor |
| reception | reception123 | Receptionist |
| staff | staff123 | Staff |

Permissions are defined in `app_permission` and mapped per role in
`app_role_permission`. Authorization is enforced in the **service layer**
(`Rbac.require("...")`) - hiding a button is only a convenience, never the only
protection. The Reception buttons are also disabled for permissions the current
user lacks.

## How to Build and Run

```
javac -encoding UTF-8 -cp "C:\path\to\mysql-connector-java-8.0.28.jar" -d build src/hospital/system/*.java
java  -cp "build;C:\path\to\mysql-connector-java-8.0.28.jar" hospital.system.Login
```

In IntelliJ IDEA open `HospitalSystem.iml` (configured with the
`mysql-connector-java-8.0.28` library) and run `Login`.

## Module Overview

### Admin & Hospital Setup (`AdminSetup`)
- Hospital profile (name, address, contact, registration).
- Departments (add / edit / delete), shared with HR, staff and doctors.
- Users & roles: create users, change role/status, reset passwords.

### Doctor Management (`ManageDoctor`)
- Profile, specialization, department assignment, qualifications, contact,
  consultation fee and availability (days + working hours).
- Add, edit, view and soft-deactivate/reactivate (history is preserved).

### Staff & Shift Management (`StaffShift`, plus `ManageEmployee`/`ManageLeave`)
- Shift master (Morning/Evening/Night, or custom) and duty roster.
- Assigning a staff member to a second shift on the same date is rejected, and
  assigning on a day covered by an approved leave is rejected (all in one
  transaction).
- Availability view: staff free for a date, plus the day's roster.
- Leave requests with approve/reject by the admin (`ManageLeave`).

### Patient Registration (`New_Patient`, `Patient_Info`)
- Unique generated patient code `PT-000001`.
- Name, age/DOB, gender, contact, address, emergency contact, blood group,
  disease, OPD/IPD type. Duplicate records (same ID number, or same
  name+contact) are rejected.

### Appointment & Queue (`BookAppointment`, `AppointmentQueue`)
- Doctor availability checked before booking; free 30-minute slots shown from
  the doctor's working hours.
- Book / confirm / reschedule / cancel; unique appointment IDs.
- Check-in assigns a per-doctor/date queue number; overlapping appointments for
  the same doctor/time/date are rejected. Uses the existing `bookappointment2`
  table, so the original flow (which opens `GenerateBill`) is preserved.

### Consultation & Clinical Records (`ConsultationRecord`)
- Vitals, symptoms, diagnosis, treatment plan and notes; linked to the patient
  and appointment, and completing the appointment on save.
- Prescription lines per consultation, plus consultation history with a
  printable record. No diagnosis is ever auto-generated.

### Billing & Payments (`GenerateBill`, `ManageBills`)
- Bill line items (consultation, laboratory, medicine, bed, other), subtotal,
  discount and final total; unique bill number `INV-000001`.
- Payment methods: Cash, UPI, Card, Net Banking, Cheque.
- Status tracked as Pending / Partially Paid / Paid / Refunded.
- Over-payment, duplicate references and duplicate same-bill/amount/date
  payments are rejected. Printable invoice and payment history.

### ERP Modules (reception buttons HR & Payroll / Inventory & Pharmacy / Finance & Accounting)
- HR & Payroll, Inventory & Pharmacy and Finance & Accounting as before. The
  Finance module reuses the `bill` table; collected money lives in
  `finance_payment`.

## Expected Workflow

```
Admin & Hospital Setup
  -> Doctor Management + Staff & Shift Management
  -> Patient Registration
  -> Appointment & Queue Management
  -> Consultation & Clinical Records
  -> Billing & Payments
```

## Testing

Two real database integration smoke tests run CRUD and business rules against
MySQL using the application's own services, then delete every record they
created (TST-prefixed). Exit code 0 = all checks passed.

```
javac -encoding UTF-8 -cp "build;mysql-connector-java-8.0.28.jar" -d build test/hospital/system/ErpSmokeTest.java
javac -encoding UTF-8 -cp "build;mysql-connector-java-8.0.28.jar" -d build test/hospital/system/WorkflowSmokeTest.java

java -cp "build;mysql-connector-java-8.0.28.jar" hospital.system.ErpSmokeTest
java -cp "build;mysql-connector-java-8.0.28.jar" hospital.system.WorkflowSmokeTest
```

- `ErpSmokeTest`: HR/payroll, inventory stock rules, finance payments/expenses,
  plus regression on existing patient/appointment/bill/login data.
- `WorkflowSmokeTest`: schema, RBAC (backend enforcement, legacy fallback),
  doctor CRUD + deactivate, shift/roster conflicts and approved-leave clash,
  patient registration/duplicates, appointment overlap + slots + check-in +
  reschedule, consultation + prescription, and billing subtotal/discount/total,
  payment status transitions, over-payment and duplicate-payment rejection.

## Notes / Existing Behaviour Preserved

- No existing table is dropped, truncated or destructively rewritten.
- The original screens (`Empo_Info`, `Department`, legacy read-only info) keep
  reading the original tables.
- `GenerateBill` keeps its original constructor `GenerateBill(patient, doctor,
  type)` so `BookAppointment` continues to open it unchanged.
- The Finance ERP keeps reconciling because a bill's `amount` equals its total.
