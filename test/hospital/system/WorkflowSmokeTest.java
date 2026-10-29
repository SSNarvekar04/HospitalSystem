package hospital.system;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * DB integration smoke test for the extended workflow modules:
 *   Admin setup / RBAC, Doctor management, Staff & shifts, Patient
 *   registration, Appointments & queue, Consultations, Billing & payments.
 *
 * Runs against the real MySQL database using the application's own services,
 * then removes every test record it created. All test data uses a TST/ prefix.
 *
 * Prerequisites: erp_migration.sql and extended_migration.sql already applied.
 *
 * Run with (from the HospitalSystem folder, classpath = build + connector jar):
 *   java -cp "build;mysql-connector-java-8.0.28.jar" hospital.system.WorkflowSmokeTest
 *
 * Exit code 0 = all checks passed, 1 = at least one check failed.
 */
public class WorkflowSmokeTest {

    private static int failures = 0;
    private static final String DEPT = "TST-WF Dept";
    private static final String DOCTOR_CODE = "TST-DOC-1";
    private static final String PATIENT_NAME = "TST-WF Patient";
    private static final String DOC_NAME = "Dr. TST Workflow";
    private static final String EMP = "TST-WF-E1";

    public static void main(String[] args) {
        System.out.println("=== WORKFLOW SMOKE TEST START ===");
        try {
            loginAsAdmin();
            run();
            System.out.println("=== RESULT: "
                    + (failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED") + " ===");
        } catch (Exception e) {
            failures++;
            System.err.println("UNEXPECTED ERROR: " + e);
            e.printStackTrace();
        } finally {
            try {
                cleanup();
            } catch (Exception e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void loginAsAdmin() {
        Rbac.User admin = new Rbac.User(0, "test-admin", "Test Admin", "Admin", null,
                allPermissionKeys());
        Session.set(admin);
    }

    private static java.util.Set<String> allPermissionKeys() {
        java.util.Set<String> set = new java.util.HashSet<>();
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement("SELECT permission_key FROM app_permission");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                set.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("Could not load permissions: " + e.getMessage());
        }
        return set;
    }

    private static void run() throws Exception {
        tablesExist();
        rbacTests();
        doctorTests();
        staffShiftTests();
        patientTests();
        appointmentTests();
        consultationTests();
        billingTests();
    }

    // -------------------------------------------------------------- tables/RBAC

    private static void tablesExist() {
        System.out.println("--- Schema ---");
        for (String t : new String[]{"hospital_profile", "app_role", "app_permission", "app_role_permission",
                "app_user", "doctor", "shift_master", "duty_roster", "consultation", "prescription",
                "bill_item"}) {
            checkTableExists(t);
        }
    }

    private static void rbacTests() throws Exception {
        System.out.println("--- RBAC ---");
        Rbac.User admin = Rbac.authenticate("admin", "admin123");
        check("admin authenticates via app_user", admin != null && "Admin".equals(admin.role));
        check("admin has SETUP_USER permission", admin != null && admin.permissions.contains("SETUP_USER"));

        Rbac.User reception = Rbac.authenticate("reception", "reception123");
        check("receptionist authenticates", reception != null && "Receptionist".equals(reception.role));
        check("receptionist lacks SETUP_USER permission",
                reception != null && !reception.permissions.contains("SETUP_USER"));

        Rbac.User bad = Rbac.authenticate("admin", "wrong-password");
        check("wrong password is rejected", bad == null);

        Rbac.User legacy = Rbac.authenticateLegacy("admin", "admin");
        check("legacy login table still works (admin/admin)", legacy != null);

        // Permission enforcement must be in the backend, not just the UI.
        Session.set(reception);
        boolean denied;
        try {
            SetupService.addUser("tst_should_fail", "xxxx", "Should Fail",
                    SetupService.roles().get(0).id);
            denied = false;
        } catch (SecurityException e) {
            denied = true;
        }
        check("receptionist blocked from creating a user (backend guard)", denied);
        Session.set(admin);
    }

    // --------------------------------------------------------------- doctors

    private static void doctorTests() {
        System.out.println("--- Doctor Management ---");
        int deptId = setupDepartment();
        try {
            DoctorService.Doctor d = new DoctorService.Doctor();
            d.doctorCode = DOCTOR_CODE;
            d.name = DOC_NAME;
            d.specialization = "TST Cardiology";
            d.departmentId = deptId;
            d.phone = "9000000000";
            d.consultationFee = "500";
            d.availableDays = "Mon,Tue,Wed,Thu,Fri,Sat,Sun";
            d.availableFrom = "09:00";
            d.availableTo = "12:00";
            int id = DoctorService.addDoctor(d);
            check("doctor created", id > 0);

            boolean dup = false;
            try {
                DoctorService.addDoctor(d);
            } catch (RuntimeException e) {
                dup = true;
            }
            check("duplicate doctor code rejected", dup);

            DoctorService.Doctor loaded = DoctorService.findById(id);
            check("doctor department assigned", loaded != null && deptId == loaded.departmentId);
            check("doctor specialization saved", loaded != null && "TST Cardiology".equals(loaded.specialization));

            DoctorService.deactivateDoctor(id);
            check("doctor deactivated", "Inactive".equals(DoctorService.findById(id).status));
            DoctorService.activateDoctor(id);
            check("doctor reactivated", "Active".equals(DoctorService.findById(id).status));
        } finally {
            safe("DELETE FROM doctor WHERE doctor_code='" + DOCTOR_CODE + "'");
        }
    }

    // ----------------------------------------------------------- staff/shifts

    private static void staffShiftTests() {
        System.out.println("--- Staff & Shift Management ---");
        int deptId = setupDepartment();
        int dsgId = setupDesignation();
        insertEmployee();
        try {
            int morning = shiftId("Morning");
            LocalDate date = LocalDate.now().plusDays(20);
            StaffService.assignShift(EMP, morning, date);
            check("shift assigned to staff", 1 == count("SELECT COUNT(*) FROM duty_roster WHERE employee_id='" + EMP + "'"));

            boolean conflict = false;
            try {
                StaffService.assignShift(EMP, morning, date);
            } catch (RuntimeException e) {
                conflict = true;
            }
            check("second shift on same date rejected", conflict);

            // Approved leave blocks roster.
            LocalDate leaveDay = date.plusDays(1);
            insertApprovedLeave(leaveDay);
            boolean leaveBlocked = false;
            try {
                StaffService.assignShift(EMP, morning, leaveDay);
            } catch (RuntimeException e) {
                leaveBlocked = true;
            }
            check("roster blocked by approved leave", leaveBlocked);

            List<String> available = StaffService.availableStaff(date, morning);
            boolean empListed = available.stream().anyMatch(s -> s.startsWith(EMP));
            check("rostered staff excluded from availability", !empListed);
        } catch (Exception e) {
            check("staff/shift tests completed without error", false);
            e.printStackTrace();
        } finally {
            safe("DELETE FROM duty_roster WHERE employee_id='" + EMP + "'");
            safe("DELETE FROM employee_leave WHERE employee_id='" + EMP + "'");
            safe("DELETE FROM employee WHERE employee_id='" + EMP + "'");
            safe("DELETE FROM hr_designation WHERE designation_name='TST-WF Role'");
        }
    }

    // -------------------------------------------------------------- patients

    private static void patientTests() {
        System.out.println("--- Patient Registration ---");
        PatientService.Patient p = new PatientService.Patient();
        p.idType = "Aadhar Card";
        p.number = "TST-WF-9999";
        p.name = PATIENT_NAME;
        p.gender = "Male";
        p.age = "40";
        p.contact = "9000000001";
        p.patientType = "OPD";
        int id = PatientService.register(p);
        PatientService.Patient saved = PatientService.findById(id);
        check("patient registered with code", saved != null && saved.patientCode != null
                && saved.patientCode.startsWith("PT-"));
        check("patient OPD type saved", saved != null && "OPD".equals(saved.patientType));

        boolean dup = false;
        try {
            PatientService.Patient p2 = new PatientService.Patient();
            p2.idType = "Aadhar Card";
            p2.number = "TST-WF-9999";
            p2.name = "Someone Else";
            PatientService.register(p2);
        } catch (RuntimeException e) {
            dup = true;
        }
        check("duplicate patient (same id number) rejected", dup);

        try {
            PatientService.Patient bad = new PatientService.Patient();
            bad.idType = "Aadhar Card";
            bad.number = "TST-WF-X";
            bad.name = "";
            PatientService.register(bad);
            check("patient without name rejected", false);
        } catch (IllegalArgumentException e) {
            check("patient without name rejected", true);
        }
    }

    // ----------------------------------------------------------- appointments

    private static void appointmentTests() {
        System.out.println("--- Appointment & Queue ---");
        int deptId = setupDepartment();
        int doctorId = insertDoctor();
        int patientId = "TST-WF Patient".equals(PATIENT_NAME)
                ? queryInt("SELECT id FROM patient_info1 WHERE name='" + PATIENT_NAME + "' ORDER BY id DESC LIMIT 1")
                : -1;
        String today = LocalDate.now().toString();
        try {
            DoctorService.Doctor doctor = DoctorService.findById(doctorId);
            int apptId = AppointmentService.book(PATIENT_NAME, patientId, "Regular", doctor,
                    "09:30", LocalDate.now(), "");
            check("appointment booked", apptId > 0);

            boolean overlap = false;
            try {
                AppointmentService.book(PATIENT_NAME, patientId, "Regular", doctor,
                        "09:30", LocalDate.now(), "");
            } catch (RuntimeException e) {
                overlap = true;
            }
            check("overlapping appointment for same doctor rejected", overlap);

            List<String> slots = AppointmentService.availableSlots(doctor, LocalDate.now());
            check("booked slot removed from availability", !slots.contains("09:30"));
            check("free slots still offered", slots.contains("09:00"));

            int queue = AppointmentService.checkIn(apptId);
            check("check-in assigns queue number 1", queue == 1);
            check("appointment status becomes Checked-In",
                    "Checked-In".equals(queryString(
                            "SELECT status FROM bookappointment2 WHERE appointment_id=" + apptId)));

            AppointmentService.reschedule(apptId, LocalDate.now(), "10:00");
            check("reschedule updates time",
                    "10:00".equals(queryString(
                            "SELECT appointment_time FROM bookappointment2 WHERE appointment_id=" + apptId)));
        } catch (Exception e) {
            check("appointment tests completed without error", false);
            e.printStackTrace();
        } finally {
            safe("DELETE FROM bookappointment2 WHERE patient_name='" + PATIENT_NAME + "'");
            safe("DELETE FROM doctor WHERE doctor_code='" + DOCTOR_CODE + "'");
        }
    }

    // ---------------------------------------------------------- consultations

    private static void consultationTests() {
        System.out.println("--- Consultation & Clinical Records ---");
        int deptId = setupDepartment();
        int doctorId = insertDoctor();
        int patientId = queryInt("SELECT id FROM patient_info1 WHERE name='" + PATIENT_NAME + "' ORDER BY id DESC LIMIT 1");
        try {
            ConsultationService.Consultation c = new ConsultationService.Consultation();
            c.patientId = patientId;
            c.patientName = PATIENT_NAME;
            c.doctorId = doctorId;
            c.doctorName = DOC_NAME;
            c.bloodPressure = "120/80";
            c.temperature = "98.6";
            c.pulse = "72";
            c.weight = "70";
            c.symptoms = "TST headache";
            c.diagnosis = "TST tension headache";
            c.treatmentPlan = "TST rest and hydration";
            java.util.List<ConsultationService.Medicine> meds = new java.util.ArrayList<>();
            ConsultationService.Medicine m = new ConsultationService.Medicine();
            m.name = "TST Paracetamol";
            m.dosage = "500mg";
            m.frequency = "1-0-1";
            m.duration = "3 days";
            meds.add(m);

            int id = ConsultationService.save(c, meds);
            check("consultation saved", id > 0);
            check("consultation linked to patient",
                    patientId == queryInt("SELECT patient_id FROM consultation WHERE consultation_id=" + id));
            check("prescription saved", 1 == queryInt(
                    "SELECT COUNT(*) FROM prescription WHERE consultation_id=" + id));
            check("consultation history returns record", !ConsultationService.history("TST", patientId).isEmpty());
        } catch (Exception e) {
            check("consultation tests completed without error", false);
            e.printStackTrace();
        } finally {
            safe("DELETE FROM prescription WHERE consultation_id IN " +
                    "(SELECT consultation_id FROM consultation WHERE patient_name='" + PATIENT_NAME + "')");
            safe("DELETE FROM consultation WHERE patient_name='" + PATIENT_NAME + "'");
            safe("DELETE FROM doctor WHERE doctor_code='" + DOCTOR_CODE + "'");
        }
    }

    // --------------------------------------------------------------- billing

    private static void billingTests() {
        System.out.println("--- Billing & Payments ---");
        try {
            int patientId = queryInt("SELECT id FROM patient_info1 WHERE name='" + PATIENT_NAME + "' ORDER BY id DESC LIMIT 1");
            java.util.List<BillingService.Line> lines = new java.util.ArrayList<>();
            lines.add(new BillingService.Line("Consultation", "OPD consult", new BigDecimal("500")));
            lines.add(new BillingService.Line("Laboratory", "CBC", new BigDecimal("300")));
            lines.add(new BillingService.Line("Medicine", "Medicines", new BigDecimal("200")));

            int billId = BillingService.createBill(patientId, PATIENT_NAME, DOC_NAME, "OPD", null,
                    lines, new BigDecimal("100"));
            check("bill created with line items", billId > 0 && BillingService.billItems(billId).size() == 3);

            BillingService.Bill bill = findBill(billId);
            check("bill subtotal = 1000", bill != null && bill.subtotal.compareTo(new BigDecimal("1000")) == 0);
            check("bill total = subtotal - discount = 900",
                    bill != null && bill.total.compareTo(new BigDecimal("900")) == 0);
            check("bill number generated", bill != null && bill.billNumber != null
                    && bill.billNumber.startsWith("INV-"));
            check("new bill status Pending", bill != null && "Pending".equals(bill.status));

            // Partial payment.
            BillingService.recordPayment(billId, new BigDecimal("400"), LocalDate.now(),
                    "Cash", "TST-WF-PAY-1", "");
            bill = findBill(billId);
            check("partial payment sets Partially Paid", bill != null && "Partially Paid".equals(bill.status));
            check("balance after partial payment = 500",
                    bill != null && bill.balance.compareTo(new BigDecimal("500")) == 0);

            // Over-payment rejected.
            boolean over = false;
            try {
                BillingService.recordPayment(billId, new BigDecimal("600"), LocalDate.now(),
                        "UPI", "TST-WF-PAY-2", "");
            } catch (RuntimeException e) {
                over = true;
            }
            check("over-payment rejected", over);

            // Duplicate reference rejected.
            boolean dupRef = false;
            try {
                BillingService.recordPayment(billId, new BigDecimal("100"), LocalDate.now(),
                        "Cash", "TST-WF-PAY-1", "");
            } catch (RuntimeException e) {
                dupRef = true;
            }
            check("duplicate payment reference rejected", dupRef);

            // Final payment completes the bill.
            BillingService.recordPayment(billId, new BigDecimal("500"), LocalDate.now(),
                    "Card", "TST-WF-PAY-3", "");
            bill = findBill(billId);
            check("full payment sets Paid", bill != null && "Paid".equals(bill.status));
            check("payment history has 2 entries", BillingService.payments(billId).size() == 2);
            check("invoice text built", BillingService.invoiceText(billId).contains("INV-"));
        } catch (Exception e) {
            check("billing tests completed without error", false);
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------------- helpers

    private static int setupDepartment() {
        Integer existing = intOrNull("SELECT department_id FROM hr_department WHERE department_name='" + DEPT + "'");
        if (existing != null) {
            return existing;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO hr_department (department_name) VALUES (?)", new String[]{"department_id"})) {
            ps.setString(1, DEPT);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int setupDesignation() {
        Integer existing = intOrNull("SELECT designation_id FROM hr_designation WHERE designation_name='TST-WF Role'");
        if (existing != null) {
            return existing;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO hr_designation (designation_name) VALUES ('TST-WF Role')",
                     new String[]{"designation_id"})) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertEmployee() {
        int deptId = setupDepartment();
        int dsgId = setupDesignation();
        safe("DELETE FROM employee WHERE employee_id='" + EMP + "'");
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO employee (employee_id, name, designation_id, department_id, " +
                             "joining_date, basic_salary, status) VALUES (?, ?, ?, ?, ?, ?, 'Active')")) {
            ps.setString(1, EMP);
            ps.setString(2, "TST Workflow Staff");
            ps.setInt(3, dsgId);
            ps.setInt(4, deptId);
            ps.setDate(5, java.sql.Date.valueOf(LocalDate.now()));
            ps.setBigDecimal(6, new BigDecimal("30000"));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int insertDoctor() {
        safe("DELETE FROM doctor WHERE doctor_code='" + DOCTOR_CODE + "'");
        int deptId = setupDepartment();
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO doctor (doctor_code, name, specialization, department_id, phone, " +
                             "consultation_fee, available_days, available_from, available_to, status) " +
                             "VALUES (?, ?, 'TST', ?, '9000000000', 500, " +
                             "'Mon,Tue,Wed,Thu,Fri,Sat,Sun', '09:00', '12:00', 'Active')",
                     new String[]{"doctor_id"})) {
            ps.setString(1, DOCTOR_CODE);
            ps.setString(2, DOC_NAME);
            ps.setInt(3, deptId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertApprovedLeave(LocalDate day) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO employee_leave (employee_id, leave_type, start_date, end_date, reason, status) " +
                             "VALUES (?, 'Casual Leave', ?, ?, 'TST', 'Approved')")) {
            ps.setString(1, EMP);
            ps.setDate(2, java.sql.Date.valueOf(day));
            ps.setDate(3, java.sql.Date.valueOf(day));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int shiftId(String name) {
        Integer id = intOrNull("SELECT shift_id FROM shift_master WHERE shift_name='" + name + "'");
        if (id == null) {
            throw new IllegalStateException("Shift not found: " + name);
        }
        return id;
    }

    private static BillingService.Bill findBill(int billId) {
        for (BillingService.Bill b : BillingService.listBills(null, null)) {
            if (b.billId == billId) {
                return b;
            }
        }
        return null;
    }

    private static void cleanup() {
        System.out.println("--- Cleaning up test data ---");
        safe("DELETE FROM bill_item WHERE bill_id IN (SELECT bill_id FROM bill WHERE patient_name='" + PATIENT_NAME + "')");
        safe("DELETE FROM finance_payment WHERE patient_name='" + PATIENT_NAME + "'");
        safe("DELETE FROM bill WHERE patient_name='" + PATIENT_NAME + "'");
        safe("DELETE FROM prescription WHERE consultation_id IN " +
                "(SELECT consultation_id FROM consultation WHERE patient_name='" + PATIENT_NAME + "')");
        safe("DELETE FROM consultation WHERE patient_name='" + PATIENT_NAME + "'");
        safe("DELETE FROM bookappointment2 WHERE patient_name='" + PATIENT_NAME + "'");
        safe("DELETE FROM patient_info1 WHERE name='" + PATIENT_NAME + "'");
        safe("DELETE FROM duty_roster WHERE employee_id='" + EMP + "'");
        safe("DELETE FROM employee_leave WHERE employee_id='" + EMP + "'");
        safe("DELETE FROM employee WHERE employee_id='" + EMP + "'");
        safe("DELETE FROM doctor WHERE doctor_code='" + DOCTOR_CODE + "'");
        safe("DELETE FROM hr_designation WHERE designation_name='TST-WF Role'");
        safe("DELETE FROM hr_department WHERE department_name='" + DEPT + "'");
        safe("DELETE FROM app_user WHERE username='tst_should_fail'");
    }

    private static void safe(String sql) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Cleanup warning [" + sql + "]: " + e.getMessage());
        }
    }

    private static void checkTableExists(String table) {
        check("table '" + table + "' exists", 1 == count(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hospital_system' " +
                        "AND table_name='" + table + "'"));
    }

    private static void check(String label, boolean ok) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            failures++;
            System.out.println("  FAIL  " + label);
        }
    }

    private static int count(String sql) {
        try {
            return queryInt(sql);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static int queryInt(String sql) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Integer intOrNull(String sql) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String queryString(String sql) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
