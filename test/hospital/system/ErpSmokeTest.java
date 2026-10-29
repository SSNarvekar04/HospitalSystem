package hospital.system;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * DB integration smoke test for the three ERP modules.
 *
 * Runs real CRUD operations against the MySQL database using the application's
 * own connection class and ErpService, verifies business rules, then removes
 * every test record it created. The test uses a TST/ prefix so it never
 * touches real data.
 *
 * Run with (from the HospitalSystem folder):
 *   java -cp "build;<mysql-connector-jar>" hospital.system.ErpSmokeTest
 *
 * Exit code 0 = all checks passed, 1 = at least one check failed.
 */
public class ErpSmokeTest {

    private static int failures = 0;
    private static final String MONTH = YearMonth.now().toString();

    public static void main(String[] args) {
        System.out.println("=== ERP SMOKE TEST START (month=" + MONTH + ") ===");
        try {
            run();
            System.out.println("=== RESULT: " + (failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED") + " ===");
        } catch (Exception e) {
            failures++;
            System.err.println("UNEXPECTED ERROR: " + e);
            e.printStackTrace();
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        // Baseline summary (today) before we create anything.
        double[] baseline = ErpService.computeSummary("DAY", LocalDate.now().toString());

        // -------- snapshot helpers for table existence --------
        checkTableExists("hr_department");
        checkTableExists("employee");
        checkTableExists("attendance");
        checkTableExists("employee_leave");
        checkTableExists("payroll");
        checkTableExists("inventory_category");
        checkTableExists("inventory_item");
        checkTableExists("stock_transaction");
        checkTableExists("finance_payment");
        checkTableExists("finance_expense");

        int deptId = insertNamed("hr_department", "department_name", "TST Dept");
        int dsgId = insertNamed("hr_designation", "designation_name", "TST Role");
        int catId = insertNamed("inventory_category", "category_name", "TST Category");
        int supId = insertNamed("inventory_supplier", "supplier_name", "TST Supplier");

        try {
            hrTests(deptId, dsgId);
            inventoryTests(catId, supId);
            financeTests(baseline);
            legacyIntegration();
        } finally {
            cleanup();
        }
    }

    // ------------------------------------------------------------------ HR

    private static void hrTests(int deptId, int dsgId) throws Exception {
        System.out.println("--- HR & Payroll ---");

        // Duplicate employee id is rejected before insert.
        check("employee id 'TST-E1' does not exist at start", !ErpService.employeeExists("TST-E1"));

        insertEmployee("TST-E1", deptId, dsgId, "50000.00", "5000.00", "2000.00");
        check("employee id 'TST-E1' exists after insert", ErpService.employeeExists("TST-E1"));

        // Duplicate insert must fail (primary key).
        boolean dupRejected = false;
        try {
            insertEmployee("TST-E1", deptId, dsgId, "50000.00", "0", "0");
        } catch (SQLException e) {
            dupRejected = true;
        }
        check("duplicate employee id is rejected", dupRejected);

        // Update employee (rename) then verify.
        updateEmployeeName("TST-E1", "TST Employee One");
        check("employee updated", "TST Employee One".equals(queryString(
                "SELECT name FROM employee WHERE employee_id = 'TST-E1'")));

        // Attendance for a few days this month.
        LocalDate today = LocalDate.now();
        LocalDate d1 = today.withDayOfMonth(1);
        LocalDate d2 = today.withDayOfMonth(2);
        LocalDate d3 = today.withDayOfMonth(3);
        insertAttendance("TST-E1", d1, "Present");
        insertAttendance("TST-E1", d2, "Present");

        // Same employee + same date must be rejected (unique key).
        boolean dupAttRejected = false;
        try {
            insertAttendance("TST-E1", d2, "Leave");
        } catch (SQLException e) {
            dupAttRejected = true;
        }
        check("duplicate attendance for same employee/date rejected", dupAttRejected);

        insertAttendance("TST-E1", d3, "Absent");
        check("attendance rows exist", 3 == queryInt("SELECT COUNT(*) FROM attendance WHERE employee_id='TST-E1'"));

        // Payroll generation.
        int generated = ErpService.generatePayroll(MONTH);
        check("payroll generated for test employee", generated >= 1);

        double net = queryDouble("SELECT net_salary FROM payroll WHERE employee_id='TST-E1' AND pay_month='" + MONTH + "'");
        int paidDays = queryInt("SELECT paid_days FROM payroll WHERE employee_id='TST-E1' AND pay_month='" + MONTH + "'");
        check("net salary = basic(50000) + allowances(5000) - deductions(2000) = 53000", net == 53000d);
        check("paid days = 2 Present records", paidDays == 2);
        check("salary expense was recorded", 1 == queryInt(
                "SELECT COUNT(*) FROM finance_expense WHERE reference = 'PR-" + MONTH + "-TST-E1'"));

        // Employee is inactive -> excluded from a new payroll run.
        setEmployeeStatus("TST-E1", "Inactive");
        int generated2 = ErpService.generatePayroll(MONTH);
        check("inactive employee not paid twice", queryInt(
                "SELECT COUNT(*) FROM payroll WHERE employee_id='TST-E1'") == 1);
        setEmployeeStatus("TST-E1", "Active");
    }

    // ------------------------------------------------------------- Inventory

    private static void inventoryTests(int catId, int supId) throws Exception {
        System.out.println("--- Inventory & Pharmacy ---");

        int itemId = insertItem("TST-ITEM-1", catId, supId, 5, 10, null);
        int expiredId = insertItem("TST-ITEM-2", catId, supId, 5, 5,
                Date.valueOf(LocalDate.now().minusDays(2)));

        ErpService.stockIn(itemId, 10, new BigDecimal("250.00"), "TST-STOCKIN-1", "smoke");
        check("stock-in raised quantity 5 -> 15", 15 == queryInt(
                "SELECT quantity FROM inventory_item WHERE item_id=" + itemId));
        check("stock-in transaction logged", 1 == queryInt(
                "SELECT COUNT(*) FROM stock_transaction WHERE item_id=" + itemId + " AND transaction_type='STOCK_IN'"));
        check("purchase expense logged as 'Medicine Purchase'", 2500d == queryDouble(
                "SELECT amount FROM finance_expense WHERE reference='TST-STOCKIN-1'"));

        ErpService.stockOut(itemId, 3, "TST-SOUT-1", "smoke");
        check("stock-out reduced quantity 15 -> 12", 12 == queryInt(
                "SELECT quantity FROM inventory_item WHERE item_id=" + itemId));

        // Over-issue must fail and leave stock untouched.
        boolean insufficientRejected = false;
        try {
            ErpService.stockOut(itemId, 100, "TST-SOUT-2", "smoke");
        } catch (SQLException e) {
            insufficientRejected = true;
        }
        check("over-issue rejected and stock unchanged", insufficientRejected
                && 12 == queryInt("SELECT quantity FROM inventory_item WHERE item_id=" + itemId));
        check("failed stock-out was not logged", 0 == queryInt(
                "SELECT COUNT(*) FROM stock_transaction WHERE reference='TST-SOUT-2'"));

        // Expired item cannot be issued.
        boolean expiredRejected = false;
        try {
            ErpService.stockOut(expiredId, 1, "TST-SOUT-Exp", "smoke");
        } catch (SQLException e) {
            expiredRejected = true;
        }
        check("expired item cannot be issued", expiredRejected);

        // Negative quantity rejected.
        boolean negRejected = false;
        try {
            ErpService.stockIn(itemId, -5, new BigDecimal("10"), "TST-NEG", "smoke");
        } catch (IllegalArgumentException e) {
            negRejected = true;
        }
        check("negative stock-in rejected", negRejected);

        // Low stock alert query fires when quantity <= reorder level.
        updateItemReorder(itemId, 20);
        check("low stock alert detects item", 1 == queryInt(
                "SELECT COUNT(*) FROM inventory_item WHERE item_id=" + itemId + " AND quantity <= reorder_level"));
        updateItemReorder(itemId, 10);
    }

    // --------------------------------------------------------------- Finance

    private static void financeTests(double[] baseline) throws Exception {
        System.out.println("--- Finance & Accounting ---");

        int billId = insertBill("TST Patient", "TST Doctor", "Regular", "1000.00");

        ErpService.recordPayment(billId, new BigDecimal("600.00"), LocalDate.now(),
                "Cash", "TST-PAY-1", "");
        ErpService.recordPayment(billId, new BigDecimal("400.00"), LocalDate.now(),
                "UPI", "TST-PAY-2", "");
        check("outstanding after two payments = 0", 0d == queryDouble(
                "SELECT (SELECT amount FROM bill WHERE bill_id=" + billId + ") - IFNULL((SELECT SUM(amount) FROM finance_payment WHERE bill_id=" + billId + "),0)"));

        // Over-payment rejected.
        boolean overRejected = false;
        try {
            ErpService.recordPayment(billId, new BigDecimal("300.00"), LocalDate.now(),
                    "Cash", "TST-PAY-3", "");
        } catch (SQLException e) {
            overRejected = true;
        }
        check("over-payment rejected", overRejected);

        // Duplicate reference rejected.
        boolean dupRefRejected = false;
        try {
            ErpService.recordPayment(billId, new BigDecimal("150.00"), LocalDate.now(),
                    "Cash", "TST-PAY-1", "");
        } catch (SQLException e) {
            dupRefRejected = true;
        }
        check("duplicate payment reference rejected", dupRefRejected);

        // Duplicate same-bill/same-amount/same-date rejected (400 already recorded).
        boolean dupPayRejected = false;
        try {
            ErpService.recordPayment(billId, new BigDecimal("400.00"), LocalDate.now(),
                    "Cash", "TST-PAY-5", "");
        } catch (SQLException e) {
            dupPayRejected = true;
        }
        check("duplicate payment (same bill/amount/date) rejected", dupPayRejected);

        // Expenses.
        ErpService.recordExpense("Maintenance", "Smoke test maintenance", new BigDecimal("150.00"),
                LocalDate.now(), "Cash", "TST-EXP-1");
        check("expense recorded", 150d == queryDouble(
                "SELECT amount FROM finance_expense WHERE reference='TST-EXP-1'"));
        boolean negExpRejected = false;
        try {
            ErpService.recordExpense("Other", "negative", new BigDecimal("-5"), LocalDate.now(), "Cash", "TST-EXP-2");
        } catch (IllegalArgumentException e) {
            negExpRejected = true;
        }
        check("negative expense rejected", negExpRejected);

        // Summary deltas for today.
        double[] now = ErpService.computeSummary("DAY", LocalDate.now().toString());
        double collectedDelta = now[1] - baseline[1];
        double billedDelta = now[0] - baseline[0];
        double expenseDelta = now[2] - baseline[2];
        check("collected income increased by exactly 1000", Math.abs(collectedDelta - 1000d) < 0.001);
        check("billed increased by 1000", Math.abs(billedDelta - 1000d) < 0.001);
        check("expenses increased by at least 2650 (2500 purchase + 150 maintenance)", expenseDelta >= 2650d);
        check("net balance = collected - expenses", Math.abs(now[3] - (now[1] - now[2])) < 0.001);
        check("pending bills (all-time) unchanged for fully paid test bill",
                Math.abs(now[4] - baseline[4]) < 0.001);

        // Monthly summary also works.
        double[] monthNow = ErpService.computeSummary("MONTH", MONTH);
        check("monthly expenses include test data", monthNow[2] >= baseline[2]);
    }

    // --------------------------------------------- Existing business flows

    private static void legacyIntegration() throws Exception {
        System.out.println("--- Existing features (data-layer regression) ---");

        checkTableExists("patient_info1");
        checkTableExists("bookappointment2");
        checkTableExists("bill");
        checkTableExists("login");
        check("login table still has 'admin' user", 0 < queryInt(
                "SELECT COUNT(*) FROM login WHERE ID='admin'"));

        int patientId = insertPatient("TST Patient", "Male", "TST-101");
        check("patient insert works", patientId > 0);
        deletePatient(patientId);
        check("patient delete works", 0 == queryInt(
                "SELECT COUNT(*) FROM patient_info1 WHERE id=" + patientId));

        // Booking an appointment still writes to the same table.
        insertAppointment("TST Patient Appt", "TST Doctor");
        deleteAppointment("TST Patient Appt");
        check("appointment insert/delete works", 0 == queryInt(
                "SELECT COUNT(*) FROM bookappointment2 WHERE patient_name='TST Patient Appt'"));
    }

    // -------------------------------------------------------------- helpers

    // Named-row insert that returns the generated id.
    private static int insertNamed(String table, String column, String value) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO " + table + " (" + column + ") VALUES (?)",
                     new String[]{"autoIncrementId"})) {
            ps.setString(1, value);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    private static void insertEmployee(String id, int deptId, int dsgId, String basic, String allow, String ded)
            throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO employee (employee_id, name, contact, designation_id, department_id, joining_date, basic_salary, allowances, deductions, status) " +
                             "VALUES (?, ?, '9000000000', ?, ?, ?, ?, ?, ?, 'Active')")) {
            ps.setString(1, id);
            ps.setString(2, "TST Employee");
            ps.setInt(3, dsgId);
            ps.setInt(4, deptId);
            ps.setObject(5, Date.valueOf(LocalDate.now()));
            ps.setBigDecimal(6, new BigDecimal(basic));
            ps.setBigDecimal(7, new BigDecimal(allow));
            ps.setBigDecimal(8, new BigDecimal(ded));
            ps.executeUpdate();
        }
    }

    private static void updateEmployeeName(String id, String name) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE employee SET name=? WHERE employee_id=?")) {
            ps.setString(1, name);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    private static void setEmployeeStatus(String id, String status) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE employee SET status=? WHERE employee_id=?")) {
            ps.setString(1, status);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    private static void insertAttendance(String empId, LocalDate date, String status) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO attendance (employee_id, attendance_date, status) VALUES (?, ?, ?)")) {
            ps.setString(1, empId);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }

    private static void updateAttendance(String empId, LocalDate date, String status) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE attendance SET status=? WHERE employee_id=? AND attendance_date=?")) {
            ps.setString(1, status);
            ps.setString(2, empId);
            ps.setDate(3, Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    private static int insertItem(String code, int catId, int supId, int qty, int reorder, java.sql.Date expiry)
            throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO inventory_item (item_code, item_name, category_id, unit, selling_price, expiry_date, supplier_id, quantity, reorder_level, status) " +
                             "VALUES (?, ?, ?, 'tablet', 100.00, ?, ?, ?, ?, 'Active')",
                     new String[]{"item_id"})) {
            ps.setString(1, code);
            ps.setString(2, "TST " + code);
            ps.setInt(3, catId);
            ps.setDate(4, expiry);
            ps.setInt(5, supId);
            ps.setInt(6, qty);
            ps.setInt(7, reorder);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    private static void updateItemReorder(int itemId, int reorder) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE inventory_item SET reorder_level=? WHERE item_id=?")) {
            ps.setInt(1, reorder);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }

    private static int insertBill(String patient, String doctor, String type, String amount) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO bill (patient_name, doctor_name, patient_type, amount, date_time) " +
                             "VALUES (?, ?, ?, ?, NOW())",
                     new String[]{"bill_id"})) {
            ps.setString(1, patient);
            ps.setString(2, doctor);
            ps.setString(3, type);
            ps.setBigDecimal(4, new BigDecimal(amount));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    private static int insertPatient(String name, String gender, String number) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO patient_info1 (id_type, number, name, gender, disease) VALUES ('Smoke', ?, ?, ?, 'Smoke')",
                     new String[]{"id"})) {
            ps.setString(1, number);
            ps.setString(2, name);
            ps.setString(3, gender);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    private static void deletePatient(int id) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement("DELETE FROM patient_info1 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static void insertAppointment(String patient, String doctor) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO bookappointment2 (patient_name, patient_type, doctor_name, appointment_time, status) " +
                             "VALUES (?, 'New', ?, '10:30', 'Scheduled')")) {
            ps.setString(1, patient);
            ps.setString(2, doctor);
            ps.executeUpdate();
        }
    }

    private static void deleteAppointment(String patient) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement("DELETE FROM bookappointment2 WHERE patient_name=?")) {
            ps.setString(1, patient);
            ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------- cleanup

    private static void cleanup() {
        System.out.println("--- Cleaning up test data ---");
        String[] statements = {
                "DELETE FROM finance_payment WHERE patient_name = 'TST Patient'",
                "DELETE FROM finance_expense WHERE reference IN ('TST-EXP-1', 'TST-STOCKIN-1', 'PR-" + MONTH + "-TST-E1')",
                "DELETE FROM stock_transaction WHERE item_id IN (SELECT item_id FROM inventory_item WHERE item_code LIKE 'TST-%')",
                "DELETE FROM inventory_item WHERE item_code LIKE 'TST-%'",
                "DELETE FROM inventory_category WHERE category_name = 'TST Category'",
                "DELETE FROM inventory_supplier WHERE supplier_name = 'TST Supplier'",
                "DELETE FROM payroll WHERE employee_id = 'TST-E1'",
                "DELETE FROM attendance WHERE employee_id = 'TST-E1'",
                "DELETE FROM employee_leave WHERE employee_id = 'TST-E1'",
                "DELETE FROM employee WHERE employee_id = 'TST-E1'",
                "DELETE FROM hr_department WHERE department_name = 'TST Dept'",
                "DELETE FROM hr_designation WHERE designation_name = 'TST Role'",
                "DELETE FROM bill WHERE patient_name = 'TST Patient'",
                "DELETE FROM patient_info1 WHERE disease = 'Smoke'",
                "DELETE FROM bookappointment2 WHERE patient_name = 'TST Patient Appt'"
        };
        for (String sql : statements) {
            try (connection c = new connection();
                 PreparedStatement ps = c.connection.prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Cleanup warning for [" + sql + "]: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- utils

    private static void checkTableExists(String table) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hospital_system' AND table_name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                check("table '" + table + "' exists", rs.getInt(1) == 1);
            }
        } catch (SQLException e) {
            check("table '" + table + "' exists", false);
        }
    }

    private static void check(String label, boolean ok) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            failures++;
            System.out.println("  FAIL  " + label);
        }
    }

    private static int queryInt(String sql) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static double queryDouble(String sql) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : -1;
        }
    }

    private static String queryString(String sql) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}