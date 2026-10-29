package hospital.system;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Business logic for the three ERP modules.
 *
 * Kept separate from the Swing screens so the rules can be verified with
 * a plain JDBC test harness. All methods use PreparedStatement and
 * try-with-resources; multi-step operations run inside a single database
 * transaction so they either all succeed or all roll back.
 */
public final class ErpService {

    private ErpService() {
    }

    // ------------------------------------------------------------------
    // HR & Payroll
    // ------------------------------------------------------------------

    public static boolean employeeExists(String employeeId) throws SQLException {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "SELECT 1 FROM employee WHERE employee_id = ?")) {
            ps.setString(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Generates payroll for every Active employee for the given month
     * (format YYYY-MM) that does not already have a payroll record.
     * net_salary = basic_salary + allowances - deductions.
     * paid_days = number of 'Present' attendance records in the month, or
     * the full number of days in the month when no attendance was recorded.
     * Every generated payroll also creates a 'Salary' finance expense inside
     * the same transaction.
     *
     * @return number of payroll records generated
     */
    public static int generatePayroll(String month) throws SQLException {
        YearMonth ym = YearMonth.parse(month);
        int daysInMonth = ym.lengthOfMonth();
        int count = 0;

        try (connection c = new connection()) {
            Connection db = c.connection;
            db.setAutoCommit(false);
            try {
                String select = "SELECT e.employee_id, e.basic_salary, e.allowances, e.deductions, " +
                        "(SELECT COUNT(*) FROM attendance a " +
                        " WHERE a.employee_id = e.employee_id AND a.status = 'Present' " +
                        " AND DATE_FORMAT(a.attendance_date, '%Y-%m') = ?) AS present_days, " +
                        "(SELECT COUNT(*) FROM attendance a " +
                        " WHERE a.employee_id = e.employee_id " +
                        " AND DATE_FORMAT(a.attendance_date, '%Y-%m') = ?) AS total_days " +
                        "FROM employee e " +
                        "WHERE e.status = 'Active' " +
                        "AND NOT EXISTS (SELECT 1 FROM payroll p " +
                        "                 WHERE p.employee_id = e.employee_id AND p.pay_month = ?)";

                try (PreparedStatement sel = db.prepareStatement(select)) {
                    sel.setString(1, month);
                    sel.setString(2, month);
                    sel.setString(3, month);
                    try (ResultSet rs = sel.executeQuery();
                         PreparedStatement insPay = db.prepareStatement(
                                 "INSERT INTO payroll " +
                                         "(employee_id, pay_month, basic_salary, allowances, deductions, paid_days, net_salary) " +
                                         "VALUES (?, ?, ?, ?, ?, ?, ?)");
                         PreparedStatement insExp = db.prepareStatement(
                                 "INSERT INTO finance_expense " +
                                         "(expense_type, description, amount, expense_date, payment_method, reference) " +
                                         "VALUES ('Salary', ?, ?, ?, 'Bank Transfer', ?)")) {

                        while (rs.next()) {
                            String empId = rs.getString("employee_id");
                            BigDecimal basic = rs.getBigDecimal("basic_salary");
                            BigDecimal allowances = rs.getBigDecimal("allowances");
                            BigDecimal deductions = rs.getBigDecimal("deductions");
                            int presentDays = rs.getInt("present_days");
                            int totalDays = rs.getInt("total_days");

                            int paidDays = (totalDays == 0) ? daysInMonth : presentDays;
                            BigDecimal net = basic.add(allowances).subtract(deductions);

                            insPay.setString(1, empId);
                            insPay.setString(2, month);
                            insPay.setBigDecimal(3, basic);
                            insPay.setBigDecimal(4, allowances);
                            insPay.setBigDecimal(5, deductions);
                            insPay.setInt(6, paidDays);
                            insPay.setBigDecimal(7, net);
                            insPay.executeUpdate();

                            insExp.setString(1, "Payroll " + empId + " for " + month);
                            insExp.setBigDecimal(2, net);
                            insExp.setObject(3, ym.atEndOfMonth());
                            insExp.setString(4, "PR-" + month + "-" + empId);
                            insExp.executeUpdate();

                            count++;
                        }
                    }
                }
                db.commit();
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Inventory & Pharmacy
    // ------------------------------------------------------------------

    /**
     * Records a stock-in (purchase) and increases the item quantity.
     * A 'Medicine Purchase' finance expense (unit_price * quantity) is
     * recorded in the same transaction.
     */
    public static void stockIn(int itemId, int qty, BigDecimal unitPrice,
                               String reference, String remarks) throws SQLException {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must be zero or a positive value.");
        }
        try (connection c = new connection()) {
            Connection db = c.connection;
            db.setAutoCommit(false);
            try {
                String itemName = getItemName(db, itemId);

                try (PreparedStatement up = db.prepareStatement(
                        "UPDATE inventory_item SET quantity = quantity + ? WHERE item_id = ?")) {
                    up.setInt(1, qty);
                    up.setInt(2, itemId);
                    up.executeUpdate();
                }
                try (PreparedStatement ins = db.prepareStatement(
                        "INSERT INTO stock_transaction " +
                                "(item_id, transaction_type, quantity, unit_price, reference, remarks) " +
                                "VALUES (?, 'STOCK_IN', ?, ?, ?, ?)")) {
                    ins.setInt(1, itemId);
                    ins.setInt(2, qty);
                    ins.setBigDecimal(3, unitPrice);
                    ins.setString(4, reference);
                    ins.setString(5, remarks);
                    ins.executeUpdate();
                }
                try (PreparedStatement insExp = db.prepareStatement(
                        "INSERT INTO finance_expense " +
                                "(expense_type, description, amount, expense_date, payment_method, reference) " +
                                "VALUES ('Medicine Purchase', ?, ?, ?, 'Cash', ?)")) {
                    insExp.setString(1, "Purchase of " + qty + " x " + itemName);
                    insExp.setBigDecimal(2, unitPrice.multiply(BigDecimal.valueOf(qty)));
                    insExp.setObject(3, LocalDate.now());
                    insExp.setString(4, reference);
                    insExp.executeUpdate();
                }
                db.commit();
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
    }

    /**
     * Records a stock-out (issue to patients/departments). Refuses to issue
     * more than the available quantity and refuses to issue expired items.
     */
    public static void stockOut(int itemId, int qty,
                                String reference, String remarks) throws SQLException {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        try (connection c = new connection()) {
            Connection db = c.connection;
            db.setAutoCommit(false);
            try {
                String itemName;
                int available;
                java.sql.Date expiry;
                try (PreparedStatement itemPs = db.prepareStatement(
                        "SELECT item_name, quantity, expiry_date FROM inventory_item WHERE item_id = ? FOR UPDATE")) {
                    itemPs.setInt(1, itemId);
                    try (ResultSet rs = itemPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Item not found.");
                        }
                        itemName = rs.getString("item_name");
                        available = rs.getInt("quantity");
                        expiry = rs.getDate("expiry_date");
                    }
                }

                if (expiry != null && !expiry.toLocalDate().isAfter(LocalDate.now())) {
                    throw new SQLException("Cannot issue '" + itemName
                            + "': item is expired (expiry " + expiry + ").");
                }
                if (qty > available) {
                    throw new SQLException("Insufficient stock for '" + itemName
                            + "'. Available: " + available + ", requested: " + qty);
                }

                try (PreparedStatement up = db.prepareStatement(
                        "UPDATE inventory_item SET quantity = quantity - ? WHERE item_id = ?")) {
                    up.setInt(1, qty);
                    up.setInt(2, itemId);
                    up.executeUpdate();
                }
                try (PreparedStatement ins = db.prepareStatement(
                        "INSERT INTO stock_transaction " +
                                "(item_id, transaction_type, quantity, unit_price, reference, remarks) " +
                                "VALUES (?, 'STOCK_OUT', ?, 0, ?, ?)")) {
                    ins.setInt(1, itemId);
                    ins.setInt(2, qty);
                    ins.setString(3, reference);
                    ins.setString(4, remarks);
                    ins.executeUpdate();
                }
                db.commit();
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
    }

    private static String getItemName(Connection db, int itemId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT item_name FROM inventory_item WHERE item_id = ?")) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Item not found.");
                }
                return rs.getString("item_name");
            }
        }
    }

    // ------------------------------------------------------------------
    // Finance & Accounting
    // ------------------------------------------------------------------

    /**
     * Records an actual cash/card/UPI payment against an existing bill entry.
     * Refuses over-payment and rejects duplicate payments (same bill, same
     * amount, same date, or an already used reference number).
     */
    public static void recordPayment(int billId, BigDecimal amount, LocalDate date,
                                     String method, String reference, String remarks) throws SQLException {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        try (connection c = new connection()) {
            Connection db = c.connection;
            db.setAutoCommit(false);
            try {
                String patientName;
                BigDecimal billed;
                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT patient_name, amount FROM bill WHERE bill_id = ?")) {
                    ps.setInt(1, billId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Bill not found.");
                        }
                        patientName = rs.getString("patient_name");
                        billed = rs.getBigDecimal("amount");
                    }
                }

                BigDecimal paidSoFar;
                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT COALESCE(SUM(amount), 0) FROM finance_payment WHERE bill_id = ?")) {
                    ps.setInt(1, billId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        paidSoFar = rs.getBigDecimal(1);
                    }
                }

                BigDecimal outstanding = billed.subtract(paidSoFar);
                if (amount.compareTo(outstanding) > 0) {
                    throw new SQLException("Payment exceeds outstanding amount "
                            + outstanding + " for bill #" + billId + ".");
                }

                try (PreparedStatement dup = db.prepareStatement(
                        "SELECT COUNT(*) FROM finance_payment " +
                                "WHERE bill_id = ? AND amount = ? AND payment_date = ?")) {
                    dup.setInt(1, billId);
                    dup.setBigDecimal(2, amount);
                    dup.setObject(3, date);
                    try (ResultSet rs = dup.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new SQLException("Duplicate payment entry: a payment for the same bill, "
                                    + "amount and date already exists.");
                        }
                    }
                }

                if (reference != null && !reference.trim().isEmpty()) {
                    try (PreparedStatement dup = db.prepareStatement(
                            "SELECT COUNT(*) FROM finance_payment WHERE reference = ?")) {
                        dup.setString(1, reference.trim());
                        try (ResultSet rs = dup.executeQuery()) {
                            rs.next();
                            if (rs.getInt(1) > 0) {
                                throw new SQLException("Transaction reference '"
                                        + reference + "' has already been used.");
                            }
                        }
                    }
                }

                try (PreparedStatement ins = db.prepareStatement(
                        "INSERT INTO finance_payment " +
                                "(bill_id, patient_name, amount, payment_date, payment_method, reference, remarks) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setInt(1, billId);
                    ins.setString(2, patientName);
                    ins.setBigDecimal(3, amount);
                    ins.setObject(4, date);
                    ins.setString(5, method);
                    ins.setString(6, reference);
                    ins.setString(7, remarks);
                    ins.executeUpdate();
                }
                db.commit();
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
    }

    public static void recordExpense(String type, String description, BigDecimal amount,
                                     LocalDate date, String method, String reference) throws SQLException {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        try (connection c = new connection()) {
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "INSERT INTO finance_expense " +
                            "(expense_type, description, amount, expense_date, payment_method, reference) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, type);
                ps.setString(2, description);
                ps.setBigDecimal(3, amount);
                ps.setObject(4, date);
                ps.setString(5, method);
                ps.setString(6, reference);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Financial summary for a period.
     *
     * @param period "DAY" | "MONTH" | "YEAR"
     * @param value  yyyy-MM-dd for DAY, yyyy-MM for MONTH, yyyy for YEAR
     * @return double[]{billed, collected, expenses, netBalance, outstandingAllTime}
     */
    public static double[] computeSummary(String period, String value) throws SQLException {
        String fmt;
        String arg = value;
        if ("DAY".equalsIgnoreCase(period)) {
            fmt = "%Y-%m-%d";
        } else if ("MONTH".equalsIgnoreCase(period)) {
            fmt = "%Y-%m";
        } else {
            fmt = "%Y";
        }

        double billed = 0, collected = 0, expenses = 0, outstanding = 0;
        try (connection c = new connection()) {
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM bill WHERE DATE_FORMAT(date_time, ?) = ?")) {
                ps.setString(1, fmt);
                ps.setString(2, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    billed = rs.getDouble(1);
                }
            }
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM finance_payment WHERE DATE_FORMAT(payment_date, ?) = ?")) {
                ps.setString(1, fmt);
                ps.setString(2, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    collected = rs.getDouble(1);
                }
            }
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM finance_expense WHERE DATE_FORMAT(expense_date, ?) = ?")) {
                ps.setString(1, fmt);
                ps.setString(2, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    expenses = rs.getDouble(1);
                }
            }
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM bill")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    outstanding = rs.getDouble(1);
                }
            }
            try (PreparedStatement ps = c.connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM finance_payment")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    outstanding -= rs.getDouble(1);
                }
            }
        }
        return new double[]{billed, collected, expenses, collected - expenses, outstanding};
    }
}